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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.rocioplata.familyfinaiapp.R
import com.rocioplata.familyfinaiapp.ui.theme.FamilyFinAIAppTheme

/**
 * Cuerpo de Inicio: la sección «Medios de pago» (RF-01 de 006) justo encima de «Categorías» (RF-01 de 005),
 * cada una con su enlace «Gestionar» a la derecha, y el texto «Inicio · Familia» (RF-08 de 002), sin
 * cuadrícula ni importes. Un callback nulo oculta su sección (no hay familia activa que gestionar).
 */
@Composable
fun HomeContent(
    modifier: Modifier = Modifier,
    familyName: String? = null,
    onManageCategories: (() -> Unit)? = null,
    onManagePaymentMethods: (() -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxSize().testTag("home_content")) {
        if (onManagePaymentMethods != null) {
            ManageSectionRow(
                title = stringResource(R.string.payment_methods_home_title),
                manageDescription = stringResource(R.string.payment_methods_home_manage_description),
                onManage = onManagePaymentMethods,
                manageTag = "home_manage_payment_methods",
            )
        }
        if (onManageCategories != null) {
            ManageSectionRow(
                title = stringResource(R.string.categories_home_title),
                manageDescription = stringResource(R.string.categories_home_manage_description),
                onManage = onManageCategories,
                manageTag = "home_manage_categories",
            )
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                text = tabTitle(MainDestination.Home, familyName),
                style = MaterialTheme.typography.titleLarge,
            )
        }
    }
}

/** Título de sección con «Gestionar» a su derecha; la descripción distingue los enlaces para el lector de pantalla. */
@Composable
private fun ManageSectionRow(
    title: String,
    manageDescription: String,
    onManage: () -> Unit,
    manageTag: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        TextButton(
            onClick = onManage,
            modifier = Modifier
                .testTag(manageTag)
                .semantics { contentDescription = manageDescription },
        ) {
            Text(stringResource(R.string.categories_home_manage))
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
    FamilyFinAIAppTheme {
        HomeContent(familyName = "Familia Plata", onManageCategories = {}, onManagePaymentMethods = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeFabPreview() {
    FamilyFinAIAppTheme { HomeFab() }
}
