package com.rocioplata.familyfinaiapp.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.rocioplata.familyfinaiapp.R
import com.rocioplata.familyfinaiapp.ui.theme.FamilyFinAIAppTheme

/**
 * Cuerpo de Inicio: el título «Categorías» con el enlace «Gestionar» a su derecha (RF-01 de 005) y el
 * texto «Inicio · Familia» (RF-08 de 002), sin cuadrícula ni importes. [onManageCategories] nulo oculta
 * la sección (no hay familia activa a la que abrir las categorías).
 */
@Composable
fun HomeContent(
    modifier: Modifier = Modifier,
    familyName: String? = null,
    onManageCategories: (() -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxSize().testTag("home_content")) {
        if (onManageCategories != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.categories_home_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() },
                )
                TextButton(onClick = onManageCategories, modifier = Modifier.testTag("home_manage_categories")) {
                    Text(stringResource(R.string.categories_home_manage))
                }
            }
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                text = tabTitle(MainDestination.Home, familyName),
                style = MaterialTheme.typography.titleLarge,
            )
        }
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
    FamilyFinAIAppTheme { HomeContent(familyName = "Familia Plata", onManageCategories = {}) }
}

@Preview(showBackground = true)
@Composable
private fun HomeFabPreview() {
    FamilyFinAIAppTheme { HomeFab() }
}
