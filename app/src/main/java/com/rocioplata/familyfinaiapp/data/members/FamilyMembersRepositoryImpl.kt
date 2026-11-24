package com.rocioplata.familyfinaiapp.data.members

import com.rocioplata.familyfinaiapp.data.cache.CachePolicy
import com.rocioplata.familyfinaiapp.domain.header.FamilyRole
import com.rocioplata.familyfinaiapp.domain.members.FamilyMember
import com.rocioplata.familyfinaiapp.domain.members.FamilyMembersData
import com.rocioplata.familyfinaiapp.domain.members.FamilyMembersFailure
import com.rocioplata.familyfinaiapp.domain.members.FamilyMembersRepository
import com.rocioplata.familyfinaiapp.domain.members.FamilyMembersResult
import com.rocioplata.familyfinaiapp.session.Session
import com.rocioplata.familyfinaiapp.session.SessionFailure
import com.rocioplata.familyfinaiapp.session.SessionProvider
import com.rocioplata.familyfinaiapp.session.SessionUnavailableException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Reglas de caché (RF-13): se muestra solo la caché del usuario actual y de menos de 7 días; otra cosa se
 * elimina. Con más de 24 h se marca como desactualizada hasta un refresco correcto. El estado en memoria es
 * por familia y el backend manda: la caché solo sirve para leer. No hay operaciones de cambio.
 */
class FamilyMembersRepositoryImpl(
    private val remote: FamilyMembersRemoteDataSource,
    private val local: FamilyMembersLocalDataSource,
    private val sessionProvider: SessionProvider,
    private val clock: () -> Long = System::currentTimeMillis,
) : FamilyMembersRepository {
    private data class Entry(val members: List<FamilyMember>, val savedAtMillis: Long)

    private val state = MutableStateFlow<Map<String, Entry>>(emptyMap())

    // Protege solo las escrituras de caché y estado (nunca la red). `clear()` lo usa sin esperar a un
    // refresco en curso, que puede estar esperando la renovación de la sesión, que a su vez termina la
    // sesión y llama a `clear()` (interbloqueo si compartieran candado, ver HeaderRepositoryImpl).
    // No es reentrante: `loadCache` lo exige y lo toma quien lo llama.
    private val storageLock = Mutex()
    private var clearEpoch = 0

    // Se sube con cada cambio local (rol o baja): un refresco que empezó antes descarta lo que trae, porque desharía el
    // cambio hasta el refresco siguiente. Solo se lee y escribe con `storageLock`.
    private var changeVersion = 0

    // Familias que el backend dice que siguen siendo del usuario (nulo hasta que se sabe). Una descarga en curso de una
    // que no está no se guarda, aunque no hubiera nada suyo en memoria ni en disco cuando se olvidaron (RF-23).
    // Solo se lee y escribe con `storageLock`.
    private var knownFamilyIds: Set<String>? = null

    private fun isKnown(familyId: String) = knownFamilyIds?.contains(familyId) != false

    private val refreshLocks = mutableMapOf<String, Mutex>()
    private val lastRefreshResults = mutableMapOf<String, FamilyMembersResult<Unit>>()

    override fun members(familyId: String): Flow<FamilyMembersData> =
        state
            .map { entries ->
                val entry = entries[familyId] ?: return@map FamilyMembersData()
                FamilyMembersData(entry.members, CachePolicy.isStale(entry.savedAtMillis, clock()))
            }
            .onStart {
                val current = session() as? FamilyMembersResult.Success ?: return@onStart
                storageLock.withLock { loadCache(familyId, current.value.userId) }
            }

    /** Un refresco a la vez por familia: una llamada simultánea espera y comparte su resultado. */
    override suspend fun refresh(familyId: String): FamilyMembersResult<Unit> {
        val lock = synchronized(refreshLocks) { refreshLocks.getOrPut(familyId) { Mutex() } }
        if (!lock.tryLock()) {
            lock.withLock { }
            val shared = synchronized(lastRefreshResults) { lastRefreshResults[familyId] }
            // Sin resultado compartido (el primero se canceló): se hace el refresco propio.
            return shared ?: refresh(familyId)
        }
        try {
            synchronized(lastRefreshResults) { lastRefreshResults.remove(familyId) }
            val result = fetch(familyId).map { }
            synchronized(lastRefreshResults) { lastRefreshResults[familyId] = result }
            return result
        } finally {
            lock.unlock()
        }
    }

    /** Sin compartir resultado con otro refresco en curso: los correos solo los recibe quien los pide. */
    override suspend fun refreshWithEmails(familyId: String): FamilyMembersResult<Map<String, String>> = fetch(familyId)

    override suspend fun applyRole(familyId: String, userId: String, role: FamilyRole) =
        changeMembers(familyId) { members -> members.map { if (it.userId == userId) it.copy(role = role) else it } }

    override suspend fun applyRemoval(familyId: String, userId: String) =
        changeMembers(familyId) { members -> members.filterNot { it.userId == userId } }

    // No espera a un refresco en curso: subir `clearEpoch` hace que ese refresco descarte lo que
    // obtenga en vez de guardarlo tras el borrado.
    override suspend fun clear() = storageLock.withLock {
        clearEpoch++
        knownFamilyIds = null
        local.clear()
        state.value = emptyMap()
    }

    // No espera a un refresco en curso: solo toma el candado de escritura, que nunca se mantiene durante la red.
    override suspend fun retainFamilies(familyIds: Set<String>) = storageLock.withLock {
        knownFamilyIds = familyIds
        local.retainFamilies(familyIds)
        state.update { entries -> entries.filterKeys { it in familyIds } }
    }

    private suspend fun fetch(familyId: String): FamilyMembersResult<Map<String, String>> {
        val session = when (val current = session()) {
            is FamilyMembersResult.Failure -> return current
            is FamilyMembersResult.Success -> current.value
        }
        val (epoch, version) = storageLock.withLock {
            loadCache(familyId, session.userId)
            clearEpoch to changeVersion
        }
        val remoteResult = remote.membersWithEmails(familyId)
        if (remoteResult is FamilyMembersResult.Failure) return remoteResult
        val payload = (remoteResult as FamilyMembersResult.Success).value
        val now = clock()
        storageLock.withLock {
            // Si se borró la caché o se aplicó un cambio local mientras se esperaba la red, no se guarda nada.
            if (epoch != clearEpoch || version != changeVersion || !isKnown(familyId)) return@withLock
            local.save(familyId, session.userId, now, payload.members)
            state.update { it + (familyId to Entry(payload.members, now)) }
        }
        return FamilyMembersResult.Success(payload.emails)
    }

    // Aplica un cambio a la lista ya cargada de la familia, conservando el `savedAt` de la última descarga.
    private suspend fun changeMembers(familyId: String, change: (List<FamilyMember>) -> List<FamilyMember>) =
        storageLock.withLock {
            val entry = state.value[familyId] ?: return@withLock
            val current = session() as? FamilyMembersResult.Success ?: return@withLock
            changeVersion++
            val updated = entry.copy(members = change(entry.members))
            local.save(familyId, current.value.userId, updated.savedAtMillis, updated.members)
            state.update { it + (familyId to updated) }
        }

    private fun <T, R> FamilyMembersResult<T>.map(transform: (T) -> R): FamilyMembersResult<R> = when (this) {
        is FamilyMembersResult.Failure -> this
        is FamilyMembersResult.Success -> FamilyMembersResult.Success(transform(value))
    }

    private suspend fun session(): FamilyMembersResult<Session> = try {
        FamilyMembersResult.Success(sessionProvider.current())
    } catch (e: SessionUnavailableException) {
        FamilyMembersResult.Failure(
            when (e.failure) {
                SessionFailure.NOT_SIGNED_IN -> FamilyMembersFailure.Unauthorized
                SessionFailure.UNKNOWN -> FamilyMembersFailure.Unknown
            },
        )
    }

    /** Carga la caché de la familia a memoria; descarta la de otro usuario o la de 7 días o más. Requiere `storageLock`. */
    private suspend fun loadCache(familyId: String, userId: String) {
        val cached = local.read(familyId)
        when {
            cached == null -> state.update { it - familyId }
            cached.userId != userId -> {
                local.clear()
                state.value = emptyMap()
            }
            CachePolicy.isExpired(cached.savedAtMillis, clock()) -> {
                local.remove(familyId)
                state.update { it - familyId }
            }
            else -> state.update { it + (familyId to Entry(cached.value, cached.savedAtMillis)) }
        }
    }
}
