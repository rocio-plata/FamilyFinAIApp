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
 * Pantalla de respaldo de las gestiones de familias («Mi familia», crear familia e «Invitaciones»): solo la barra con la
 * flecha y el título. La usa la sobrecarga sin estado de `MainScreen` cuando no recibe el contenido real y los tests de
 * UI, igual que `PaymentMethodsPlaceholder`; las pantallas reales son `FamilyManagementScreen`, `FamilyCreateScreen` e
 * `InvitationsScreen` (009).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyRoutePlaceholder(route: ManagementRoute, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val (title, tag) = when (route) {
        FamilyRoute.Manage -> R.string.family_manage_title to "family_manage_screen"
        FamilyRoute.Create -> R.string.family_create_title to "family_create_screen"
        else -> R.string.invitations_title to "invitations_screen"
    }
    Scaffold(
        modifier = modifier.testTag(tag),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("family_route_back")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.family_back_description),
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
private fun FamilyRoutePlaceholderPreview() {
    FamilyFinAIAppTheme { FamilyRoutePlaceholder(FamilyRoute.Manage, onBack = {}) }
}
