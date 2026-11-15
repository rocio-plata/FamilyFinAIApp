package com.rocioplata.familyfinaiapp.ui.paymentmethods

import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethod

sealed interface PaymentMethodsUiState {
    /** Sin datos todavía (ni caché ni respuesta): primera carga. */
    data object Loading : PaymentMethodsUiState

    /** Sin datos y el refresco falló: error con «Reintentar». */
    data object Error : PaymentMethodsUiState

    data class Content(
        /** Medios activos, en el orden que devuelve el backend (RF-02). */
        val active: List<PaymentMethod>,
        /** Medios archivados, en el orden que devuelve el backend (RF-03). */
        val archived: List<PaymentMethod>,
        val archivedExpanded: Boolean,
        /** Los datos tienen más de 24 h (RF-11). */
        val isStale: Boolean,
        val isRefreshing: Boolean,
    ) : PaymentMethodsUiState {
        /** No hay ningún medio activo (excepcional: el backend crea algunos al crear la familia). */
        val isEmpty: Boolean get() = active.isEmpty()
    }
}
