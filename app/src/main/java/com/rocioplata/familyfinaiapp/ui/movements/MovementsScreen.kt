package com.rocioplata.familyfinaiapp.ui.movements

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
 * Entrada con estado de la pestaña "Movimientos" de [familyId]. [viewModelKey] debe incluir la sesión y la
 * familia (`main-<generación>-movements-<familia>`): así cambiar de familia o de usuario nunca reutiliza el
 * mes ni el filtro de otro listado. [currency] es la de la familia (puede faltar en una copia antigua).
 */
@Composable
fun MovementsScreen(
    familyId: String,
    currency: String?,
    currentUserId: String?,
    viewModelKey: String,
    onMovementClick: (String) -> Unit,
    onInsufficientRole: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = (LocalContext.current.applicationContext as FamilyFinApp).container
    val viewModel: MovementsViewModel = viewModel(
        key = viewModelKey,
        factory = viewModelFactory {
            initializer {
                MovementsViewModel(
                    movements = container.movementsRepository,
                    categories = container.categoriesRepository,
                    paymentMethods = container.paymentMethodsRepository,
                    members = container.membersRepository,
                    familyId = familyId,
                    currency = currency,
                    currentUserId = currentUserId,
                    dates = container.movementDates,
                    connectivity = container.connectivityObserver,
                    onInsufficientRole = onInsufficientRole,
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onStart() }
    // Al volver de otra pestaña o pantalla el listado ya se había compuesto antes: se actualiza igual (se ignora si ya hay una carga).
    LaunchedEffect(Unit) { viewModel.onStart() }

    MovementsContent(
        state = state,
        onPreviousMonth = viewModel::previousMonth,
        onNextMonth = viewModel::nextMonth,
        onFilterSelected = viewModel::selectFilter,
        onRetry = viewModel::retry,
        onMovementClick = onMovementClick,
        modifier = modifier,
    )
}
