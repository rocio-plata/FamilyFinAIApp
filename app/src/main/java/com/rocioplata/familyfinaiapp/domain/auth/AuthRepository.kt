package com.rocioplata.familyfinaiapp.domain.auth

sealed interface AuthResult {
    /** La sesión quedó iniciada y guardada. */
    data object Success : AuthResult

    data class Failure(val failure: AuthFailure) : AuthResult
}

interface AuthRepository {
    /** Recibe el email ya recortado y la contraseña tal cual. Nunca lanza por red o servidor. */
    suspend fun login(email: String, password: String): AuthResult

    /** Recibe nombre y email ya recortados y la contraseña tal cual. Nunca lanza por red o servidor. */
    suspend fun register(displayName: String, email: String, password: String): AuthResult
}
