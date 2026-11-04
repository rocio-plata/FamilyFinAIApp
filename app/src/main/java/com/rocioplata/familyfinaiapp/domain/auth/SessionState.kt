package com.rocioplata.familyfinaiapp.domain.auth

/** Motivo por el que terminó una sesión; determina el mensaje del acceso (RF-07, RF-08). */
enum class SessionEndReason { Expired, Security, LoggedOut }

sealed interface SessionState {
    /** Se está leyendo la sesión guardada; no se muestra ni el acceso ni la pantalla principal. */
    data object Loading : SessionState

    /** Sin sesión. [reason] es nulo en el primer uso o al no encontrar una sesión guardada. */
    data class SignedOut(val reason: SessionEndReason? = null) : SessionState

    /** [generation] cambia en cada inicio de sesión para no reutilizar estado de una sesión anterior. */
    data class SignedIn(val userId: String, val generation: Long) : SessionState
}
