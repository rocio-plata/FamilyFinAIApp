package com.rocioplata.familyfinaiapp.domain.header

sealed interface HeaderFailure {
    data object Network : HeaderFailure
    data object InsufficientRole : HeaderFailure
    data object Unauthorized : HeaderFailure
    data object InvalidData : HeaderFailure
    data object Unknown : HeaderFailure
}

/** Un fallo nulo indica que ese recurso se refrescó correctamente. */
data class HeaderRefreshResult(
    val families: HeaderFailure? = null,
    val pendingInvitations: HeaderFailure? = null,
    val profile: HeaderFailure? = null,
) {
    val isSuccessful: Boolean get() = families == null && pendingInvitations == null && profile == null
}
