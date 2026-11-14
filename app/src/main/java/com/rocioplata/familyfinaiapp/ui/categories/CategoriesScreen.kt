package com.rocioplata.familyfinaiapp.ui.categories

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
import com.rocioplata.familyfinaiapp.domain.categories.CategoryType

/**
 * Entrada con estado del listado de categorías de [familyId]. [viewModelKey] debe incluir la sesión y
 * la familia (`main-<generación>-categories-<familia>`): así cambiar de familia o de usuario nunca
 * reutiliza el estado de otro listado.
 */
@Composable
fun CategoriesScreen(
    familyId: String,
    canManage: Boolean,
    viewModelKey: String,
    onBack: () -> Unit,
    onCategoryClick: (String) -> Unit,
    onCreateClick: (CategoryType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = (LocalContext.current.applicationContext as FamilyFinApp).container
    val viewModel: CategoriesViewModel = viewModel(
        key = viewModelKey,
        factory = viewModelFactory {
            initializer {
                CategoriesViewModel(container.categoriesRepository, familyId, container.connectivityObserver)
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onStart() }
    // Al volver del detalle el listado ya se había compuesto antes: se actualiza igual (se ignora si ya hay una carga).
    LaunchedEffect(Unit) { viewModel.onStart() }

    CategoriesContent(
        state = state,
        canManage = canManage,
        onBack = onBack,
        onQueryChange = viewModel::setQuery,
        onSelectType = viewModel::selectType,
        onRetry = viewModel::retry,
        onCategoryClick = onCategoryClick,
        onCreateClick = onCreateClick,
        onToggleArchived = viewModel::toggleArchived,
        // La acción de eliminar una archivada se conecta en T-21 y T-22.
        onDeleteArchived = {},
        modifier = modifier,
    )
}
