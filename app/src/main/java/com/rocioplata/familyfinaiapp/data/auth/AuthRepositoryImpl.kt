package com.rocioplata.familyfinaiapp.data.auth

import com.rocioplata.familyfinaiapp.domain.auth.AuthRepository
import com.rocioplata.familyfinaiapp.domain.auth.AuthResult
import com.rocioplata.familyfinaiapp.session.SessionManager

/** Con éxito entrega la sesión a [SessionManager], que la guarda cifrada y la activa (RF-01, RF-02). */
class AuthRepositoryImpl(
    private val remote: AuthRemoteDataSource,
    private val sessionManager: SessionManager,
) : AuthRepository {
    override suspend fun login(email: String, password: String): AuthResult =
        remote.login(email, password).toAuthResult()

    override suspend fun register(displayName: String, email: String, password: String): AuthResult =
        remote.register(displayName, email, password).toAuthResult()

    private suspend fun AuthCallResult.toAuthResult(): AuthResult = when (this) {
        is AuthCallResult.Success -> {
            sessionManager.start(session)
            AuthResult.Success
        }
        is AuthCallResult.Failure -> AuthResult.Failure(failure)
    }
}
