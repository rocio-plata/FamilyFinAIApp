package com.rocioplata.familyfinaiapp.data.header

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
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * Reglas de caché (RF-07): se muestra solo la caché del usuario actual y de menos de 7 días;
 * otra cosa se elimina. Con más de 24 h se marca como desactualizada hasta un refresco correcto.
 */
class HeaderRepositoryImpl(
    private val remote: HeaderRemoteDataSource,
    private val local: HeaderLocalDataSource,
    private val sessionProvider: SessionProvider,
    private val clock: () -> Long = System::currentTimeMillis,
) : HeaderRepository {
    private val state = MutableStateFlow(HeaderData())
    private val mutex = Mutex()

    override val header: Flow<HeaderData> = state

    override suspend fun refresh(): HeaderRefreshResult = mutex.withLock {
        val session = try {
            sessionProvider.current()
        } catch (e: SessionUnavailableException) {
            return failAll(e.failure.toHeaderFailure())
        }
        loadCache(session.userId)
        fetch(session)
    }

    private suspend fun loadCache(userId: String) {
        val now = clock()
        val families = local.readFamilies()
        val invitations = local.readPendingInvitations()
        val profile = local.readDisplayName()
        val entries = listOfNotNull(families, invitations, profile)
        val discard = entries.any { it.userId != userId || now - it.savedAtMillis >= MAX_CACHE_AGE_MS }
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

    private suspend fun fetch(session: Session): HeaderRefreshResult = coroutineScope {
        val families = async { remote.families(session) }
        val invitations = async { remote.pendingInvitations(session) }
        val profile = async { remote.displayName(session) }

        val familiesResult = families.await()
        val invitationsResult = invitations.await()
        val profileResult = profile.await()
        val now = clock()

        if (familiesResult is RemoteResult.Success) {
            local.saveFamilies(session.userId, now, familiesResult.value)
            state.update { build(families = Timed(familiesResult.value, now), current = it) }
        }
        if (invitationsResult is RemoteResult.Success) {
            local.savePendingInvitations(session.userId, now, invitationsResult.value)
            state.update { build(invitations = Timed(invitationsResult.value, now), current = it) }
        }
        if (profileResult is RemoteResult.Success) {
            local.saveDisplayName(session.userId, now, profileResult.value)
            state.update { build(name = Timed(profileResult.value, now), current = it) }
        }

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
        return merged.copy(isStale = oldest != null && now - oldest > STALE_AFTER_MS)
    }

    private fun failAll(failure: HeaderFailure) = HeaderRefreshResult(failure, failure, failure)

    private fun SessionFailure.toHeaderFailure(): HeaderFailure = when (this) {
        SessionFailure.NETWORK -> HeaderFailure.Network
        SessionFailure.INVALID_CREDENTIALS, SessionFailure.NOT_CONFIGURED -> HeaderFailure.Unauthorized
        SessionFailure.UNKNOWN -> HeaderFailure.Unknown
    }

    private companion object {
        val STALE_AFTER_MS = 24.hours.inWholeMilliseconds
        val MAX_CACHE_AGE_MS = 7.days.inWholeMilliseconds
    }
}
