package com.rocioplata.familyfinaiapp.ui.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.rocioplata.familyfinaiapp.FamilyFinApp
import com.rocioplata.familyfinaiapp.domain.auth.SessionEndReason

/**
 * [viewModelKey] identifica cada episodio sin sesión: tras cerrar sesión se crea un ViewModel nuevo
 * y el acceso aparece vacío (RF-09). [endReason] solo se usa al crearlo.
 */
@Composable
fun AuthScreen(
    endReason: SessionEndReason?,
    viewModelKey: String,
    modifier: Modifier = Modifier,
) {
    val container = (LocalContext.current.applicationContext as FamilyFinApp).container
    val viewModel: AuthViewModel = viewModel(
        key = viewModelKey,
        factory = viewModelFactory { initializer { AuthViewModel(container.authRepository, endReason) } },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    AuthContent(
        state = state,
        onTabSelected = viewModel::onTabSelected,
        onFieldChanged = viewModel::onFieldChanged,
        onFieldBlur = viewModel::onFieldBlur,
        onTogglePasswordVisibility = viewModel::onTogglePasswordVisibility,
        onSubmit = viewModel::onSubmit,
        modifier = modifier,
    )
}
