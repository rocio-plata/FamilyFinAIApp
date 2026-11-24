package com.rocioplata.familyfinaiapp.domain.family

import com.rocioplata.familyfinaiapp.domain.auth.AuthValidation
import com.rocioplata.familyfinaiapp.domain.auth.FieldError
import java.time.Instant
import java.util.Locale

enum class InvitationEmailError { REQUIRED, INVALID_FORMAT, TOO_LONG, ALREADY_INVITED }

sealed interface InvitationEmailValidation {
    data class Valid(val email: String) : InvitationEmailValidation
    data class Invalid(val error: InvitationEmailError) : InvitationEmailValidation
}

/**
 * Regla del correo al invitar (RF-14, RF-17). El formato es el del registro (003); el máximo de 255 es el de la columna
 * del backend (BG-07). Una invitación vencida no impide invitar de nuevo al mismo correo.
 */
object InvitationValidation {
    const val MAX_EMAIL_LENGTH = 255

    fun validateEmail(
        email: String,
        sent: Collection<SentInvitation>,
        now: Instant,
    ): InvitationEmailValidation {
        val trimmed = email.trim()
        return when {
            AuthValidation.validateEmail(trimmed) == FieldError.REQUIRED ->
                InvitationEmailValidation.Invalid(InvitationEmailError.REQUIRED)
            trimmed.length > MAX_EMAIL_LENGTH -> InvitationEmailValidation.Invalid(InvitationEmailError.TOO_LONG)
            AuthValidation.validateEmail(trimmed) != null ->
                InvitationEmailValidation.Invalid(InvitationEmailError.INVALID_FORMAT)
            sent.any { !it.isExpired(now) && sameEmail(it.email, trimmed) } ->
                InvitationEmailValidation.Invalid(InvitationEmailError.ALREADY_INVITED)
            else -> InvitationEmailValidation.Valid(trimmed)
        }
    }

    fun sameEmail(a: String, b: String) = a.trim().lowercase(Locale.ROOT) == b.trim().lowercase(Locale.ROOT)
}
