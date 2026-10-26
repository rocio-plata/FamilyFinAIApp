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
import com.rocioplata.familyfinaiapp.ui.theme.FamilyFinAIAppTheme

// Muestra solo el nombre de la sección (RF-06).
@Composable
fun TabPlaceholder(destination: MainDestination, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("tab_placeholder_${destination.name}"),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(destination.labelRes),
            style = MaterialTheme.typography.titleLarge,
        )
    }
}

@Preview(showBackground = true, name = "Movimientos")
@Composable
private fun MovementsPlaceholderPreview() {
    FamilyFinAIAppTheme { TabPlaceholder(MainDestination.Movements) }
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
