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
 * Pantalla completa de la gestión de categorías (RF-01), sin cabecera de familia ni barra de pestañas.
 * Provisional: solo la barra con la flecha y el título; el listado real lo sustituye en T-16/T-17.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesPlaceholder(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier.testTag("categories_screen"),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.categories_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("categories_back")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.categories_back_description),
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
private fun CategoriesPlaceholderPreview() {
    FamilyFinAIAppTheme { CategoriesPlaceholder(onBack = {}) }
}
