package com.rocioplata.familyfinaiapp.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import com.rocioplata.familyfinaiapp.R
import com.rocioplata.familyfinaiapp.ui.theme.FamilyFinAIAppTheme

// Título de la sección seguido de la familia activa (RF-08); sin familia, solo el título.
@Composable
fun TabPlaceholder(
    destination: MainDestination,
    modifier: Modifier = Modifier,
    familyName: String? = null,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("tab_placeholder_${destination.name}"),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = tabTitle(destination, familyName),
            style = MaterialTheme.typography.titleLarge,
        )
    }
}

@Composable
internal fun tabTitle(destination: MainDestination, familyName: String?): String {
    val title = stringResource(destination.labelRes)
    return if (familyName == null) {
        title
    } else {
        stringResource(R.string.tab_content_title_with_family, title, familyName)
    }
}

@Preview(showBackground = true, name = "Movimientos")
@Composable
private fun MovementsPlaceholderPreview() {
    FamilyFinAIAppTheme { TabPlaceholder(MainDestination.Movements, familyName = "Familia Plata") }
}

@Preview(showBackground = true, name = "Reportes")
@Composable
private fun ReportsPlaceholderPreview() {
    FamilyFinAIAppTheme { TabPlaceholder(MainDestination.Reports) }
}

@Preview(showBackground = true, name = "Presupuestos")
@Composable
private fun BudgetsPlaceholderPreview() {
    FamilyFinAIAppTheme { TabPlaceholder(MainDestination.Budgets) }
}

@Preview(showBackground = true, name = "Asistente")
@Composable
private fun AssistantPlaceholderPreview() {
    FamilyFinAIAppTheme { TabPlaceholder(MainDestination.Assistant) }
}
