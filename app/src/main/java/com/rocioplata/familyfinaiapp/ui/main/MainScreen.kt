package com.rocioplata.familyfinaiapp.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.rocioplata.familyfinaiapp.ui.theme.FamilyFinAIAppTheme

// La pestaña seleccionada vive solo en memoria: se conserva al pasar a segundo
// plano y se reinicia a Inicio al recrear la Activity (RF-08).
@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    var selected by remember { mutableStateOf(MainDestination.Home) }
    BackHandler(enabled = selected != MainDestination.Home) {
        selected = MainDestination.Home
    }
    MainScreenContent(
        selected = selected,
        onSelect = { destination ->
            if (destination != selected) selected = destination
        },
        modifier = modifier,
    )
}

@Composable
fun MainScreenContent(
    selected: MainDestination,
    onSelect: (MainDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            MainHeader(
                modifier = Modifier.windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                    ),
                ),
            )
        },
        bottomBar = { MainBottomBar(selected = selected, onSelect = onSelect) },
        floatingActionButton = {
            if (selected == MainDestination.Home) HomeFab()
        },
    ) { innerPadding ->
        val contentModifier = Modifier.padding(innerPadding)
        if (selected == MainDestination.Home) {
            HomeContent(modifier = contentModifier)
        } else {
            TabPlaceholder(destination = selected, modifier = contentModifier)
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun MainScreenPreview() {
    FamilyFinAIAppTheme { MainScreen() }
}
