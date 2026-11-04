package com.rocioplata.familyfinaiapp.session

import com.rocioplata.familyfinaiapp.data.auth.AuthRemoteDataSource
import com.rocioplata.familyfinaiapp.data.auth.LogoutCallResult
import com.rocioplata.familyfinaiapp.data.auth.RefreshCallResult
import com.rocioplata.familyfinaiapp.data.session.SessionStore
import com.rocioplata.familyfinaiapp.data.session.StoredSession
import com.rocioplata.familyfinaiapp.domain.auth.SessionEndReason
import com.rocioplata.familyfinaiapp.domain.auth.SessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

sealed interface RefreshOutcome {
    data class Refreshed(val accessToken: String) : RefreshOutcome {
        override fun toString() = "Refreshed"
    }

    /** El backend rechazó la renovación y la sesión se cerró (RF-07). */
    data object SessionEnded : RefreshOutcome

    /** No se pudo renovar por red o servidor; la sesión se conserva (RF-10). */
    data class Unavailable(val offline: Boolean) : RefreshOutcome
}

enum class LogoutResult { Success, NoConnection, Unknown }

/**
 * Única fuente de verdad de la sesión, con ámbito de aplicación. [onSessionEnded] borra los datos
 * del usuario guardados por otras funcionalidades (RF-09).
 */
class SessionManager(
    private val store: SessionStore,
    private val remote: AuthRemoteDataSource,
    private val onSessionEnded: suspend () -> Unit,
) : SessionProvider {
    private val mutex = Mutex()
    private val _state = MutableStateFlow<SessionState>(SessionState.Loading)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    @Volatile
    private var session: StoredSession? = null
    private var generation = 0L

    /** Lee la sesión guardada una sola vez por proceso (RF-05). Sin red: una sesión guardada vale (RF-10). */
    suspend fun load() = mutex.withLock {
        if (_state.value != SessionState.Loading) return@withLock
        val stored = try {
            store.read()
        } catch (_: IOException) {
            // Un archivo de sesión ilegible equivale a no tener sesión.
            null
        }
        if (stored != null) {
            signIn(stored)
        } else {
            clearUserData(null)
        }
    }

    /** Guarda la sesión obtenida en login o registro y la activa con una generación nueva. */
    suspend fun start(newSession: StoredSession) = mutex.withLock {
        store.save(newSession)
        signIn(newSession)
    }

    /** Termina la sesión: borra sesión y datos del usuario y muestra el acceso con [reason] (RF-07, RF-09). */
    suspend fun endSession(reason: SessionEndReason) = mutex.withLock { endSessionLocked(reason) }

    /**
     * Renueva el acceso tras un 401 obtenido con [usedAccessToken] (RF-06). Bajo el `Mutex`, solo la
     * primera petición llama al backend; las demás reciben el token ya renovado, porque reutilizar el
     * refresh token haría que el backend cerrara todas las sesiones del usuario.
     */
    suspend fun refreshAfter(usedAccessToken: String): RefreshOutcome = mutex.withLock {
        val current = session ?: return@withLock RefreshOutcome.SessionEnded
        if (current.accessToken != usedAccessToken) return@withLock RefreshOutcome.Refreshed(current.accessToken)
        when (val result = remote.refresh(current.refreshToken)) {
            is RefreshCallResult.Success -> {
                val renewed = current.copy(accessToken = result.accessToken, refreshToken = result.refreshToken)
                // El backend ya invalidó el par anterior: primero en memoria y luego en disco, antes de usarlo.
                session = renewed
                store.save(renewed)
                RefreshOutcome.Refreshed(renewed.accessToken)
            }
            is RefreshCallResult.Rejected -> {
                endSessionLocked(result.reason)
                RefreshOutcome.SessionEnded
            }
            RefreshCallResult.Network -> RefreshOutcome.Unavailable(offline = true)
            RefreshCallResult.Unknown -> RefreshOutcome.Unavailable(offline = false)
        }
    }

    /**
     * Cierra la sesión en el backend (todas las del usuario) y luego en el teléfono (RF-08). Sin
     * conexión o con error no se toca nada. La llamada se hace sin el `Mutex`, porque el plugin de
     * sesión puede necesitar [refreshAfter] durante ella.
     */
    suspend fun logout(): LogoutResult {
        if (session == null) return LogoutResult.Success
        return when (remote.logout()) {
            // 401: el backend ya no acepta la sesión; se termina igualmente en el teléfono.
            LogoutCallResult.Success, LogoutCallResult.Unauthorized -> {
                endSession(SessionEndReason.LoggedOut)
                LogoutResult.Success
            }
            LogoutCallResult.NoConnection -> LogoutResult.NoConnection
            LogoutCallResult.Unknown -> LogoutResult.Unknown
        }
    }

    /** Token de acceso vigente para el plugin de sesión, o null si no hay sesión. */
    fun accessToken(): String? = session?.accessToken

    override suspend fun current(): Session {
        val current = session ?: throw SessionUnavailableException(SessionFailure.NOT_SIGNED_IN)
        return Session(current.userId, current.accessToken)
    }

    private fun signIn(stored: StoredSession) {
        session = stored
        generation++
        _state.value = SessionState.SignedIn(stored.userId, generation)
    }

    // Si la sesión ya terminó, se conserva el primer motivo.
    private suspend fun endSessionLocked(reason: SessionEndReason) {
        if (session == null && _state.value is SessionState.SignedOut) return
        session = null
        clearUserData(reason)
    }

    private suspend fun clearUserData(reason: SessionEndReason?) {
        try {
            store.clear()
            onSessionEnded()
        } finally {
            _state.value = SessionState.SignedOut(reason)
        }
    }
}
