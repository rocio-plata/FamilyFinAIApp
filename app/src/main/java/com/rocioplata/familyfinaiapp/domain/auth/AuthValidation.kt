package com.rocioplata.familyfinaiapp.domain.auth

enum class FieldError {
    REQUIRED,
    INVALID_EMAIL,
    PASSWORD_TOO_SHORT,
    PASSWORDS_DO_NOT_MATCH,
    NAME_TOO_LONG,
}

/**
 * Reglas de los formularios de acceso (RF-03). Email y nombre se validan recortados;
 * la contraseña nunca se recorta. Las longitudes se cuentan como en el backend (unidades UTF-16).
 */
object AuthValidation {
    const val MIN_PASSWORD_LENGTH = 8
    const val MAX_DISPLAY_NAME_LENGTH = 60

    // Misma expresión que el contrato de `POST /auth/register`.
    private val EMAIL_PATTERN = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

    fun validateEmail(email: String): FieldError? {
        val trimmed = email.trim()
        return when {
            trimmed.isEmpty() -> FieldError.REQUIRED
            !EMAIL_PATTERN.matches(trimmed) -> FieldError.INVALID_EMAIL
            else -> null
        }
    }

    /** En el login solo se exige que no esté vacía, para no revelar la regla de longitud. */
    fun validateLoginPassword(password: String): FieldError? =
        if (password.isEmpty()) FieldError.REQUIRED else null

    fun validateNewPassword(password: String): FieldError? = when {
        password.isEmpty() -> FieldError.REQUIRED
        password.length < MIN_PASSWORD_LENGTH -> FieldError.PASSWORD_TOO_SHORT
        else -> null
    }

    fun validatePasswordConfirmation(password: String, confirmation: String): FieldError? = when {
        confirmation.isEmpty() -> FieldError.REQUIRED
        confirmation != password -> FieldError.PASSWORDS_DO_NOT_MATCH
        else -> null
    }

    fun validateDisplayName(name: String): FieldError? {
        val trimmed = name.trim()
        return when {
            trimmed.isEmpty() -> FieldError.REQUIRED
            trimmed.length > MAX_DISPLAY_NAME_LENGTH -> FieldError.NAME_TOO_LONG
            else -> null
        }
    }
}
