package com.rocioplata.familyfinaiapp.domain.auth

/** Rechazos de login o registro que la interfaz traduce a mensajes (RF-04). */
sealed interface AuthFailure {
    /** Credenciales erróneas o usuario inexistente: nunca se distingue cuál. */
    data object InvalidCredentials : AuthFailure

    /** El backend rechazó el registro; incluye el email ya registrado, que no se revela. */
    data object RegistrationRejected : AuthFailure

    data object Network : AuthFailure

    data object Unknown : AuthFailure
}
