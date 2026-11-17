package com.rocioplata.familyfinaiapp.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.rocioplata.familyfinaiapp.R
import com.rocioplata.familyfinaiapp.ui.theme.FamilyFinAIAppTheme

/**
 * Pantalla completa provisional de una [MovementRoute]: solo la barra con la flecha y el título.
 * La sustituyen el detalle (T-18) y el formulario (T-16).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovementRoutePlaceholder(route: MovementRoute, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val (tag, title) = when (route) {
        MovementRoute.Create -> "movement_create_screen" to R.string.movement_create_title
        is MovementRoute.Detail -> "movement_detail_screen" to R.string.movement_detail_title
        is MovementRoute.Edit -> "movement_edit_screen" to R.string.movement_edit_title
    }
    Scaffold(
        modifier = modifier.testTag(tag),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("movement_back")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.movement_back_description),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding))
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun MovementRoutePlaceholderPreview() {
    FamilyFinAIAppTheme { MovementRoutePlaceholder(MovementRoute.Create, onBack = {}) }
}
