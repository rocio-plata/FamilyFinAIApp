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
import androidx.compose.ui.tooling.preview.Preview
import com.rocioplata.familyfinaiapp.ui.theme.FamilyFinAIAppTheme

@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    val container = (LocalContext.current.applicationContext as FamilyFinApp).container
    val viewModel: MainViewModel = viewModel(
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
) {
    var selected by remember { mutableStateOf(MainDestination.Home) }
    BackHandler(enabled = selected != MainDestination.Home) {
        selected = MainDestination.Home
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
                HomeContent(modifier = contentModifier, familyName = familyName)
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
