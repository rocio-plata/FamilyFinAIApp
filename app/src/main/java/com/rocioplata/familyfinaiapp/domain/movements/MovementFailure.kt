package com.rocioplata.familyfinaiapp.domain.movements

sealed interface MovementFailure {
    /** No hay conexión; se devuelve sin llamar al backend (RF-14). */
    data object Offline : MovementFailure
    data object Network : MovementFailure

    /** El backend rechazó una fecha futura; la app no debería enviarla (RF-16). */
    data object FutureDate : MovementFailure

    /** La categoría elegida ya no está disponible (archivada o eliminada) (CA-22). */
    data object CategoryUnavailable : MovementFailure

    /** La etiqueta elegida ya no está disponible o no es de la categoría (CA-22). */
    data object TagUnavailable : MovementFailure

    /** El medio de pago elegido ya no está disponible o no hay uno (CA-22). */
    data object PaymentMethodUnavailable : MovementFailure

    /** Reclasificar entre gasto e ingreso: la app solo ofrece categorías del mismo tipo. */
    data object TypeMismatch : MovementFailure

    /** La moneda de la familia cambió respecto a la que usa la app. */
    data object CurrencyMismatch : MovementFailure
    data object NotFound : MovementFailure
    data object InvalidData : MovementFailure

    /** El backend respondió `403`: el usuario ya no puede operar en esta familia. */
    data object NotAllowed : MovementFailure
    data object Unauthorized : MovementFailure
    data object Unknown : MovementFailure
}

/** Resultado de una operación: los fallos de red o de servidor nunca se lanzan. */
sealed interface MovementResult<out T> {
    data class Success<out T>(val value: T) : MovementResult<T>
    data class Failure(val failure: MovementFailure) : MovementResult<Nothing>
}

/**
 * Resultado de una edición, que puede ser en dos llamadas (datos y después categoría, RF-10). Si fallan los
 * datos no se intenta la categoría.
 */
sealed interface MovementEditResult {
    data object Success : MovementEditResult

    /** No se guardó nada. */
    data class Failure(val failure: MovementFailure) : MovementEditResult

    /** Los datos se guardaron pero el cambio de categoría o etiqueta falló con [failure]. */
    data class Partial(val failure: MovementFailure) : MovementEditResult
}
