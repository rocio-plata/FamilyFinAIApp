package com.rocioplata.familyfinaiapp.ui.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.rocioplata.familyfinaiapp.FamilyFinApp
import com.rocioplata.familyfinaiapp.domain.movements.MovementType

/**
 * Entrada con estado del dashboard de Inicio de [familyId] (RF-01 a RF-11): crea su ViewModel y vuelve a actualizar los
 * datos al volver a primer plano. [viewModelKey] debe incluir la sesión, la familia y las veces que cambió la familia
 * activa (`main-<generación>-dashboard-<familia>-<instancia>`): así el mes y el tipo se conservan al girar la pantalla,
 * al pasar a segundo plano y al cambiar de pestaña, y cambiar de familia o de usuario empieza en el mes en curso con
 * gastos (CA-17, CA-18).
 */
@Composable
fun DashboardScreen(
    familyId: String,
    currency: String?,
    viewModelKey: String,
    onCategoryClick: (categoryId: String, type: MovementType) -> Unit,
    onManageCategories: () -> Unit,
    onInsufficientRole: () -> Unit,
    modifier: Modifier = Modifier,
    bottomSpace: Dp = DEFAULT_BOTTOM_SPACE,
) {
    val container = (LocalContext.current.applicationContext as FamilyFinApp).container
    val viewModel: DashboardViewModel = viewModel(
        key = viewModelKey,
        factory = viewModelFactory {
            initializer {
                DashboardViewModel(
                    movements = container.movementsRepository,
                    categories = container.categoriesRepository,
                    paymentMethods = container.paymentMethodsRepository,
                    familyId = familyId,
                    currency = currency,
                    dates = container.movementDates,
                    connectivity = container.connectivityObserver,
                    onInsufficientRole = onInsufficientRole,
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onStart() }

    DashboardContent(
        state = state,
        onPreviousMonth = viewModel::previousMonth,
        onNextMonth = viewModel::nextMonth,
        onCurrentMonth = viewModel::goToCurrentMonth,
        onSelectType = viewModel::selectType,
        onCategoryClick = onCategoryClick,
        onManageCategories = onManageCategories,
        onRetry = viewModel::retry,
        bottomSpace = bottomSpace,
        modifier = modifier,
    )
}
