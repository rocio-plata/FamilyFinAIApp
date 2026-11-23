package com.rocioplata.familyfinaiapp.data.header

import com.rocioplata.familyfinaiapp.data.cache.CachePolicy
import com.rocioplata.familyfinaiapp.domain.header.Family
import com.rocioplata.familyfinaiapp.domain.header.HeaderData
import com.rocioplata.familyfinaiapp.domain.header.HeaderFailure
import com.rocioplata.familyfinaiapp.domain.header.HeaderRefreshResult
import com.rocioplata.familyfinaiapp.domain.header.HeaderRepository
import com.rocioplata.familyfinaiapp.domain.header.Timed
import com.rocioplata.familyfinaiapp.session.Session
import com.rocioplata.familyfinaiapp.session.SessionFailure
import com.rocioplata.familyfinaiapp.session.SessionProvider
import com.rocioplata.familyfinaiapp.session.SessionUnavailableException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Reglas de caché (RF-07): se muestra solo la caché del usuario actual y de menos de 7 días;
 * otra cosa se elimina. Con más de 24 h se marca como desactualizada hasta un refresco correcto.
 */
class HeaderRepositoryImpl(
    private val remote: HeaderRemoteDataSource,
    private val local: HeaderLocalDataSource,
    private val sessionProvider: SessionProvider,
    /**
     * Recibe los identificadores de las familias vigentes cada vez que el backend entrega la lista o se aplica un cambio
     * local a ella (RF-23 de la 009): así otros repositorios olvidan lo guardado de las que ya no están. Se llama fuera
     * del candado de escritura; nunca por un fallo, por la caché ni al terminar la sesión.
     */
    private val onFamiliesKnown: suspend (Set<String>) -> Unit = {},
    private val clock: () -> Long = System::currentTimeMillis,
) : HeaderRepository {
    private val state = MutableStateFlow(HeaderData())
    private val mutex = Mutex()

    // Protege solo las escrituras de caché y estado (nunca la red). `clear()` lo usa sin esperar al
    // `mutex` de refresco: un refresco en curso puede estar esperando la renovación de la sesión,
    // que a su vez termina la sesión y llama a `clear()` (interbloqueo si compartieran candado).
    private val storageLock = Mutex()
    private var clearEpoch = 0

    // Se suben con cada cambio local de ese recurso: un refresco que empezó antes descarta lo que trae de él, porque
    // desharía el cambio (por ejemplo, una familia recién creada desaparecería hasta el refresco siguiente).
    private var familiesVersion = 0
    private var invitationsVersion = 0

    override val header: Flow<HeaderData> = state

    override suspend fun refresh(): HeaderRefreshResult = mutex.withLock {
        val session = try {
            sessionProvider.current()
        } catch (e: SessionUnavailableException) {
            return failAll(e.failure.toHeaderFailure())
        }
        val start = storageLock.withLock {
            loadCache(session.userId)
            Start(clearEpoch, familiesVersion, invitationsVersion)
        }
        fetch(session, start)
    }

    override suspend fun addFamily(family: Family) = changeFamilies { list ->
        if (list.any { it.id == family.id }) list.map { if (it.id == family.id) family else it } else list + family
    }

    override suspend fun removeFamily(familyId: String) = changeFamilies { list -> list.filterNot { it.id == familyId } }

    override suspend fun setFamilyOrder(orderedIds: List<String>) = changeFamilies { list ->
        val ordered = orderedIds.distinct().mapNotNull { id -> list.firstOrNull { it.id == id } }
        ordered + list.filter { family -> ordered.none { it.id == family.id } }
    }

    override suspend fun updateFamily(familyId: String, change: (Family) -> Family) = changeFamilies { list ->
        list.map { if (it.id == familyId) change(it) else it }
    }

    override suspend fun setPendingInvitations(count: Int) = storageLock.withLock {
        val userId = currentUserIdOrNull() ?: return@withLock
        invitationsVersion++
        val now = clock()
        local.savePendingInvitations(userId, now, count)
        state.update { build(invitations = Timed(count, now), current = it) }
    }

    // No espera a un refresco en curso: subir `clearEpoch` hace que ese refresco descarte lo que
    // obtenga en vez de guardarlo tras el borrado.
    override suspend fun clear() = storageLock.withLock {
        clearEpoch++
        local.clear()
        state.value = HeaderData()
    }

    private suspend fun loadCache(userId: String) {
        val now = clock()
        val families = local.readFamilies()
        val invitations = local.readPendingInvitations()
        val profile = local.readDisplayName()
        val entries = listOfNotNull(families, invitations, profile)
        val discard = entries.any { it.userId != userId || CachePolicy.isExpired(it.savedAtMillis, now) }
        if (discard) {
            local.clear()
            state.value = HeaderData()
            return
        }
        state.value = build(
            families = families?.let { Timed(it.value, it.savedAtMillis) },
            invitations = invitations?.let { Timed(it.value, it.savedAtMillis) },
            name = profile?.let { Timed(it.value, it.savedAtMillis) },
        )
    }

    // Aplica un cambio a la lista de familias ya cargada, conservando su `savedAt`.
    private suspend fun changeFamilies(change: (List<Family>) -> List<Family>) {
        val known = storageLock.withLock {
            val current = state.value.families ?: return@withLock null
            val userId = currentUserIdOrNull() ?: return@withLock null
            familiesVersion++
            val updated = Timed(change(current.value), current.savedAtMillis)
            local.saveFamilies(userId, updated.savedAtMillis, updated.value)
            state.update { build(families = updated, current = it) }
            updated.value.map { it.id }.toSet()
        }
        known?.let { onFamiliesKnown(it) }
    }

    private suspend fun currentUserIdOrNull(): String? = try {
        sessionProvider.current().userId
    } catch (e: SessionUnavailableException) {
        null
    }

    private class Start(val epoch: Int, val familiesVersion: Int, val invitationsVersion: Int)

    private suspend fun fetch(session: Session, start: Start): HeaderRefreshResult = coroutineScope {
        val families = async { remote.families() }
        val invitations = async { remote.pendingInvitations() }
        val profile = async { remote.displayName() }

        val familiesResult = families.await()
        val invitationsResult = invitations.await()
        val profileResult = profile.await()
        val now = clock()

        val known = storageLock.withLock {
            // Si se borró la caché mientras se esperaba la red, no se guarda nada (RF-09).
            if (start.epoch != clearEpoch) return@withLock null
            var knownFamilies: Set<String>? = null
            if (familiesResult is RemoteResult.Success && start.familiesVersion == familiesVersion) {
                local.saveFamilies(session.userId, now, familiesResult.value)
                state.update { build(families = Timed(familiesResult.value, now), current = it) }
                knownFamilies = familiesResult.value.map { it.id }.toSet()
            }
            if (invitationsResult is RemoteResult.Success && start.invitationsVersion == invitationsVersion) {
                local.savePendingInvitations(session.userId, now, invitationsResult.value)
                state.update { build(invitations = Timed(invitationsResult.value, now), current = it) }
            }
            if (profileResult is RemoteResult.Success) {
                local.saveDisplayName(session.userId, now, profileResult.value)
                state.update { build(name = Timed(profileResult.value, now), current = it) }
            }
            knownFamilies
        }
        known?.let { onFamiliesKnown(it) }

        HeaderRefreshResult(
            families = (familiesResult as? RemoteResult.Failure)?.failure,
            pendingInvitations = (invitationsResult as? RemoteResult.Failure)?.failure,
            profile = (profileResult as? RemoteResult.Failure)?.failure,
        )
    }

    private fun build(
        families: Timed<List<Family>>? = null,
        invitations: Timed<Int>? = null,
        name: Timed<String>? = null,
        current: HeaderData = HeaderData(),
    ): HeaderData {
        val merged = current.copy(
            families = families ?: current.families,
            pendingInvitations = invitations ?: current.pendingInvitations,
            displayName = name ?: current.displayName,
        )
        val now = clock()
        val oldest = listOfNotNull(
            merged.families?.savedAtMillis,
            merged.pendingInvitations?.savedAtMillis,
            merged.displayName?.savedAtMillis,
        ).minOrNull()
        return merged.copy(isStale = oldest != null && CachePolicy.isStale(oldest, now))
    }

    private fun failAll(failure: HeaderFailure) = HeaderRefreshResult(failure, failure, failure)

    private fun SessionFailure.toHeaderFailure(): HeaderFailure = when (this) {
        SessionFailure.NOT_SIGNED_IN -> HeaderFailure.Unauthorized
        SessionFailure.UNKNOWN -> HeaderFailure.Unknown
    }
}
