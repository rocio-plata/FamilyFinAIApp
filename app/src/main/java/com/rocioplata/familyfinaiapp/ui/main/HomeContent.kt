package com.rocioplata.familyfinaiapp.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rocioplata.familyfinaiapp.R
import com.rocioplata.familyfinaiapp.ui.dashboard.DEFAULT_BOTTOM_SPACE
import com.rocioplata.familyfinaiapp.ui.theme.FamilyFinAIAppTheme

/**
 * Cuerpo de Inicio (RF-01 y RF-12 de la 008): el dashboard de la familia activa, en [dashboardContent], que incluye las
 * secciones de categorías y de medios de pago con su «Gestionar». Todo en un contenedor que se desplaza, con espacio
 * al final para que el botón «+» no tape la última fila. Sin [dashboardContent] (no hay familia activa que mostrar)
 * solo queda la fila «Medios de pago» (RF-01 de 006) con su enlace «Gestionar», que no depende de ella; un callback
 * nulo oculta su sección.
 */
@Composable
fun HomeContent(
    modifier: Modifier = Modifier,
    onManagePaymentMethods: (() -> Unit)? = null,
    dashboardContent: (@Composable (Modifier) -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("home_content"),
    ) {
        dashboardContent?.invoke(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp))
        // Con dashboard, «Medios de pago» es una sección suya (RF-16 a RF-18); sin él queda solo el título con «Gestionar».
        if (onManagePaymentMethods != null && dashboardContent == null) {
            ManageSectionRow(
                title = stringResource(R.string.payment_methods_home_title),
                manageDescription = stringResource(R.string.payment_methods_home_manage_description),
                onManage = onManagePaymentMethods,
                manageTag = "home_manage_payment_methods",
            )
        }
        Spacer(modifier = Modifier.height(DEFAULT_BOTTOM_SPACE))
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
            color = MaterialTheme.colorScheme.onBackground,
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
fun HomeFab(modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    FloatingActionButton(onClick = onClick, modifier = modifier) {
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
        HomeContent(onManagePaymentMethods = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeFabPreview() {
    FamilyFinAIAppTheme { HomeFab() }
}
