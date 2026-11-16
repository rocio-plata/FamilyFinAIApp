package com.rocioplata.familyfinaiapp.data.paymentmethods

import com.rocioplata.familyfinaiapp.data.cache.CachePolicy
import com.rocioplata.familyfinaiapp.data.network.ConnectivityObserver
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethod
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethodFailure
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethodResult
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethodStatus
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethodsData
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethodsRepository
import com.rocioplata.familyfinaiapp.session.Session
import com.rocioplata.familyfinaiapp.session.SessionFailure
import com.rocioplata.familyfinaiapp.session.SessionProvider
import com.rocioplata.familyfinaiapp.session.SessionUnavailableException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Reglas de caché (RF-11): se muestra solo la caché del usuario actual y de menos de 7 días; otra
 * cosa se elimina. Con más de 24 h se marca como desactualizada hasta un refresco correcto.
 * El estado en memoria es por familia y el backend manda: la caché solo sirve para leer. Los cambios
 * requieren conexión (RF-12): sin ella no se llama al backend; con éxito se aplican a memoria y caché.
 */
class PaymentMethodsRepositoryImpl(
    private val remote: PaymentMethodsRemoteDataSource,
    private val local: PaymentMethodsLocalDataSource,
    private val sessionProvider: SessionProvider,
    private val connectivity: ConnectivityObserver,
    private val clock: () -> Long = System::currentTimeMillis,
) : PaymentMethodsRepository {
    /** `savedAtMillis` es el de la última descarga completa, no el de un cambio posterior. */
    private data class Entry(val paymentMethods: List<PaymentMethod>, val savedAtMillis: Long)

    private val state = MutableStateFlow<Map<String, Entry>>(emptyMap())

    // Protege solo las escrituras de caché y estado (nunca la red). `clear()` lo usa sin esperar a un
    // refresco en curso, que puede estar esperando la renovación de la sesión, que a su vez termina la
    // sesión y llama a `clear()` (interbloqueo si compartieran candado, ver HeaderRepositoryImpl).
    // No es reentrante: `loadCache` lo exige y lo toma quien lo llama.
    private val storageLock = Mutex()
    private var clearEpoch = 0

    private val refreshLocks = mutableMapOf<String, Mutex>()
    private val lastRefreshResults = mutableMapOf<String, PaymentMethodResult<Unit>>()

    override fun paymentMethods(familyId: String): Flow<PaymentMethodsData> =
        state
            .map { entries ->
                val entry = entries[familyId] ?: return@map PaymentMethodsData()
                PaymentMethodsData(entry.paymentMethods, CachePolicy.isStale(entry.savedAtMillis, clock()))
            }
            .onStart {
                val current = session() as? PaymentMethodResult.Success ?: return@onStart
                storageLock.withLock { loadCache(familyId, current.value.userId) }
            }

    /** Un refresco a la vez por familia: una llamada simultánea espera y comparte su resultado. */
    override suspend fun refresh(familyId: String): PaymentMethodResult<Unit> {
        val lock = synchronized(refreshLocks) { refreshLocks.getOrPut(familyId) { Mutex() } }
        if (!lock.tryLock()) {
            lock.withLock { }
            val shared = synchronized(lastRefreshResults) { lastRefreshResults[familyId] }
            // Sin resultado compartido (el primero se canceló): se hace el refresco propio.
            return shared ?: refresh(familyId)
        }
        try {
            synchronized(lastRefreshResults) { lastRefreshResults.remove(familyId) }
            val result = fetch(familyId)
            synchronized(lastRefreshResults) { lastRefreshResults[familyId] = result }
            return result
        } finally {
            lock.unlock()
        }
    }

    // No espera a un refresco en curso: subir `clearEpoch` hace que ese refresco descarte lo que
    // obtenga en vez de guardarlo tras el borrado.
    override suspend fun clear() = storageLock.withLock {
        clearEpoch++
        local.clear()
        state.value = emptyMap()
    }

    // El backend no ordena por estado: el nuevo medio va al final de la lista y, al ser activo, al final de los activos.
    override suspend fun createPaymentMethod(familyId: String, name: String): PaymentMethodResult<PaymentMethod> =
        change(familyId, { remote.createPaymentMethod(familyId, name) }) { methods, created -> methods + created }

    override suspend fun renamePaymentMethod(
        familyId: String,
        paymentMethodId: String,
        name: String,
    ): PaymentMethodResult<Unit> = change(
        familyId,
        { remote.renamePaymentMethod(familyId, paymentMethodId, name) },
    ) { methods, _ -> methods.update(paymentMethodId) { it.copy(name = name) } }

    override suspend fun deprecatePaymentMethod(familyId: String, paymentMethodId: String): PaymentMethodResult<Unit> =
        change(familyId, { remote.deprecatePaymentMethod(familyId, paymentMethodId) }) { methods, _ ->
            methods.update(paymentMethodId) { it.copy(status = PaymentMethodStatus.DEPRECATED) }
        }

    override suspend fun deletePaymentMethod(familyId: String, paymentMethodId: String): PaymentMethodResult<Unit> =
        change(familyId, { remote.deletePaymentMethod(familyId, paymentMethodId) }) { methods, _ ->
            methods.filterNot { it.id == paymentMethodId }
        }

    private suspend fun fetch(familyId: String): PaymentMethodResult<Unit> {
        val session = when (val current = session()) {
            is PaymentMethodResult.Failure -> return current
            is PaymentMethodResult.Success -> current.value
        }
        val epoch = storageLock.withLock {
            loadCache(familyId, session.userId)
            clearEpoch
        }
        val remoteResult = remote.paymentMethods(familyId)
        if (remoteResult is PaymentMethodResult.Failure) return remoteResult
        val methods = (remoteResult as PaymentMethodResult.Success).value
        val now = clock()
        storageLock.withLock {
            // Si se borró la caché mientras se esperaba la red, no se guarda nada.
            if (epoch != clearEpoch) return@withLock
            local.save(familyId, session.userId, now, methods)
            state.update { it + (familyId to Entry(methods, now)) }
        }
        return PaymentMethodResult.Success(Unit)
    }

    private suspend fun session(): PaymentMethodResult<Session> = try {
        PaymentMethodResult.Success(sessionProvider.current())
    } catch (e: SessionUnavailableException) {
        PaymentMethodResult.Failure(
            when (e.failure) {
                SessionFailure.NOT_SIGNED_IN -> PaymentMethodFailure.Unauthorized
                SessionFailure.UNKNOWN -> PaymentMethodFailure.Unknown
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

    /**
     * Ejecuta un cambio: sin conexión no llama al backend (RF-12); con éxito aplica [apply] a la lista
     * en memoria y en caché, conservando el `savedAt` de la última descarga completa. Un fallo no
     * cambia nada. Si no hay datos cargados de la familia solo se devuelve el resultado.
     */
    private suspend fun <T> change(
        familyId: String,
        request: suspend () -> PaymentMethodResult<T>,
        apply: (List<PaymentMethod>, T) -> List<PaymentMethod>,
    ): PaymentMethodResult<T> {
        if (!connectivity.isOnline.first()) return PaymentMethodResult.Failure(PaymentMethodFailure.Offline)
        val session = when (val current = session()) {
            is PaymentMethodResult.Failure -> return current
            is PaymentMethodResult.Success -> current.value
        }
        val epoch = storageLock.withLock { clearEpoch }
        val result = request()
        if (result !is PaymentMethodResult.Success) return result
        storageLock.withLock {
            // Si se borró la caché mientras se esperaba la red, no se guarda nada.
            if (epoch != clearEpoch) return@withLock
            val entry = state.value[familyId] ?: return@withLock
            val updated = Entry(apply(entry.paymentMethods, result.value), entry.savedAtMillis)
            local.save(familyId, session.userId, updated.savedAtMillis, updated.paymentMethods)
            state.update { it + (familyId to updated) }
        }
        return result
    }

    private fun List<PaymentMethod>.update(id: String, change: (PaymentMethod) -> PaymentMethod) =
        map { if (it.id == id) change(it) else it }
}
