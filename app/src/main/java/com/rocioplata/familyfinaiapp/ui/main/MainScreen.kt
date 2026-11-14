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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.rocioplata.familyfinaiapp.ui.categories.CategoriesScreen
import com.rocioplata.familyfinaiapp.ui.categories.CategoryEditScreen
import androidx.compose.ui.tooling.preview.Preview
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
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onStart() }

    MainScreen(
        uiState = uiState,
        onFamilyClick = viewModel::openSelector,
        onRetry = viewModel::retry,
        onSelectFamily = viewModel::selectFamily,
        onDismissSelector = viewModel::dismissSelector,
        onManageCategories = viewModel::openCategories,
        onCategoriesBack = viewModel::categoriesBack,
        categoriesContent = { route ->
            val family = (uiState.families as? FamiliesState.Content)?.activeFamily
            val key = "${viewModelKey ?: "main"}-categories-${family?.id}"
            val canManage = family?.role == FamilyRole.OWNER
            when {
                family == null -> CategoriesPlaceholder(onBack = viewModel::categoriesBack, modifier = modifier)
                route == CategoriesRoute.List -> CategoriesScreen(
                    familyId = family.id,
                    canManage = canManage,
                    viewModelKey = key,
                    onBack = viewModel::categoriesBack,
                    onCategoryClick = viewModel::openCategoryEdit,
                    onCreateClick = viewModel::openCategoryCreate,
                    modifier = modifier,
                )
                else -> CategoryEditScreen(
                    familyId = family.id,
                    categoryId = (route as? CategoriesRoute.Edit)?.categoryId,
                    initialType = (route as? CategoriesRoute.Create)?.type ?: CategoryType.EXPENSE,
                    canManage = canManage,
                    viewModelKey = key,
                    onBack = viewModel::categoriesBack,
                    onCreated = viewModel::openCategoryEdit,
                    onInsufficientRole = viewModel::onInsufficientRole,
                    modifier = modifier,
                )
            }
        },
        modifier = modifier,
    )
}

// La pestaña seleccionada vive solo en memoria: se conserva al pasar a segundo
// plano y se reinicia a Inicio al recrear la Activity (RF-08).
@Composable
fun MainScreen(
    uiState: MainUiState,
    onFamilyClick: () -> Unit,
    onRetry: () -> Unit,
    onSelectFamily: (String) -> Unit,
    onDismissSelector: () -> Unit,
    modifier: Modifier = Modifier,
    onManageCategories: () -> Unit = {},
    onCategoriesBack: () -> Unit = {},
    categoriesContent: (@Composable (CategoriesRoute) -> Unit)? = null,
) {
    var selected by remember { mutableStateOf(MainDestination.Home) }
    BackHandler(enabled = selected != MainDestination.Home && uiState.categoriesRoute == null) {
        selected = MainDestination.Home
    }
    // La gestión de categorías ocupa toda la pantalla: sin cabecera de familia ni barra de pestañas.
    val route = uiState.categoriesRoute
    if (route != null) {
        BackHandler(onBack = onCategoriesBack)
        if (categoriesContent != null) {
            categoriesContent(route)
        } else {
            CategoriesPlaceholder(onBack = onCategoriesBack, modifier = modifier)
        }
        return
    }
    MainScreenContent(
        selected = selected,
        onSelect = { destination ->
            if (destination != selected) selected = destination
        },
        uiState = uiState,
        onFamilyClick = onFamilyClick,
        onRetry = onRetry,
        onSelectFamily = onSelectFamily,
        onDismissSelector = onDismissSelector,
        onManageCategories = onManageCategories,
        modifier = modifier,
    )
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
    onManageCategories: () -> Unit = {},
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
            if (selected == MainDestination.Home) HomeFab()
        },
    ) { innerPadding ->
        val contentModifier = Modifier.padding(innerPadding)
        // key(familyId) recrea el cuerpo al cambiar de familia para que recargue sus datos.
        key(content?.activeFamily?.id) {
            val familyName = content?.activeFamily?.name
            if (selected == MainDestination.Home) {
                HomeContent(
                    modifier = contentModifier,
                    familyName = familyName,
                    onManageCategories = onManageCategories.takeIf { content != null },
                )
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
