package com.rocioplata.familyfinaiapp.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rocioplata.familyfinaiapp.domain.auth.AuthFailure
import com.rocioplata.familyfinaiapp.domain.auth.AuthRepository
import com.rocioplata.familyfinaiapp.domain.auth.AuthResult
import com.rocioplata.familyfinaiapp.domain.auth.AuthValidation
import com.rocioplata.familyfinaiapp.domain.auth.FieldError
import com.rocioplata.familyfinaiapp.domain.auth.SessionEndReason
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Estado y acciones de la pantalla de acceso (RF-01 a RF-04, RF-12). Vive solo en memoria: no usa
 * `SavedStateHandle`, para que las contraseñas nunca acaben en el estado guardado y, tras terminarse
 * el proceso, los campos aparezcan vacíos.
 */
class AuthViewModel(
    private val repository: AuthRepository,
    endReason: SessionEndReason? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AuthUiState(endReason = endReason))
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun onTabSelected(tab: AuthTab) {
        _uiState.update { state ->
            if (state.submitting || state.tab == tab) state else state.copy(tab = tab, generalError = null, endReason = null)
        }
    }

    fun onFieldChanged(field: AuthField, value: String) {
        _uiState.update { state ->
            if (state.submitting) return@update state
            var next = state.withField(field) { it.copy(value = value) }
            // Una vez que el campo ya se mostró al usuario, el error sigue a lo que escribe.
            if (next.field(field).touched) next = next.validated(field)
            // La confirmación depende de la contraseña.
            if (field == AuthField.RegisterPassword && next.field(AuthField.RegisterPasswordConfirmation).touched) {
                next = next.validated(AuthField.RegisterPasswordConfirmation)
            }
            next
        }
    }

    /** El campo tuvo foco y lo perdió: se valida y se muestra su error (RF-03). */
    fun onFieldBlur(field: AuthField) {
        _uiState.update { state -> if (state.submitting) state else state.validated(field) }
    }

    fun onTogglePasswordVisibility(field: AuthField) {
        _uiState.update { state ->
            if (!field.isPassword) return@update state
            val visible = if (field in state.visiblePasswords) state.visiblePasswords - field else state.visiblePasswords + field
            state.copy(visiblePasswords = visible)
        }
    }

    fun onSubmit() {
        val current = _uiState.value
        if (current.submitting) return
        val tab = current.tab
        val validated = tab.fields().fold(current) { state, field -> state.validated(field) }
        if (tab.fields().any { validated.field(it).error != null }) {
            _uiState.value = validated.copy(
                generalError = null,
                endReason = null,
                invalidSubmitCount = validated.invalidSubmitCount + 1,
            )
            return
        }
        // `submitting` se marca antes de lanzar la corrutina, así que una segunda pulsación se ignora (CA-16).
        _uiState.value = validated.copy(submitting = true, generalError = null, endReason = null)
        viewModelScope.launch {
            val result = try {
                send(validated)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // El repositorio no lanza por red o servidor, pero guardar la sesión cifrada puede fallar.
                AuthResult.Failure(AuthFailure.Unknown)
            }
            // Con éxito la raíz sustituye esta pantalla al iniciarse la sesión: no se rehabilita el botón.
            if (result is AuthResult.Failure) {
                _uiState.update { it.copy(submitting = false, generalError = result.failure) }
            }
        }
    }

    // Email y nombre se recortan; la contraseña se envía tal cual.
    private suspend fun send(state: AuthUiState): AuthResult = when (state.tab) {
        AuthTab.Login -> repository.login(
            email = state.field(AuthField.LoginEmail).value.trim(),
            password = state.field(AuthField.LoginPassword).value,
        )
        AuthTab.Register -> repository.register(
            displayName = state.field(AuthField.RegisterName).value.trim(),
            email = state.field(AuthField.RegisterEmail).value.trim(),
            password = state.field(AuthField.RegisterPassword).value,
        )
    }

    private fun AuthUiState.withField(field: AuthField, change: (FieldState) -> FieldState): AuthUiState =
        copy(fields = fields + (field to change(field(field))))

    private fun AuthUiState.validated(field: AuthField): AuthUiState =
        withField(field) { it.copy(touched = true, error = errorOf(field)) }

    private fun AuthUiState.errorOf(field: AuthField): FieldError? {
        val value = field(field).value
        return when (field) {
            AuthField.LoginEmail, AuthField.RegisterEmail -> AuthValidation.validateEmail(value)
            AuthField.LoginPassword -> AuthValidation.validateLoginPassword(value)
            AuthField.RegisterName -> AuthValidation.validateDisplayName(value)
            AuthField.RegisterPassword -> AuthValidation.validateNewPassword(value)
            AuthField.RegisterPasswordConfirmation ->
                AuthValidation.validatePasswordConfirmation(field(AuthField.RegisterPassword).value, value)
        }
    }
}
