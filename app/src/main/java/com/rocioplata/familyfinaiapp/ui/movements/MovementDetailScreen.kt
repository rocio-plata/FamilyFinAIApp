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
 * Entrada con estado del detalle del movimiento [movementId] de [familyId]. [viewModelKey] debe incluir la
 * sesión, la familia, el movimiento y la apertura de la ruta: así abrir otro detalle nunca reutiliza el
 * estado de éste, y volver de la edición al mismo detalle sí lo conserva. [onClose] se llama al eliminar y al
 * volver al listado; [onEdit] abre la edición del movimiento.
 */
@Composable
fun MovementDetailScreen(
    familyId: String,
    movementId: String,
    currentUserId: String?,
    viewModelKey: String,
    onClose: () -> Unit,
    onEdit: () -> Unit,
    onInsufficientRole: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = (LocalContext.current.applicationContext as FamilyFinApp).container
    val viewModel: MovementDetailViewModel = viewModel(
        key = viewModelKey,
        factory = viewModelFactory {
            initializer {
                MovementDetailViewModel(
                    movements = container.movementsRepository,
                    categories = container.categoriesRepository,
                    paymentMethods = container.paymentMethodsRepository,
                    members = container.membersRepository,
                    familyId = familyId,
                    movementId = movementId,
                    currentUserId = currentUserId,
                    onClose = onClose,
                    onInsufficientRole = onInsufficientRole,
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    MovementDetailContent(
        state = state,
        onBack = viewModel::requestClose,
        onEdit = onEdit,
        onDelete = viewModel::requestDelete,
        onConfirmDelete = viewModel::confirmDelete,
        onCancelDelete = viewModel::cancelDelete,
        onNoticeConsumed = viewModel::consumeNotice,
        modifier = modifier,
    )
}
