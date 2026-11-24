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

/**
 * Entrada con estado de «Invitaciones» (RF-18 a RF-21). [viewModelKey] debe incluir la sesión y la apertura de la ruta
 * (`main-<generación>-invitations-<instancia>`): así cada apertura carga la lista de nuevo y el estado sobrevive a
 * recrear la pantalla. [onSwitchFamily] activa la familia aceptada y lleva a Inicio (RF-19).
 */
@Composable
fun InvitationsScreen(
    viewModelKey: String,
    onBack: () -> Unit,
    onSwitchFamily: (familyId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = (LocalContext.current.applicationContext as FamilyFinApp).container
    val viewModel: InvitationsViewModel = viewModel(
        key = viewModelKey,
        factory = viewModelFactory {
            initializer { InvitationsViewModel(container.invitationsRepository, container.connectivityObserver) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onStart() }

    InvitationsContent(
        state = state,
        onBack = onBack,
        onSwitchFamily = onSwitchFamily,
        onEventConsumed = viewModel::consumeEvent,
        actions = ReceivedInvitationActions(
            onAccept = viewModel::accept,
            onDecline = viewModel::requestDecline,
            onConfirmDecline = viewModel::confirmDecline,
            onDismissDecline = viewModel::dismissDecline,
            onRetry = viewModel::retry,
        ),
        modifier = modifier,
    )
}
