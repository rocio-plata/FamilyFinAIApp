package com.rocioplata.familyfinaiapp.ui.main

import com.rocioplata.familyfinaiapp.domain.header.Family

sealed interface FamiliesState {
    data object Loading : FamiliesState

    /** El backend respondió sin familias (no se espera: siempre existe la personal). */
    data object NoData : FamiliesState

    data class Content(
        val families: List<Family>,
        val activeFamily: Family,
        val isStale: Boolean,
    ) : FamiliesState {
        val hasSelector: Boolean get() = families.size > 1
    }

    data object Error : FamiliesState
}

data class MainUiState(
    val families: FamiliesState = FamiliesState.Loading,
    val pendingInvitations: Int? = null,
    val displayName: String? = null,
    val selectorOpen: Boolean = false,
    val isRefreshing: Boolean = false,
    // Esta spec no genera cambios locales (RF-09); la sincronización posterior lo activará.
    val hasLocalChanges: Boolean = false,
)
