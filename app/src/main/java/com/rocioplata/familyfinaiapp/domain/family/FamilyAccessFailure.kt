package com.rocioplata.familyfinaiapp.domain.family

sealed interface FamilyAccessFailure {
    /** No hay conexión; se devuelve sin llamar al backend (RF-25). */
    data object Offline : FamilyAccessFailure
    data object Network : FamilyAccessFailure
    data object InsufficientRole : FamilyAccessFailure

    /** El usuario ya no pertenece a la familia, o esta ya no existe. */
    data object NotAMember : FamilyAccessFailure
    data object Unauthorized : FamilyAccessFailure
    data object InvalidName : FamilyAccessFailure
    data object InvalidOrder : FamilyAccessFailure
    data object AlreadyMember : FamilyAccessFailure
    data object InvalidEmail : FamilyAccessFailure

    /** Dejaría a la familia sin Propietario (RF-11). */
    data object LastOwner : FamilyAccessFailure
    data object MemberNotFound : FamilyAccessFailure
    data object InvitationNotFound : FamilyAccessFailure
    data object InvitationExpired : FamilyAccessFailure
    data object InvitationNotPending : FamilyAccessFailure

    /** La invitación es de otro correo. */
    data object InvitationNotForUser : FamilyAccessFailure
    data object CurrencyLocked : FamilyAccessFailure
    data object InvalidData : FamilyAccessFailure
    data object Unknown : FamilyAccessFailure
}

/** Resultado de una operación: los fallos de red o de servidor nunca se lanzan. */
sealed interface FamilyAccessResult<out T> {
    data class Success<out T>(val value: T) : FamilyAccessResult<T>
    data class Failure(val failure: FamilyAccessFailure) : FamilyAccessResult<Nothing>
}
