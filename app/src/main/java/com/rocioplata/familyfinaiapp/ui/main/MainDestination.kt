package com.rocioplata.familyfinaiapp.ui.main

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.ui.graphics.vector.ImageVector
import com.rocioplata.familyfinaiapp.R

// El orden de las entradas es el orden de las pestañas en la barra inferior.
enum class MainDestination(
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    Home(R.string.tab_home, Icons.Outlined.Home),
    Movements(R.string.tab_movements, Icons.Outlined.Receipt),
    Reports(R.string.tab_reports, Icons.Outlined.PieChart),
    Budgets(R.string.tab_budgets, Icons.Outlined.AccountBalanceWallet),
    Assistant(R.string.tab_assistant, Icons.Outlined.AutoAwesome),
}
