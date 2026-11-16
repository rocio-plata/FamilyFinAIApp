package com.rocioplata.familyfinaiapp.data.movements

import com.rocioplata.familyfinaiapp.data.cache.CachePolicy
import com.rocioplata.familyfinaiapp.data.network.ConnectivityObserver
import com.rocioplata.familyfinaiapp.domain.movements.Movement
import com.rocioplata.familyfinaiapp.domain.movements.NoteUpdate
import com.rocioplata.familyfinaiapp.domain.movements.MovementChanges
import com.rocioplata.familyfinaiapp.domain.movements.MovementDates
import com.rocioplata.familyfinaiapp.domain.movements.MovementEditResult
import com.rocioplata.familyfinaiapp.domain.movements.MovementFailure
import com.rocioplata.familyfinaiapp.domain.movements.MovementResult
import com.rocioplata.familyfinaiapp.domain.movements.MovementsData
import com.rocioplata.familyfinaiapp.domain.movements.MovementsRepository
import com.rocioplata.familyfinaiapp.domain.movements.NewMovement
import com.rocioplata.familyfinaiapp.session.Session
import com.rocioplata.familyfinaiapp.session.SessionFailure
import com.rocioplata.familyfinaiapp.session.SessionProvider
import com.rocioplata.familyfinaiapp.session.SessionUnavailableException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.YearMonth

/**
 * Reglas de caché (RF-13): se muestra solo la caché del usuario actual y de menos de 7 días; otra cosa se
 * elimina. Con más de 24 h se marca como desactualizada hasta un refresco correcto. El estado en memoria es
 * por familia y mes, y el backend manda: la caché solo sirve para leer. Los cambios requieren conexión y no
 * hay cola offline (RF-14); tras un cambio correcto se aplica lo enviado a los meses cargados, en memoria y en
 * caché, conservando su `savedAt` (el mes no se da por descargado de nuevo).
 */
class MovementsRepositoryImpl(
    private val remote: MovementsRemoteDataSource,
    private val local: MovementsLocalDataSource,
    private val sessionProvider: SessionProvider,
    private val connectivity: ConnectivityObserver,
    private val dates: MovementDates,
    private val clock: () -> Long = System::currentTimeMillis,
) : MovementsRepository {
    private data class Key(val familyId: String, val month: YearMonth)

    private data class Entry(val movements: List<Movement>, val savedAtMillis: Long)

    private val state = MutableStateFlow<Map<Key, Entry>>(emptyMap())

    // Protege solo las escrituras de caché y estado (nunca la red). `clear()` lo usa sin esperar a un
    // refresco en curso, que puede estar esperando la renovación de la sesión, que a su vez termina la
    // sesión y llama a `clear()` (interbloqueo si compartieran candado, ver HeaderRepositoryImpl).
    // No es reentrante: `loadCache` lo exige y lo toma quien lo llama.
    private val storageLock = Mutex()
    private var clearEpoch = 0

    private val refreshLocks = mutableMapOf<Key, Mutex>()
    private val lastRefreshResults = mutableMapOf<Key, MovementResult<Unit>>()

    override fun movements(familyId: String, month: YearMonth): Flow<MovementsData> {
        val key = Key(familyId, month)
        return state
            .map { entries ->
                val entry = entries[key] ?: return@map MovementsData()
                MovementsData(entry.movements, CachePolicy.isStale(entry.savedAtMillis, clock()))
            }
            .onStart {
                val current = session() as? MovementResult.Success ?: return@onStart
                storageLock.withLock { loadCache(key, current.value.userId) }
            }
    }

    // Solo mira los meses que ya están en memoria: el backend no tiene consulta individual (BG-06).
    override fun movement(familyId: String, movementId: String): Flow<Movement?> =
        state
            .map { entries ->
                entries.entries
                    .filter { it.key.familyId == familyId }
                    .firstNotNullOfOrNull { (_, entry) -> entry.movements.firstOrNull { it.id == movementId } }
            }
            .distinctUntilChanged()

    /** Un refresco a la vez por familia y mes: una llamada simultánea espera y comparte su resultado. */
    override suspend fun refresh(familyId: String, month: YearMonth): MovementResult<Unit> {
        val key = Key(familyId, month)
        val lock = synchronized(refreshLocks) { refreshLocks.getOrPut(key) { Mutex() } }
        if (!lock.tryLock()) {
            lock.withLock { }
            val shared = synchronized(lastRefreshResults) { lastRefreshResults[key] }
            // Sin resultado compartido (el primero se canceló): se hace el refresco propio.
            return shared ?: refresh(familyId, month)
        }
        try {
            synchronized(lastRefreshResults) { lastRefreshResults.remove(key) }
            val result = fetch(key)
            synchronized(lastRefreshResults) { lastRefreshResults[key] = result }
            return result
        } finally {
            lock.unlock()
        }
    }

    override suspend fun createMovement(familyId: String, movement: NewMovement): MovementResult<Movement> {
        val session = when (val current = writableSession()) {
            is MovementResult.Failure -> return current
            is MovementResult.Success -> current.value
        }
        val epoch = currentEpoch()
        val result = remote.createMovement(familyId, movement, session.userId, Instant.ofEpochMilli(clock()))
        if (result is MovementResult.Failure) return result
        val created = (result as MovementResult.Success).value
        applyToLoaded(epoch, session.userId) { entries ->
            val key = Key(familyId, YearMonth.from(created.date))
            val entry = entries[key] ?: return@applyToLoaded entries
            entries + (key to entry.copy(movements = sorted(entry.movements + created)))
        }
        // Se recuerda aunque el mes no esté cargado: es una preferencia, no un dato del mes.
        local.saveLastUsedPaymentMethodId(session.userId, familyId, movement.paymentMethodId)
        return result
    }

    override suspend fun editMovement(
        familyId: String,
        movementId: String,
        changes: MovementChanges,
    ): MovementEditResult {
        if (changes.isEmpty) return MovementEditResult.Success
        val session = when (val current = writableSession()) {
            is MovementResult.Failure -> return MovementEditResult.Failure(current.failure)
            is MovementResult.Success -> current.value
        }
        val epoch = currentEpoch()
        if (changes.hasDataChanges) {
            val saved = remote.updateMovement(familyId, movementId, changes)
            if (saved is MovementResult.Failure) return MovementEditResult.Failure(saved.failure)
            applyToLoaded(epoch, session.userId) { replaceMovement(it, familyId, movementId) { m -> m.withData(changes) } }
        }
        val category = changes.category ?: return MovementEditResult.Success
        val reclassified = remote.reclassifyMovement(familyId, movementId, category.categoryId, category.tagId)
        if (reclassified is MovementResult.Failure) {
            // Si los datos se guardaron antes, es un parcial; si no, no se guardó nada.
            return if (changes.hasDataChanges) {
                MovementEditResult.Partial(reclassified.failure)
            } else {
                MovementEditResult.Failure(reclassified.failure)
            }
        }
        applyToLoaded(epoch, session.userId) {
            replaceMovement(it, familyId, movementId) { m -> m.copy(categoryId = category.categoryId, tagId = category.tagId) }
        }
        return MovementEditResult.Success
    }

    override suspend fun deleteMovement(familyId: String, movementId: String): MovementResult<Unit> {
        val session = when (val current = writableSession()) {
            is MovementResult.Failure -> return current
            is MovementResult.Success -> current.value
        }
        val epoch = currentEpoch()
        val result = remote.deleteMovement(familyId, movementId)
        if (result is MovementResult.Failure) return result
        applyToLoaded(epoch, session.userId) { replaceMovement(it, familyId, movementId) { null } }
        return result
    }

    override suspend fun lastUsedPaymentMethodId(familyId: String): String? {
        val session = session() as? MovementResult.Success ?: return null
        return local.lastUsedPaymentMethodId(session.value.userId, familyId)
    }

    // No espera a un refresco en curso: subir `clearEpoch` hace que ese refresco descarte lo que
    // obtenga en vez de guardarlo tras el borrado.
    override suspend fun clear() = storageLock.withLock {
        clearEpoch++
        local.clear()
        state.value = emptyMap()
    }

    private suspend fun fetch(key: Key): MovementResult<Unit> {
        val session = when (val current = session()) {
            is MovementResult.Failure -> return current
            is MovementResult.Success -> current.value
        }
        val epoch = storageLock.withLock {
            loadCache(key, session.userId)
            clearEpoch
        }
        val remoteResult = remote.movements(key.familyId, dates.boundsOf(key.month))
        if (remoteResult is MovementResult.Failure) return remoteResult
        val movements = sorted((remoteResult as MovementResult.Success).value)
        val now = clock()
        storageLock.withLock {
            // Si se borró la caché mientras se esperaba la red, no se guarda nada.
            if (epoch != clearEpoch) return@withLock
            local.save(key.familyId, key.month, session.userId, now, movements)
            state.update { it + (key to Entry(movements, now)) }
        }
        return MovementResult.Success(Unit)
    }

    /** Fecha descendente y, dentro del día, el registrado más recientemente primero (RF-02, BG-01). */
    private fun sorted(movements: List<Movement>): List<Movement> =
        movements.sortedWith(compareByDescending<Movement> { it.date }.thenByDescending { it.createdAt })

    /** Sin conexión no se llama al backend (RF-14); después, la sesión. */
    private suspend fun writableSession(): MovementResult<Session> {
        if (!connectivity.isOnline.first()) return MovementResult.Failure(MovementFailure.Offline)
        return session()
    }

    private suspend fun currentEpoch(): Int = storageLock.withLock { clearEpoch }

    /**
     * Aplica un cambio a los meses cargados y guarda en caché los que cambiaron, con su `savedAt` intacto. Si se
     * borró todo mientras se esperaba la red ([epoch] distinto), no se aplica nada.
     */
    private suspend fun applyToLoaded(
        epoch: Int,
        userId: String,
        change: (Map<Key, Entry>) -> Map<Key, Entry>,
    ) = storageLock.withLock {
        if (epoch != clearEpoch) return@withLock
        val before = state.value
        val after = change(before)
        after.forEach { (key, entry) ->
            if (before[key] != entry) {
                local.save(key.familyId, key.month, userId, entry.savedAtMillis, entry.movements)
            }
        }
        state.value = after
    }

    /**
     * Quita el movimiento del mes cargado donde esté y, si [transform] devuelve uno, lo coloca en el mes de su
     * fecha (puede ser otro) cuando ese mes está cargado. Un movimiento que no está en memoria no se toca.
     */
    private fun replaceMovement(
        entries: Map<Key, Entry>,
        familyId: String,
        movementId: String,
        transform: (Movement) -> Movement?,
    ): Map<Key, Entry> {
        val sourceKey = entries.keys.firstOrNull { key ->
            key.familyId == familyId && entries.getValue(key).movements.any { it.id == movementId }
        } ?: return entries
        val original = entries.getValue(sourceKey).movements.first { it.id == movementId }
        val updated = transform(original)
        val result = entries.toMutableMap()
        val source = result.getValue(sourceKey)
        val targetKey = updated?.let { Key(familyId, YearMonth.from(it.date)) }
        if (updated != null && targetKey == sourceKey) {
            result[sourceKey] = source.copy(movements = sorted(source.movements.map { if (it.id == movementId) updated else it }))
            return result
        }
        result[sourceKey] = source.copy(movements = source.movements.filterNot { it.id == movementId })
        val target = targetKey?.let { result[it] }
        if (updated != null && target != null) {
            result[targetKey] = target.copy(movements = sorted(target.movements + updated))
        }
        return result
    }

    private fun Movement.withData(changes: MovementChanges): Movement = copy(
        amount = changes.amount ?: amount,
        title = changes.title ?: title,
        note = when (val update = changes.note) {
            NoteUpdate.Unchanged -> note
            NoteUpdate.Remove -> null
            is NoteUpdate.Set -> update.text
        },
        date = changes.date ?: date,
        paymentMethodId = changes.paymentMethodId ?: paymentMethodId,
    )

    private suspend fun session(): MovementResult<Session> = try {
        MovementResult.Success(sessionProvider.current())
    } catch (e: SessionUnavailableException) {
        MovementResult.Failure(
            when (e.failure) {
                SessionFailure.NOT_SIGNED_IN -> MovementFailure.Unauthorized
                SessionFailure.UNKNOWN -> MovementFailure.Unknown
            },
        )
    }

    /** Carga la caché del mes a memoria; descarta la de otro usuario o la de 7 días o más. Requiere `storageLock`. */
    private suspend fun loadCache(key: Key, userId: String) {
        val cached = local.read(key.familyId, key.month)
        when {
            cached == null -> state.update { it - key }
            cached.userId != userId -> {
                local.clear()
                state.value = emptyMap()
            }
            CachePolicy.isExpired(cached.savedAtMillis, clock()) -> {
                local.remove(key.familyId, key.month)
                state.update { it - key }
            }
            else -> state.update { it + (key to Entry(sorted(cached.value), cached.savedAtMillis)) }
        }
    }
}
