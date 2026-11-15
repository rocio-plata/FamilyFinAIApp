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

/** Resultado de una operación: los fallos de red o de servidor nunca se lanzan. */
sealed interface CategoryResult<out T> {
    data class Success<out T>(val value: T) : CategoryResult<T>
    data class Failure(val failure: CategoryFailure) : CategoryResult<Nothing>
}
