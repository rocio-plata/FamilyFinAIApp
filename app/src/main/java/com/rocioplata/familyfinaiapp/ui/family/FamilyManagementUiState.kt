package com.rocioplata.familyfinaiapp.ui.family

import com.rocioplata.familyfinaiapp.domain.family.FamilyAccessFailure
import com.rocioplata.familyfinaiapp.domain.header.FamilyRole

/** Datos de la familia que muestra la cabecera de «Mi familia» (RF-06): salen de la cabecera y se actualizan en vivo. */
data class FamilyInfo(
    val name: String,
    /** Código de la moneda (por ejemplo `CLP`); nulo si no se conoce. */
    val currency: String?,
    /** Rol del usuario en esta familia. */
    val role: FamilyRole,
    /** De la lista de miembros si está cargada; si no, el que dio la cabecera (puede faltar). */
    val memberCount: Int?,
)

/** Una fila de la lista de miembros (RF-06 a RF-11), ya con lo que el usuario puede hacer con ella. */
data class MemberRow(
    val userId: String,
    /** Nulo o vacío si el miembro no tiene nombre: la interfaz muestra un texto genérico. */
    val displayName: String?,
    val role: FamilyRole,
    /** Es el propio usuario («Tú»). */
    val isYou: Boolean,
    /** Solo para un Propietario, con conexión; nunca se guarda (RF-07). */
    val email: String?,
    /** El rol al que pasaría con «Cambiar rol» (el contrario del actual). */
    val newRole: FamilyRole,
    /** Cambiar el rol: solo un Propietario, y no al único Propietario (RF-09, RF-11). */
    val canChangeRole: Boolean,
    /** Quitar a otra persona: solo un Propietario y nunca a sí mismo (RF-10). */
    val canRemove: Boolean,
)

sealed interface MembersSection {
    /** Sin lista todavía (ni copia ni respuesta). */
    data object Loading : MembersSection

    /** Sin lista y el refresco falló: error con «Reintentar» solo en esta sección. */
    data object Error : MembersSection

    data class Content(
        val rows: List<MemberRow>,
        /** La lista tiene más de 24 h sin confirmarse con el backend. */
        val isStale: Boolean,
        val isRefreshing: Boolean,
    ) : MembersSection
}

/** Si el usuario puede salir de la familia y, si no, por qué (RF-12). */
enum class LeaveState {
    Allowed,

    /** Es la única familia que tiene. */
    OnlyFamily,

    /** Es su único Propietario: antes debe nombrar a otro. */
    OnlyOwner,

    /** Es Propietario y no hay lista de miembros para saber si es el único. */
    NeedsConnection,
}

/** Acción que espera confirmación del usuario (RF-09, RF-10, RF-12). */
sealed interface FamilyPendingAction {
    data class ChangeRole(
        val userId: String,
        val displayName: String?,
        val newRole: FamilyRole,
        val isYou: Boolean,
    ) : FamilyPendingAction

    data class Remove(val userId: String, val displayName: String?) : FamilyPendingAction

    data class Leave(val familyName: String) : FamilyPendingAction
}

/** Algo que la pantalla muestra una sola vez y luego confirma con `consumeEvent()`. */
sealed interface FamilyManagementEvent {
    data class Failed(val failure: FamilyAccessFailure) : FamilyManagementEvent
}

data class FamilyManagementUiState(
    /** Nulo si la cabecera ya no tiene esta familia (la pantalla se cierra sola). */
    val family: FamilyInfo? = null,
    /** El rol actual es Propietario: se ve en la cabecera en cada momento y decide qué acciones hay (RF-08). */
    val isOwner: Boolean = false,
    val members: MembersSection = MembersSection.Loading,
    val leave: LeaveState = LeaveState.NeedsConnection,
    val pendingAction: FamilyPendingAction? = null,
    /** Hay una acción en curso: las demás quedan deshabilitadas (CA-30). */
    val isBusy: Boolean = false,
    val event: FamilyManagementEvent? = null,
)
