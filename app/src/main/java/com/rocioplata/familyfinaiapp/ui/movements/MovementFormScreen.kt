package com.rocioplata.familyfinaiapp.ui.movements

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.rocioplata.familyfinaiapp.FamilyFinApp

/**
 * Entrada con estado del formulario de [familyId]: "Nuevo movimiento" o, con [movementId], "Editar movimiento". [viewModelKey] debe incluir la sesión,
 * la familia y la apertura de la ruta (`main-<generación>-movement-form-<familia>-<apertura>`): así cada
 * apertura empieza con un formulario vacío y cambiar de familia o de usuario nunca reutiliza lo escrito.
 * [onClose] se llama al guardar, al descartar y al salir sin cambios.
 */
@Composable
fun MovementFormScreen(
    familyId: String,
    familyName: String,
    currency: String?,
    viewModelKey: String,
    onClose: () -> Unit,
    onInsufficientRole: () -> Unit,
    onCurrencyMismatch: () -> Unit,
    modifier: Modifier = Modifier,
    /** Movimiento que se edita; nulo para un alta. */
    movementId: String? = null,
) {
    val container = (LocalContext.current.applicationContext as FamilyFinApp).container
    val viewModel: MovementFormViewModel = viewModel(
        key = viewModelKey,
        factory = viewModelFactory {
            initializer {
                MovementFormViewModel(
                    movements = container.movementsRepository,
                    categories = container.categoriesRepository,
                    paymentMethods = container.paymentMethodsRepository,
                    familyId = familyId,
                    currency = currency,
                    dates = container.movementDates,
                    onClose = onClose,
                    onInsufficientRole = onInsufficientRole,
                    onCurrencyMismatch = onCurrencyMismatch,
                    movementId = movementId,
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    MovementFormContent(
        state = state,
        familyName = familyName,
        onClose = viewModel::requestClose,
        onTypeChange = viewModel::onTypeChange,
        onAmountChange = viewModel::onAmountChange,
        onTitleChange = viewModel::onTitleChange,
        onCategorySelect = viewModel::onCategorySelect,
        onTagSelect = viewModel::onTagSelect,
        onPaymentMethodSelect = viewModel::onPaymentMethodSelect,
        onToday = viewModel::onToday,
        onYesterday = viewModel::onYesterday,
        onDateSelect = viewModel::onDateSelect,
        onNoteChange = viewModel::onNoteChange,
        onSave = viewModel::save,
        onRetryOptions = viewModel::retryOptions,
        onConfirmDiscard = viewModel::confirmDiscard,
        onDismissDiscard = viewModel::dismissDiscard,
        onNoticeConsumed = viewModel::consumeNotice,
        modifier = modifier,
    )
}
