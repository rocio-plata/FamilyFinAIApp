package com.rocioplata.familyfinaiapp.ui.auth

import com.rocioplata.familyfinaiapp.domain.auth.AuthFailure
import com.rocioplata.familyfinaiapp.domain.auth.FieldError
import com.rocioplata.familyfinaiapp.domain.auth.SessionEndReason

enum class AuthTab { Login, Register }

/** Cada formulario tiene sus propios campos, de modo que alternar de pestaña conserva lo escrito (RF-12). */
enum class AuthField(val tab: AuthTab, val isPassword: Boolean = false) {
    LoginEmail(AuthTab.Login),
    LoginPassword(AuthTab.Login, isPassword = true),
    RegisterName(AuthTab.Register),
    RegisterEmail(AuthTab.Register),
    RegisterPassword(AuthTab.Register, isPassword = true),
    RegisterPasswordConfirmation(AuthTab.Register, isPassword = true),
}

/** Campos de la pestaña, en el orden en que se muestran. */
fun AuthTab.fields(): List<AuthField> = AuthField.entries.filter { it.tab == this }

/**
 * [value] es lo escrito sin recortar. [error] solo existe tras perder el foco o enviar ([touched]) y
 * desaparece al corregir el valor (RF-03).
 */
data class FieldState(
    val value: String = "",
    val touched: Boolean = false,
    val error: FieldError? = null,
) {
    // Evita que una contraseña acabe en logs por un toString accidental.
    override fun toString(): String = "FieldState(touched=$touched, error=$error)"
}

data class AuthUiState(
    val tab: AuthTab = AuthTab.Login,
    val fields: Map<AuthField, FieldState> = emptyMap(),
    /** Contraseñas mostradas en claro; por defecto todas están ocultas. */
    val visiblePasswords: Set<AuthField> = emptySet(),
    val submitting: Boolean = false,
    val generalError: AuthFailure? = null,
    /** Motivo del cierre de sesión que se muestra sobre el formulario hasta enviar o cambiar de pestaña. */
    val endReason: SessionEndReason? = null,
    /** Aumenta con cada envío rechazado por la validación; la pantalla lleva el foco al primer error (CA-04). */
    val invalidSubmitCount: Int = 0,
) {
    fun field(field: AuthField): FieldState = fields[field] ?: FieldState()

    override fun toString(): String =
        "AuthUiState(tab=$tab, submitting=$submitting, generalError=$generalError, endReason=$endReason)"
}
