package com.rocioplata.familyfinaiapp.ui.auth

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rocioplata.familyfinaiapp.R
import com.rocioplata.familyfinaiapp.domain.auth.AuthFailure
import com.rocioplata.familyfinaiapp.domain.auth.AuthValidation
import com.rocioplata.familyfinaiapp.domain.auth.FieldError
import com.rocioplata.familyfinaiapp.domain.auth.SessionEndReason
import com.rocioplata.familyfinaiapp.ui.theme.FamilyFinAIAppTheme

/** Contenido sin estado de la pantalla de acceso (RF-01, RF-12). */
@Composable
fun AuthContent(
    state: AuthUiState,
    onTabSelected: (AuthTab) -> Unit,
    onFieldChanged: (AuthField, String) -> Unit,
    onFieldBlur: (AuthField) -> Unit,
    onTogglePasswordVisibility: (AuthField) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequesters = remember { AuthField.entries.associateWith { FocusRequester() } }
    FocusFirstErrorOnInvalidSubmit(state, focusRequesters)
    // Atrás en "Crear cuenta" vuelve a "Iniciar sesión"; en el login sale de la app (CA-18).
    BackHandler(enabled = state.tab == AuthTab.Register && !state.submitting) { onTabSelected(AuthTab.Login) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        AuthHeader()
        AuthTabs(selected = state.tab, enabled = !state.submitting, onSelected = onTabSelected)
        AuthMessage(state)
        when (state.tab) {
            AuthTab.Login -> LoginForm(state, focusRequesters, onFieldChanged, onFieldBlur, onTogglePasswordVisibility, onSubmit)
            AuthTab.Register ->
                RegisterForm(state, focusRequesters, onFieldChanged, onFieldBlur, onTogglePasswordVisibility, onSubmit)
        }
        AuthFooter(tab = state.tab, enabled = !state.submitting, onTabSelected = onTabSelected)
    }
}

@Composable
private fun AuthHeader() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Decorativo: el nombre de la app va justo debajo. El logo lleva su propio fondo.
        Image(
            painter = painterResource(R.drawable.ic_brand_logo),
            contentDescription = null,
            modifier = Modifier.size(88.dp),
        )
        Text(
            text = stringResource(R.string.auth_brand_name),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(R.string.auth_tagline),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

// El botón seleccionado muestra una marca además del color, y se anuncia como seleccionado (RF-12, CA-23).
@Composable
private fun AuthTabs(selected: AuthTab, enabled: Boolean, onSelected: (AuthTab) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        AuthTab.entries.forEachIndexed { index, tab ->
            SegmentedButton(
                selected = tab == selected,
                onClick = { onSelected(tab) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = AuthTab.entries.size),
                enabled = enabled,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(stringResource(tab.labelRes()))
            }
        }
    }
}

// Un solo mensaje sobre el formulario: el de cierre forzado o el error del último envío (RF-04, RF-07).
@Composable
private fun AuthMessage(state: AuthUiState) {
    val failure = state.generalError
    val messageRes = failure?.messageRes() ?: state.endReason?.messageRes() ?: return
    val isError = failure != null
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Text(
            text = stringResource(messageRes),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp),
        )
    }
}

/**
 * Cuando el envío es rechazado por la validación, el foco pasa al primer campo con error de la
 * pestaña activa (CA-04). Se recuerda cuántos envíos rechazados ya se atendieron para no volver a
 * mover el foco al recrear la pantalla, ya que el ViewModel conserva el contador.
 */
@Composable
private fun FocusFirstErrorOnInvalidSubmit(state: AuthUiState, focusRequesters: Map<AuthField, FocusRequester>) {
    var handled by rememberSaveable { mutableIntStateOf(state.invalidSubmitCount) }
    LaunchedEffect(state.invalidSubmitCount) {
        if (state.invalidSubmitCount == handled) return@LaunchedEffect
        handled = state.invalidSubmitCount
        state.tab.fields().firstOrNull { state.field(it).error != null }?.let { focusRequesters.getValue(it).requestFocus() }
    }
}

@Composable
private fun AuthFormCard(content: @Composable () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { content() }
    }
}

@Composable
private fun LoginForm(
    state: AuthUiState,
    focusRequesters: Map<AuthField, FocusRequester>,
    onFieldChanged: (AuthField, String) -> Unit,
    onFieldBlur: (AuthField) -> Unit,
    onTogglePasswordVisibility: (AuthField) -> Unit,
    onSubmit: () -> Unit,
) {
    val enabled = !state.submitting
    AuthFormCard {
        AuthTextField(
            field = state.field(AuthField.LoginEmail),
            label = stringResource(R.string.auth_field_email),
            leadingIcon = Icons.Outlined.Email,
            onValueChange = { onFieldChanged(AuthField.LoginEmail, it) },
            onBlur = { onFieldBlur(AuthField.LoginEmail) },
            enabled = enabled,
            modifier = Modifier.focusRequester(focusRequesters.getValue(AuthField.LoginEmail)),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
        )
        AuthTextField(
            field = state.field(AuthField.LoginPassword),
            label = stringResource(R.string.auth_field_password),
            leadingIcon = Icons.Outlined.Lock,
            onValueChange = { onFieldChanged(AuthField.LoginPassword, it) },
            onBlur = { onFieldBlur(AuthField.LoginPassword) },
            enabled = enabled,
            modifier = Modifier.focusRequester(focusRequesters.getValue(AuthField.LoginPassword)),
            passwordVisible = AuthField.LoginPassword in state.visiblePasswords,
            onTogglePasswordVisibility = { onTogglePasswordVisibility(AuthField.LoginPassword) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        )
        SubmitButton(text = stringResource(R.string.auth_button_login), submitting = state.submitting, onClick = onSubmit)
    }
}

// "Siguiente" pasa al campo siguiente y la acción del último campo envía el formulario (RF-02).
@Composable
private fun RegisterForm(
    state: AuthUiState,
    focusRequesters: Map<AuthField, FocusRequester>,
    onFieldChanged: (AuthField, String) -> Unit,
    onFieldBlur: (AuthField) -> Unit,
    onTogglePasswordVisibility: (AuthField) -> Unit,
    onSubmit: () -> Unit,
) {
    val enabled = !state.submitting
    AuthFormCard {
        AuthTextField(
            field = state.field(AuthField.RegisterName),
            label = stringResource(R.string.auth_field_display_name),
            leadingIcon = Icons.Outlined.Person,
            onValueChange = { onFieldChanged(AuthField.RegisterName, it) },
            onBlur = { onFieldBlur(AuthField.RegisterName) },
            enabled = enabled,
            modifier = Modifier.focusRequester(focusRequesters.getValue(AuthField.RegisterName)),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
        )
        AuthTextField(
            field = state.field(AuthField.RegisterEmail),
            label = stringResource(R.string.auth_field_email),
            leadingIcon = Icons.Outlined.Email,
            onValueChange = { onFieldChanged(AuthField.RegisterEmail, it) },
            onBlur = { onFieldBlur(AuthField.RegisterEmail) },
            enabled = enabled,
            modifier = Modifier.focusRequester(focusRequesters.getValue(AuthField.RegisterEmail)),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
        )
        AuthTextField(
            field = state.field(AuthField.RegisterPassword),
            label = stringResource(R.string.auth_field_password),
            leadingIcon = Icons.Outlined.Lock,
            onValueChange = { onFieldChanged(AuthField.RegisterPassword, it) },
            onBlur = { onFieldBlur(AuthField.RegisterPassword) },
            enabled = enabled,
            modifier = Modifier.focusRequester(focusRequesters.getValue(AuthField.RegisterPassword)),
            helper = stringResource(R.string.auth_password_helper, AuthValidation.MIN_PASSWORD_LENGTH),
            passwordVisible = AuthField.RegisterPassword in state.visiblePasswords,
            onTogglePasswordVisibility = { onTogglePasswordVisibility(AuthField.RegisterPassword) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
        )
        AuthTextField(
            field = state.field(AuthField.RegisterPasswordConfirmation),
            label = stringResource(R.string.auth_field_password_confirmation),
            leadingIcon = Icons.Outlined.Lock,
            onValueChange = { onFieldChanged(AuthField.RegisterPasswordConfirmation, it) },
            onBlur = { onFieldBlur(AuthField.RegisterPasswordConfirmation) },
            enabled = enabled,
            modifier = Modifier.focusRequester(focusRequesters.getValue(AuthField.RegisterPasswordConfirmation)),
            passwordVisible = AuthField.RegisterPasswordConfirmation in state.visiblePasswords,
            onTogglePasswordVisibility = { onTogglePasswordVisibility(AuthField.RegisterPasswordConfirmation) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        )
        SubmitButton(text = stringResource(R.string.auth_button_register), submitting = state.submitting, onClick = onSubmit)
    }
}

/**
 * Campo con etiqueta visible. Si [onTogglePasswordVisibility] no es nulo es un campo de contraseña,
 * oculta por defecto, con un control para mostrarla. El error se muestra como texto y se anuncia (RF-03);
 * sin error se muestra la ayuda [helper], si hay.
 * [onBlur] se llama al perder el foco después de haberlo tenido.
 */
@Composable
private fun AuthTextField(
    field: FieldState,
    label: String,
    leadingIcon: ImageVector,
    onValueChange: (String) -> Unit,
    onBlur: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    helper: String? = null,
    passwordVisible: Boolean = false,
    onTogglePasswordVisibility: (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    var hadFocus by remember { mutableStateOf(false) }
    val errorText = field.error?.message()
    OutlinedTextField(
        value = field.value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = { Icon(leadingIcon, contentDescription = null) },
        trailingIcon = onTogglePasswordVisibility?.let { toggle ->
            {
                IconButton(onClick = toggle, enabled = enabled) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = stringResource(
                            if (passwordVisible) R.string.auth_password_hide else R.string.auth_password_show,
                        ),
                    )
                }
            }
        },
        visualTransformation = if (onTogglePasswordVisibility == null || passwordVisible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        isError = errorText != null,
        supportingText = (errorText ?: helper)?.let { { Text(it) } },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        modifier = modifier
            .fillMaxWidth()
            .semantics { if (errorText != null) error(errorText) }
            .onFocusChanged { focus ->
                if (focus.isFocused) {
                    hadFocus = true
                } else if (hadFocus) {
                    hadFocus = false
                    onBlur()
                }
            },
    )
}

// El progreso acompaña al texto, que sigue visible para el lector de pantalla.
@Composable
private fun SubmitButton(text: String, submitting: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = !submitting,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
    ) {
        if (submitting) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = LocalContentColor.current,
            )
            Spacer(Modifier.width(12.dp))
        }
        Text(text)
    }
}

@Composable
private fun AuthFooter(tab: AuthTab, enabled: Boolean, onTabSelected: (AuthTab) -> Unit) {
    val (prompt, action, target) = when (tab) {
        AuthTab.Login -> Triple(R.string.auth_footer_to_register_prompt, R.string.auth_footer_to_register_action, AuthTab.Register)
        AuthTab.Register -> Triple(R.string.auth_footer_to_login_prompt, R.string.auth_footer_to_login_action, AuthTab.Login)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Text(
            text = stringResource(prompt),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f, fill = false),
        )
        TextButton(onClick = { onTabSelected(target) }, enabled = enabled) { Text(stringResource(action)) }
    }
}

@Composable
private fun AuthPreview(state: AuthUiState) {
    FamilyFinAIAppTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            AuthContent(state, {}, { _, _ -> }, {}, {}, {})
        }
    }
}

@Preview(showBackground = true, heightDp = 780)
@Composable
private fun AuthContentLoginPreview() = AuthPreview(AuthUiState())

// El tema oscuro sigue el ajuste del sistema: la preview nocturna muestra también la variante oscura del logo.
@Preview(showBackground = true, heightDp = 780, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AuthContentLoginNightPreview() = AuthPreview(AuthUiState())

@Preview(showBackground = true, heightDp = 780)
@Composable
private fun AuthContentErrorsPreview() = AuthPreview(
    AuthUiState(
        endReason = SessionEndReason.Expired,
        fields = mapOf(
            AuthField.LoginEmail to FieldState("ana@", touched = true, error = FieldError.INVALID_EMAIL),
            AuthField.LoginPassword to FieldState("", touched = true, error = FieldError.REQUIRED),
        ),
    ),
)

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun AuthContentRegisterPreview() = AuthPreview(AuthUiState(tab = AuthTab.Register))

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun AuthContentRegisterErrorsPreview() = AuthPreview(
    AuthUiState(
        tab = AuthTab.Register,
        visiblePasswords = setOf(AuthField.RegisterPassword),
        fields = mapOf(
            AuthField.RegisterName to FieldState("", touched = true, error = FieldError.REQUIRED),
            AuthField.RegisterEmail to FieldState("ana@mail.com"),
            AuthField.RegisterPassword to FieldState("corta", touched = true, error = FieldError.PASSWORD_TOO_SHORT),
            AuthField.RegisterPasswordConfirmation to
                FieldState("otra", touched = true, error = FieldError.PASSWORDS_DO_NOT_MATCH),
        ),
    ),
)

@Preview(showBackground = true, heightDp = 780)
@Composable
private fun AuthContentSubmittingPreview() = AuthPreview(
    AuthUiState(
        submitting = true,
        generalError = AuthFailure.Network,
        fields = mapOf(
            AuthField.LoginEmail to FieldState("ana@mail.com"),
            AuthField.LoginPassword to FieldState("secreto"),
        ),
    ),
)
