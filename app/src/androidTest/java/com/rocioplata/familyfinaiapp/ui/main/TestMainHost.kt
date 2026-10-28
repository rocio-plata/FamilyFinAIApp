package com.rocioplata.familyfinaiapp.ui.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.rocioplata.familyfinaiapp.domain.header.Family
import com.rocioplata.familyfinaiapp.domain.header.FamilyRole

val OwnerFamily = Family("f1", "Familia Plata", FamilyRole.OWNER)
val MemberFamily = Family("f2", "Familia Gómez", FamilyRole.MEMBER)

fun contentState(
    families: List<Family> = listOf(OwnerFamily, MemberFamily),
    active: Family = families.first(),
    isStale: Boolean = false,
) = FamiliesState.Content(families, active, isStale)

/** Reproduce lo mínimo del ViewModel (selector y familia activa) sin red ni caché. */
@Composable
fun TestMainHost(
    initial: MainUiState,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
) {
    var state by remember { mutableStateOf(initial) }
    MainScreen(
        uiState = state,
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
        modifier = modifier,
    )
}
