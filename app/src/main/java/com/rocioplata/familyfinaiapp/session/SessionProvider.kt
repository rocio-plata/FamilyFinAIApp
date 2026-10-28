package com.rocioplata.familyfinaiapp.session

data class Session(
    val userId: String,
    val accessToken: String,
) {
    // Evita que el token acabe en logs por un toString accidental.
    override fun toString(): String = "Session(userId=$userId)"
}

enum class SessionFailure { INVALID_CREDENTIALS, NOT_CONFIGURED, NETWORK, UNKNOWN }

class SessionUnavailableException(val failure: SessionFailure) : Exception(failure.name)

interface SessionProvider {
    /** Sesión vigente del usuario; lanza [SessionUnavailableException] si no hay sesión. */
    suspend fun current(): Session
}
