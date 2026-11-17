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
    /** Pestaña inferior seleccionada; vive en el ViewModel, así que sobrevive a recrear la pantalla (RF-08 de la 001). */
    val selectedTab: MainDestination = MainDestination.Home,
    /** Gestión de la familia activa (categorías o medios de pago) a pantalla completa; `null` muestra las pestañas. */
    val managementRoute: ManagementRoute? = null,
    /**
     * Identifica cada apertura de una gestión: un formulario nuevo no debe reutilizar el estado de uno anterior
     * (se usa en la clave de su ViewModel). Atrás dentro de una gestión (edición → detalle) la conserva.
     */
    val managementRouteInstance: Int = 0,
    // Esta spec no genera cambios locales (RF-09); la sincronización posterior lo activará.
    val hasLocalChanges: Boolean = false,
)
