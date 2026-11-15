package com.rocioplata.familyfinaiapp.domain.paymentmethods

sealed interface PaymentMethodFailure {
    /** No hay conexión; se devuelve sin llamar al backend (RF-12). */
    data object Offline : PaymentMethodFailure
    data object Network : PaymentMethodFailure
    data object InsufficientRole : PaymentMethodFailure
    data object Unauthorized : PaymentMethodFailure
    data object DuplicateName : PaymentMethodFailure
    data object InvalidName : PaymentMethodFailure

    /** El medio es el predeterminado de algún miembro; el backend lo rechaza al archivar y al eliminar (RF-10). */
    data object IsSomeonesDefault : PaymentMethodFailure
    data object HasAssociatedItems : PaymentMethodFailure
    data object NotFound : PaymentMethodFailure
    data object InvalidData : PaymentMethodFailure
    data object Unknown : PaymentMethodFailure
}

/** Resultado de una operación: los fallos de red o de servidor nunca se lanzan. */
sealed interface PaymentMethodResult<out T> {
    data class Success<out T>(val value: T) : PaymentMethodResult<T>
    data class Failure(val failure: PaymentMethodFailure) : PaymentMethodResult<Nothing>
}
