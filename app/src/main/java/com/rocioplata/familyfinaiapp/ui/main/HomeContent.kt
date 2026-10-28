package com.rocioplata.familyfinaiapp.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.rocioplata.familyfinaiapp.R
import com.rocioplata.familyfinaiapp.ui.theme.FamilyFinAIAppTheme

// Cuerpo placeholder de Inicio: «Inicio · Familia» (RF-08), sin otros datos ni secciones.
@Composable
fun HomeContent(modifier: Modifier = Modifier, familyName: String? = null) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("home_content"),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = tabTitle(MainDestination.Home, familyName),
            style = MaterialTheme.typography.titleLarge,
        )
    }
}

// FAB sin acción (RF-04); el contenedor raíz decide cuándo mostrarlo.
@Composable
fun HomeFab(modifier: Modifier = Modifier) {
    FloatingActionButton(onClick = {}, modifier = modifier) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = stringResource(R.string.fab_add_description),
        )
    }
}

@Preview(showBackground = true, name = "Inicio")
@Composable
private fun HomeContentPreview() {
    FamilyFinAIAppTheme { HomeContent(familyName = "Familia Plata") }
}

@Preview(showBackground = true)
@Composable
private fun HomeFabPreview() {
    FamilyFinAIAppTheme { HomeFab() }
}
