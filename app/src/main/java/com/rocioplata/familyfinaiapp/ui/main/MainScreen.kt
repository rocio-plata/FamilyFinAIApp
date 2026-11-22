package com.rocioplata.familyfinaiapp.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.rocioplata.familyfinaiapp.FamilyFinApp
import com.rocioplata.familyfinaiapp.domain.header.FamilyRole
import com.rocioplata.familyfinaiapp.domain.categories.CategoryType
import com.rocioplata.familyfinaiapp.domain.movements.MovementType
import com.rocioplata.familyfinaiapp.ui.categories.CategoriesScreen
import com.rocioplata.familyfinaiapp.ui.categories.CategoryEditScreen
import com.rocioplata.familyfinaiapp.ui.dashboard.DashboardScreen
import com.rocioplata.familyfinaiapp.ui.movements.MovementDetailScreen
import com.rocioplata.familyfinaiapp.ui.movements.MovementFormScreen
import com.rocioplata.familyfinaiapp.ui.movements.MovementsScreen
import com.rocioplata.familyfinaiapp.ui.paymentmethods.PaymentMethodsScreen
import com.rocioplata.familyfinaiapp.domain.auth.SessionState
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rocioplata.familyfinaiapp.ui.theme.FamilyFinAIAppTheme

@Composable
fun MainScreen(modifier: Modifier = Modifier, viewModelKey: String? = null) {
    val container = (LocalContext.current.applicationContext as FamilyFinApp).container
    val viewModel: MainViewModel = viewModel(
        key = viewModelKey,
        factory = viewModelFactory {
            initializer { MainViewModel(container.headerRepository, container.connectivityObserver) }
        },
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val session by container.sessionManager.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onStart() }

    MainScreen(
        uiState = uiState,
        onFamilyClick = viewModel::openSelector,
        onRetry = viewModel::retry,
        onSelectFamily = viewModel::selectFamily,
        onDismissSelector = viewModel::dismissSelector,
        onSelectTab = viewModel::selectTab,
        onManagePaymentMethods = viewModel::openPaymentMethods,
        onAddMovement = { viewModel.openMovementCreate() },
        homeDashboardContent = { contentModifier ->
            val family = (uiState.families as? FamiliesState.Content)?.activeFamily
            if (family != null) {
                // La instancia de la familia activa va en la clave: cada cambio de familia, también volver a una anterior,
                // empieza con un dashboard nuevo (mes en curso y gastos); sin cambio, el mes y el tipo se conservan.
                DashboardScreen(
                    familyId = family.id,
                    currency = family.currency,
                    viewModelKey = "${viewModelKey ?: "main"}-dashboard-${family.id}-${uiState.activeFamilyInstance}",
                    onCategoryClick = { categoryId, type -> viewModel.openMovementCreate(categoryId, type) },
                    onManageCategories = viewModel::openCategories,
                    onManagePaymentMethods = viewModel::openPaymentMethods,
                    onInsufficientRole = viewModel::onInsufficientRole,
                    // El espacio para el botón «+» lo deja Inicio al final.
                    bottomSpace = 16.dp,
                    modifier = contentModifier,
                )
            }
        },
        movementsContent = { contentModifier ->
            val family = (uiState.families as? FamiliesState.Content)?.activeFamily
            if (family == null) {
                TabPlaceholder(MainDestination.Movements, modifier = contentModifier)
            } else {
                MovementsScreen(
                    familyId = family.id,
                    currency = family.currency,
                    currentUserId = (session as? SessionState.SignedIn)?.userId,
                    viewModelKey = "${viewModelKey ?: "main"}-movements-${family.id}",
                    onMovementClick = viewModel::openMovementDetail,
                    onInsufficientRole = viewModel::onInsufficientRole,
                    modifier = contentModifier,
                )
            }
        },
        onManagementBack = viewModel::managementBack,
        managementContent = { route ->
            val family = (uiState.families as? FamiliesState.Content)?.activeFamily
            val key = "${viewModelKey ?: "main"}-categories-${family?.id}"
            val canManage = family?.role == FamilyRole.OWNER
            when {
                family == null -> ManagementPlaceholder(route, onBack = viewModel::managementBack, modifier = modifier)
                route is MovementRoute.Create -> MovementFormScreen(
                    familyId = family.id,
                    familyName = family.name,
                    currency = family.currency,
                    initialType = route.type ?: MovementType.EXPENSE,
                    initialCategoryId = route.categoryId,
                    viewModelKey = "${viewModelKey ?: "main"}-movement-form-${family.id}-${uiState.managementRouteInstance}",
                    onClose = viewModel::managementBack,
                    onInsufficientRole = viewModel::onInsufficientRole,
                    onCurrencyMismatch = viewModel::onInsufficientRole,
                    modifier = modifier,
                )
                route is MovementRoute.Detail -> MovementDetailScreen(
                    familyId = family.id,
                    movementId = route.movementId,
                    currentUserId = (session as? SessionState.SignedIn)?.userId,
                    viewModelKey = "${viewModelKey ?: "main"}-movement-detail-${family.id}-${uiState.managementRouteInstance}",
                    onClose = viewModel::managementBack,
                    onEdit = { viewModel.openMovementEdit(route.movementId) },
                    onInsufficientRole = viewModel::onInsufficientRole,
                    modifier = modifier,
                )
                route is MovementRoute.Edit -> MovementFormScreen(
                    familyId = family.id,
                    familyName = family.name,
                    currency = family.currency,
                    viewModelKey = "${viewModelKey ?: "main"}-movement-form-${family.id}-${uiState.managementRouteInstance}",
                    onClose = viewModel::managementBack,
                    onInsufficientRole = viewModel::onInsufficientRole,
                    onCurrencyMismatch = viewModel::onInsufficientRole,
                    movementId = route.movementId,
                    onMovedToAnotherMonth = viewModel::closeManagement,
                    modifier = modifier,
                )
                route is MovementRoute -> MovementRoutePlaceholder(route, onBack = viewModel::managementBack, modifier = modifier)
                route == PaymentMethodsRoute -> PaymentMethodsScreen(
                    familyId = family.id,
                    viewModelKey = "${viewModelKey ?: "main"}-payment-methods-${family.id}",
                    onBack = viewModel::managementBack,
                    onInsufficientRole = viewModel::onInsufficientRole,
                    modifier = modifier,
                )
                route == CategoriesRoute.List -> CategoriesScreen(
                    familyId = family.id,
                    canManage = canManage,
                    viewModelKey = key,
                    onBack = viewModel::managementBack,
                    onCategoryClick = viewModel::openCategoryEdit,
                    onCreateClick = viewModel::openCategoryCreate,
                    onInsufficientRole = viewModel::onInsufficientRole,
                    modifier = modifier,
                )
                else -> CategoryEditScreen(
                    familyId = family.id,
                    categoryId = (route as? CategoriesRoute.Edit)?.categoryId,
                    initialType = (route as? CategoriesRoute.Create)?.type ?: CategoryType.EXPENSE,
                    canManage = canManage,
                    viewModelKey = key,
                    onBack = viewModel::managementBack,
                    onCreated = viewModel::openCategoryEdit,
                    onInsufficientRole = viewModel::onInsufficientRole,
                    modifier = modifier,
                )
            }
        },
        modifier = modifier,
    )
}

// La pestaña seleccionada está en `uiState` (la guarda el ViewModel): se conserva al recrear la pantalla y
// al pasar a segundo plano, y se reinicia a Inicio al terminar el proceso (RF-08 de la 001).
@Composable
fun MainScreen(
    uiState: MainUiState,
    onFamilyClick: () -> Unit,
    onRetry: () -> Unit,
    onSelectFamily: (String) -> Unit,
    onDismissSelector: () -> Unit,
    modifier: Modifier = Modifier,
    onSelectTab: (MainDestination) -> Unit = {},
    onManagePaymentMethods: () -> Unit = {},
    onAddMovement: () -> Unit = {},
    onManagementBack: () -> Unit = {},
    managementContent: (@Composable (ManagementRoute) -> Unit)? = null,
    movementsContent: (@Composable (Modifier) -> Unit)? = null,
    homeDashboardContent: (@Composable (Modifier) -> Unit)? = null,
) {
    val selected = uiState.selectedTab
    BackHandler(enabled = selected != MainDestination.Home && uiState.managementRoute == null) {
        onSelectTab(MainDestination.Home)
    }
    // Una gestión ocupa toda la pantalla: sin cabecera de familia ni barra de pestañas.
    val route = uiState.managementRoute
    if (route != null) {
        BackHandler(onBack = onManagementBack)
        if (managementContent != null) {
            managementContent(route)
        } else {
            ManagementPlaceholder(route, onBack = onManagementBack, modifier = modifier)
        }
        return
    }
    MainScreenContent(
        selected = selected,
        onSelect = { destination ->
            if (destination != selected) onSelectTab(destination)
        },
        uiState = uiState,
        onFamilyClick = onFamilyClick,
        onRetry = onRetry,
        onSelectFamily = onSelectFamily,
        onDismissSelector = onDismissSelector,
        onManagePaymentMethods = onManagePaymentMethods,
        onAddMovement = onAddMovement,
        movementsContent = movementsContent,
        homeDashboardContent = homeDashboardContent,
        modifier = modifier,
    )
}

/** Pantalla provisional de la gestión abierta cuando no hay contenido real (sin familia o en tests). */
@Composable
private fun ManagementPlaceholder(route: ManagementRoute, onBack: () -> Unit, modifier: Modifier = Modifier) {
    when (route) {
        PaymentMethodsRoute -> PaymentMethodsPlaceholder(onBack = onBack, modifier = modifier)
        is CategoriesRoute -> CategoriesPlaceholder(onBack = onBack, modifier = modifier)
        is MovementRoute -> MovementRoutePlaceholder(route, onBack = onBack, modifier = modifier)
    }
}

@Composable
fun MainScreenContent(
    selected: MainDestination,
    onSelect: (MainDestination) -> Unit,
    modifier: Modifier = Modifier,
    uiState: MainUiState = MainUiState(),
    onFamilyClick: () -> Unit = {},
    onRetry: () -> Unit = {},
    onSelectFamily: (String) -> Unit = {},
    onDismissSelector: () -> Unit = {},
    onManagePaymentMethods: () -> Unit = {},
    onAddMovement: () -> Unit = {},
    movementsContent: (@Composable (Modifier) -> Unit)? = null,
    homeDashboardContent: (@Composable (Modifier) -> Unit)? = null,
) {
    val content = uiState.families as? FamiliesState.Content
    Scaffold(
        modifier = modifier,
        topBar = {
            Column {
                MainHeader(
                    state = uiState,
                    onFamilyClick = onFamilyClick,
                    onRetry = onRetry,
                    modifier = Modifier.windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                        ),
                    ),
                )
                if (content?.isStale == true) StaleDataBanner()
            }
        },
        bottomBar = { MainBottomBar(selected = selected, onSelect = onSelect) },
        floatingActionButton = {
            if (selected == MainDestination.Home || selected == MainDestination.Movements) {
                HomeFab(onClick = onAddMovement)
            }
        },
    ) { innerPadding ->
        val contentModifier = Modifier.padding(innerPadding)
        // key(familyId) recrea el cuerpo al cambiar de familia para que recargue sus datos.
        key(content?.activeFamily?.id) {
            val familyName = content?.activeFamily?.name
            if (selected == MainDestination.Home) {
                HomeContent(
                    modifier = contentModifier,
                    onManagePaymentMethods = onManagePaymentMethods.takeIf { content != null },
                    dashboardContent = homeDashboardContent.takeIf { content != null },
                )
            } else if (selected == MainDestination.Movements && movementsContent != null) {
                movementsContent(contentModifier)
            } else {
                TabPlaceholder(destination = selected, modifier = contentModifier, familyName = familyName)
            }
        }
    }
    if (content != null && uiState.selectorOpen) {
        FamilySelectorSheet(
            families = content.families,
            activeFamilyId = content.activeFamily.id,
            onSelect = onSelectFamily,
            onDismiss = onDismissSelector,
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun MainScreenPreview() {
    FamilyFinAIAppTheme { MainScreenContent(selected = MainDestination.Home, onSelect = {}) }
}
