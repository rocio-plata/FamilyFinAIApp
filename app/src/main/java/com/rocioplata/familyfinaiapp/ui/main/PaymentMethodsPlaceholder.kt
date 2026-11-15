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
 * Pantalla de respaldo de la gestión de medios de pago: solo la barra con la flecha y el título. La usa
 * la sobrecarga sin estado de `MainScreen` cuando no recibe el contenido real (sin familia activa y en los
 * tests de UI), igual que `CategoriesPlaceholder`; el listado real es `PaymentMethodsScreen` (RF-01 de 006).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentMethodsPlaceholder(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier.testTag("payment_methods_screen"),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.payment_methods_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("payment_methods_back")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.payment_methods_back_description),
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
private fun PaymentMethodsPlaceholderPreview() {
    FamilyFinAIAppTheme { PaymentMethodsPlaceholder(onBack = {}) }
}
