package com.rocioplata.familyfinaiapp.domain.categories

sealed interface CategoryFailure {
    /** No hay conexión; se devuelve sin llamar al backend (RF-21). */
    data object Offline : CategoryFailure
    data object Network : CategoryFailure
    data object InsufficientRole : CategoryFailure
    data object Unauthorized : CategoryFailure
    data object DuplicateName : CategoryFailure
    data object NotFound : CategoryFailure
    data object HasAssociatedItems : CategoryFailure
    data object CategoryNotActive : CategoryFailure
    data object InvalidData : CategoryFailure
    data object Unknown : CategoryFailure
}

/**
 * Resultado de reactivar una categoría o un tag (RF-01, RF-04 y RF-11 de la 010): reactivar y llevarlo al final de las
 * activas son dos llamadas. `Partial` es una reactivación confirmada cuyo nuevo orden no se pudo guardar.
 */
sealed interface ReactivationResult {
    data object Success : ReactivationResult
    data class Partial(val orderFailure: CategoryFailure) : ReactivationResult
    data class Failure(val failure: CategoryFailure) : ReactivationResult
}

/** Resultado de una operación: los fallos de red o de servidor nunca se lanzan. */
sealed interface CategoryResult<out T> {
    data class Success<out T>(val value: T) : CategoryResult<T>
    data class Failure(val failure: CategoryFailure) : CategoryResult<Nothing>
}
