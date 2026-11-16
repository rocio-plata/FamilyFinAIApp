package com.rocioplata.familyfinaiapp.ui.paymentmethods

import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethod
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethodFailure

/** Qué guarda el formulario: un medio nuevo o el nuevo nombre de uno existente (RF-06, RF-07). */
sealed interface PaymentMethodFormTarget {
    data object Create : PaymentMethodFormTarget
    data class Rename(val paymentMethodId: String) : PaymentMethodFormTarget
}

/** Error mostrado junto al campo del nombre; el texto escrito se conserva siempre (RF-12, RF-13). */
sealed interface PaymentMethodFormError {
    /** Nombre vacío o solo espacios (RF-13). */
    data object Required : PaymentMethodFormError
    data object TooLong : PaymentMethodFormError
    data object Duplicate : PaymentMethodFormError

    /** El guardado falló por otro motivo (sin conexión, red, servidor…). */
    data class Failure(val failure: PaymentMethodFailure) : PaymentMethodFormError
}

/** Formulario de alta o renombrado; vive en el ViewModel para sobrevivir a la recreación (CA-18). */
data class PaymentMethodFormState(
    val target: PaymentMethodFormTarget,
    /** Nombre con el que se abrió: vacío en el alta, el actual al renombrar. */
    val initialName: String,
    val text: String,
    val error: PaymentMethodFormError? = null,
    val isSaving: Boolean = false,
    /** Se pidió cerrar con cambios sin guardar: se muestra la confirmación de descarte (RF-13). */
    val confirmingDiscard: Boolean = false,
)

/** Algo que el listado debe mostrar una sola vez y luego confirmar con `consumeEvent()`. */
sealed interface PaymentMethodsEvent {
    /** Una operación falló sin poder mostrarse junto al campo: aviso (RF-12). */
    data class Failed(val failure: PaymentMethodFailure) : PaymentMethodsEvent
}

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
        val form: PaymentMethodFormState? = null,
        val event: PaymentMethodsEvent? = null,
    ) : PaymentMethodsUiState {
        /** No hay ningún medio activo (excepcional: el backend crea algunos al crear la familia). */
        val isEmpty: Boolean get() = active.isEmpty()
    }
}
