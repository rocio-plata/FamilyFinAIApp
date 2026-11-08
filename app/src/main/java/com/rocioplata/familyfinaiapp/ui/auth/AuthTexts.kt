package com.rocioplata.familyfinaiapp.ui.auth

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.rocioplata.familyfinaiapp.R
import com.rocioplata.familyfinaiapp.domain.auth.AuthFailure
import com.rocioplata.familyfinaiapp.domain.auth.AuthValidation
import com.rocioplata.familyfinaiapp.domain.auth.FieldError
import com.rocioplata.familyfinaiapp.domain.auth.SessionEndReason

/** Los límites salen de [AuthValidation], el único lugar donde se definen. */
@Composable
fun FieldError.message(): String = when (this) {
    FieldError.REQUIRED -> stringResource(R.string.auth_error_required)
    FieldError.INVALID_EMAIL -> stringResource(R.string.auth_error_invalid_email)
    FieldError.PASSWORD_TOO_SHORT ->
        stringResource(R.string.auth_error_password_too_short, AuthValidation.MIN_PASSWORD_LENGTH)
    FieldError.PASSWORDS_DO_NOT_MATCH -> stringResource(R.string.auth_error_passwords_do_not_match)
    FieldError.NAME_TOO_LONG -> stringResource(R.string.auth_error_name_too_long, AuthValidation.MAX_DISPLAY_NAME_LENGTH)
}

@StringRes
fun AuthFailure.messageRes(): Int = when (this) {
    AuthFailure.InvalidCredentials -> R.string.auth_message_invalid_credentials
    AuthFailure.RegistrationRejected -> R.string.auth_message_registration_rejected
    AuthFailure.Network -> R.string.auth_message_no_connection
    AuthFailure.Unknown -> R.string.auth_message_generic_error
}

/** Tras cerrar sesión por decisión del usuario no se muestra ningún mensaje (RF-09). */
@StringRes
fun SessionEndReason.messageRes(): Int? = when (this) {
    SessionEndReason.Expired -> R.string.auth_message_session_expired
    SessionEndReason.Security -> R.string.auth_message_session_security
    SessionEndReason.LoggedOut -> null
}

@StringRes
fun AuthTab.labelRes(): Int = when (this) {
    AuthTab.Login -> R.string.auth_tab_login
    AuthTab.Register -> R.string.auth_tab_register
}
