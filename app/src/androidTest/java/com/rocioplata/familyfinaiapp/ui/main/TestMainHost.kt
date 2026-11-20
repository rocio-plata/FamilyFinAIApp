package com.rocioplata.familyfinaiapp.ui.main

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.rocioplata.familyfinaiapp.ui.dashboard.DashboardContent
import com.rocioplata.familyfinaiapp.domain.categories.Category
import com.rocioplata.familyfinaiapp.domain.categories.CategoryStatus
import com.rocioplata.familyfinaiapp.domain.categories.CategoryType
import com.rocioplata.familyfinaiapp.domain.dashboard.CategoryAmount
import com.rocioplata.familyfinaiapp.domain.dashboard.CategoryBreakdown
import com.rocioplata.familyfinaiapp.domain.dashboard.ChartSegment
import com.rocioplata.familyfinaiapp.domain.dashboard.MonthTotals
import com.rocioplata.familyfinaiapp.domain.header.Family
import com.rocioplata.familyfinaiapp.domain.money.Money
import com.rocioplata.familyfinaiapp.domain.movements.MovementType
import com.rocioplata.familyfinaiapp.ui.dashboard.DashboardContentState
import com.rocioplata.familyfinaiapp.ui.dashboard.DashboardUiState
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.YearMonth
import com.rocioplata.familyfinaiapp.domain.header.FamilyRole

/** Un dashboard con un par de categorías, para probar Inicio sin ViewModel ni red. */
fun sampleDashboardState(): DashboardUiState {
    val totals = MonthTotals(Money(1_850_000, "CLP"), Money(571_500, "CLP"))
    val food = CategoryAmount(
        Category("c-food", CategoryType.EXPENSE, "Comestibles", "shopping_cart", CategoryStatus.ACTIVE, emptyList(), "sage"),
        Money(425_800, "CLP"), 0,
    )
    val health = CategoryAmount(
        Category("c-health", CategoryType.EXPENSE, "Salud", "medical_services", CategoryStatus.ACTIVE, emptyList(), "coral"),
        Money(145_700, "CLP"), 1,
    )
    val segments = listOf(food, health).map { ChartSegment(it, BigDecimal(it.amount.amountMinor).divide(BigDecimal(571_500), 10, RoundingMode.HALF_UP)) }
    return DashboardUiState(
        month = YearMonth.of(2026, 10), canGoNext = true, isCurrentMonth = false, activeType = MovementType.EXPENSE,
        content = DashboardContentState.Content(
            totals = totals, percentOfIncome = totals.percentOfIncome(MovementType.EXPENSE),
            breakdown = CategoryBreakdown(totals.expenses, listOf(food, health), segments), comparison = null,
            isMonthEmpty = false, isStale = false, isRefreshing = false,
        ),
    )
}

val OwnerFamily = Family("f1", "Familia Plata", FamilyRole.OWNER)
val MemberFamily = Family("f2", "Familia Gómez", FamilyRole.MEMBER)

fun contentState(
    families: List<Family> = listOf(OwnerFamily, MemberFamily),
    active: Family = families.first(),
    isStale: Boolean = false,
) = FamiliesState.Content(families, active, isStale)

/**
 * Reproduce lo mínimo del ViewModel (selector, familia activa, pestaña y ruta de gestión) sin red ni caché.
 * La pestaña se guarda con `rememberSaveable` para emular que el ViewModel sobrevive a recrear la pantalla.
 */
@Composable
fun TestMainHost(
    initial: MainUiState,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
) {
    var state by remember { mutableStateOf(initial) }
    var tab by rememberSaveable { mutableStateOf(initial.selectedTab) }
    // Misma regla que MainViewModel.managementBack para las rutas de movimientos: la edición vuelve al detalle.
    val back = {
        val route = state.managementRoute
        state = state.copy(managementRoute = if (route is MovementRoute.Edit) MovementRoute.Detail(route.movementId) else null)
    }
    MainScreen(
        uiState = state.copy(selectedTab = tab),
        onSelectTab = { tab = it },
        onFamilyClick = { state = state.copy(selectorOpen = true) },
        onRetry = onRetry,
        onSelectFamily = { id ->
            val content = state.families as FamiliesState.Content
            // Igual que `MainViewModel`: cada cambio de familia activa suma una instancia.
            val changed = content.activeFamily.id != id
            state = state.copy(
                families = content.copy(activeFamily = content.families.first { it.id == id }),
                selectorOpen = false,
                activeFamilyInstance = state.activeFamilyInstance + if (changed) 1 else 0,
            )
        },
        onDismissSelector = { state = state.copy(selectorOpen = false) },
        homeDashboardContent = { contentModifier ->
            val family = (state.families as? FamiliesState.Content)?.activeFamily
            if (family != null) {
                // Una instancia nueva del dashboard por cada familia activa, como la clave del ViewModel real.
                key(family.id, state.activeFamilyInstance) {
                    Column(modifier = contentModifier) {
                        Text(family.name, modifier = Modifier.testTag("test_dashboard_family"))
                        DashboardContent(
                            state = sampleDashboardState(),
                            onPreviousMonth = {},
                            onNextMonth = {},
                            onCurrentMonth = {},
                            onSelectType = {},
                            onCategoryClick = { id, type -> state = state.copy(managementRoute = MovementRoute.Create(id, type)) },
                            onManageCategories = { state = state.copy(managementRoute = CategoriesRoute.List) },
                            bottomSpace = 16.dp,
                        )
                    }
                }
            }
        },
        onManagePaymentMethods = { state = state.copy(managementRoute = PaymentMethodsRoute) },
        onAddMovement = { state = state.copy(managementRoute = MovementRoute.Create()) },
        onManagementBack = back,
        managementContent = { route ->
            when (route) {
                // Un detalle mínimo con "Editar", para probar la navegación detalle → edición → atrás.
                is MovementRoute.Detail -> Column(modifier = Modifier.testTag("test_detail_screen")) {
                    Text("Detalle ${route.movementId}")
                    Button(
                        onClick = { state = state.copy(managementRoute = MovementRoute.Edit(route.movementId)) },
                        modifier = Modifier.testTag("test_detail_edit"),
                    ) { Text("Editar") }
                }
                // El resto de las rutas, con las pantallas provisionales de siempre.
                PaymentMethodsRoute -> PaymentMethodsPlaceholder(onBack = back)
                is CategoriesRoute -> CategoriesPlaceholder(onBack = back)
                is MovementRoute -> MovementRoutePlaceholder(route, onBack = back)
            }
        },
        modifier = modifier,
    )
}
