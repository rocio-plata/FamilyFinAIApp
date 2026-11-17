package com.rocioplata.familyfinaiapp.ui.main

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.rocioplata.familyfinaiapp.domain.header.Family
import com.rocioplata.familyfinaiapp.domain.header.FamilyRole

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
            state = state.copy(
                families = content.copy(activeFamily = content.families.first { it.id == id }),
                selectorOpen = false,
            )
        },
        onDismissSelector = { state = state.copy(selectorOpen = false) },
        onManageCategories = { state = state.copy(managementRoute = CategoriesRoute.List) },
        onManagePaymentMethods = { state = state.copy(managementRoute = PaymentMethodsRoute) },
        onAddMovement = { state = state.copy(managementRoute = MovementRoute.Create) },
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
