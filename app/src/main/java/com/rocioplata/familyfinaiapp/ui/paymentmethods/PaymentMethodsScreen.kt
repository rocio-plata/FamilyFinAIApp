package com.rocioplata.familyfinaiapp.ui.paymentmethods

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
 * Entrada con estado del listado de medios de pago de [familyId]. [viewModelKey] debe incluir la sesión
 * y la familia (`main-<generación>-payment-methods-<familia>`): así cambiar de familia o de usuario nunca
 * reutiliza el estado de otro listado.
 */
@Composable
fun PaymentMethodsScreen(
    familyId: String,
    viewModelKey: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = (LocalContext.current.applicationContext as FamilyFinApp).container
    val viewModel: PaymentMethodsViewModel = viewModel(
        key = viewModelKey,
        factory = viewModelFactory {
            initializer {
                PaymentMethodsViewModel(container.paymentMethodsRepository, familyId, container.connectivityObserver)
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onStart() }
    // Al volver de otra pantalla el listado ya se había compuesto antes: se actualiza igual (se ignora si ya hay una carga).
    LaunchedEffect(Unit) { viewModel.onStart() }

    PaymentMethodsContent(
        state = state,
        onBack = onBack,
        onRetry = viewModel::retry,
        onToggleArchived = viewModel::toggleArchived,
        onCreate = viewModel::openCreate,
        onNameChange = viewModel::onNameChange,
        onSave = viewModel::save,
        onCloseForm = viewModel::requestClose,
        onConfirmDiscard = viewModel::confirmDiscard,
        onDismissDiscard = viewModel::dismissDiscard,
        modifier = modifier,
    )
}
