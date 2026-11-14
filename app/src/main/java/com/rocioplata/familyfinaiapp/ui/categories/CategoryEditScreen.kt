package com.rocioplata.familyfinaiapp.ui.categories

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.rocioplata.familyfinaiapp.FamilyFinApp
import com.rocioplata.familyfinaiapp.R
import com.rocioplata.familyfinaiapp.domain.categories.CategoryFailure
import com.rocioplata.familyfinaiapp.domain.categories.CategoryType
import java.util.UUID

/**
 * Entrada con estado del alta ([categoryId] nulo) y la edición de una categoría. Cada vez que se abre la
 * pantalla se crea un ViewModel nuevo (la clave lleva un identificador propio de esa apertura), de modo
 * que un borrador descartado no reaparece al volver a abrirla (RF-09, CA-24). [viewModelKey] debe incluir
 * la sesión y la familia.
 */
@Composable
fun CategoryEditScreen(
    familyId: String,
    categoryId: String?,
    initialType: CategoryType,
    canManage: Boolean,
    viewModelKey: String,
    onBack: () -> Unit,
    onCreated: (String) -> Unit,
    onInsufficientRole: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = (LocalContext.current.applicationContext as FamilyFinApp).container
    // Sobrevive a la recreación de la pantalla (y con ella el ViewModel), pero no a salir y volver a entrar.
    val opening = rememberSaveable { UUID.randomUUID().toString() }
    val viewModel: CategoryEditViewModel = viewModel(
        key = "$viewModelKey-${categoryId ?: "new"}-$opening",
        factory = viewModelFactory {
            initializer {
                CategoryEditViewModel(
                    repository = container.categoriesRepository,
                    familyId = familyId,
                    categoryId = categoryId,
                    initialType = initialType,
                    initialCanManage = canManage,
                    onInsufficientRole = onInsufficientRole,
                )
            }
        },
    )
    LaunchedEffect(canManage) { viewModel.setCanManage(canManage) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when (val current = state) {
        CategoryEditUiState.Loading -> LoadingScreen(onBack, modifier)
        CategoryEditUiState.NotFound -> {
            LoadingScreen(onBack, modifier)
            GoneDialog(messageRes = CategoryFailure.NotFound.messageRes(), onDismiss = onBack)
        }
        is CategoryEditUiState.Form -> CategoryEditContent(
            state = current,
            onLeave = onBack,
            onNameChange = viewModel::setName,
            onIconChange = viewModel::setIcon,
            onTypeChange = viewModel::setType,
            onSubmit = viewModel::submit,
            onEventConsumed = viewModel::consumeEvent,
            onCreated = onCreated,
            modifier = modifier,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoadingScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier.testTag("category_edit_loading"),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.categories_edit_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.categories_back_description),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}
