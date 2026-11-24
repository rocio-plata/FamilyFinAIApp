package com.rocioplata.familyfinaiapp.ui.family

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.rocioplata.familyfinaiapp.FamilyFinApp
import com.rocioplata.familyfinaiapp.domain.auth.SessionState

/**
 * Entrada con estado de «Mi familia» de [familyId] (RF-03, RF-06 a RF-12). [viewModelKey] debe incluir la sesión y la
 * familia (`main-<generación>-family-<familia>`): así cambiar de familia o de usuario nunca reutiliza su estado.
 * [onInsufficientRole] avisa de que el backend rechazó una acción por rol o por no ser ya miembro (RF-22).
 */
@Composable
fun FamilyManagementScreen(
    familyId: String,
    viewModelKey: String,
    onBack: () -> Unit,
    onInsufficientRole: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = (LocalContext.current.applicationContext as FamilyFinApp).container
    val session by container.sessionManager.state.collectAsStateWithLifecycle()
    val currentUserId = (session as? SessionState.SignedIn)?.userId
    val viewModel: FamilyManagementViewModel = viewModel(
        key = viewModelKey,
        factory = viewModelFactory {
            initializer {
                FamilyManagementViewModel(
                    familyId = familyId,
                    currentUserId = currentUserId,
                    header = container.headerRepository,
                    members = container.membersRepository,
                    management = container.familyManagementRepository,
                    connectivity = container.connectivityObserver,
                    onInsufficientRole = onInsufficientRole,
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onStart() }

    FamilyManagementContent(
        state = state,
        onBack = onBack,
        onRetry = viewModel::retry,
        onChangeRole = viewModel::requestChangeRole,
        onRemove = viewModel::requestRemove,
        onLeave = viewModel::requestLeave,
        onConfirmAction = viewModel::confirmPendingAction,
        onDismissAction = viewModel::dismissPendingAction,
        onEventConsumed = viewModel::consumeEvent,
        modifier = modifier,
    )
}
