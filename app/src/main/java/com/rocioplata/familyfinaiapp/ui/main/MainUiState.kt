package com.rocioplata.familyfinaiapp.ui.main

import com.rocioplata.familyfinaiapp.domain.family.FamilyAccessFailure
import com.rocioplata.familyfinaiapp.domain.header.Family

sealed interface FamiliesState {
    data object Loading : FamiliesState

    /** El backend respondió sin familias: pasa si quitan al usuario de la única que tenía (RF-24 de la 009). */
    data object NoData : FamiliesState

    data class Content(
        val families: List<Family>,
        val activeFamily: Family,
        val isStale: Boolean,
    ) : FamiliesState

    data object Error : FamiliesState
}

/** Algo que la pantalla principal debe decir una sola vez y luego confirmar con `consumeNotice()`. */
sealed interface MainNotice {
    /** La familia de la gestión abierta ya no es del usuario (salió, lo quitaron o dejó de existir) (RF-22). */
    data class NoLongerMember(val familyName: String) : MainNotice
}

data class MainUiState(
    val families: FamiliesState = FamiliesState.Loading,
    val pendingInvitations: Int? = null,
    val displayName: String? = null,
    val selectorOpen: Boolean = false,
    /** El último reordenado de familias falló (RF-02): el selector lo muestra en línea; se limpia al cerrarlo o mover otra. */
    val selectorError: FamilyAccessFailure? = null,
    /** Se está guardando un reordenado: el selector no admite otro movimiento. */
    val isReordering: Boolean = false,
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
    /**
     * Cuántas veces ha cambiado la familia activa desde que se abrió la pantalla (RF-01, CA-18 de la 008): forma parte de
     * la clave del ViewModel del dashboard, así que cada cambio, también volver a una familia anterior, empieza en el
     * mes en curso con gastos. Cambia con la familia efectiva, también si la anterior desaparece de la cabecera.
     */
    val activeFamilyInstance: Int = 0,
    /** Aviso pendiente de mostrar (RF-22 de la 009). */
    val notice: MainNotice? = null,
    // Esta spec no genera cambios locales (RF-09); la sincronización posterior lo activará.
    val hasLocalChanges: Boolean = false,
)
