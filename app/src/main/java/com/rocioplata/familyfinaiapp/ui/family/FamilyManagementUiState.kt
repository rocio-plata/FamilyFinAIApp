package com.rocioplata.familyfinaiapp.ui.family

import com.rocioplata.familyfinaiapp.domain.family.CurrencyLockReason
import com.rocioplata.familyfinaiapp.domain.family.FamilyAccessFailure
import com.rocioplata.familyfinaiapp.domain.header.FamilyRole
import java.time.Instant

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

/** Sección de la moneda de la familia (RF-13): solo la ve un Propietario y se carga aparte de los miembros. */
sealed interface CurrencySection {
    /** Un Integrante no la ve. */
    data object Hidden : CurrencySection

    /** Comprobando si la moneda se puede cambiar. */
    data object Loading : CurrencySection

    /** No se pudo comprobar: no se ofrece cambiarla y se puede reintentar. */
    data object Error : CurrencySection

    /**
     * [isLocked] sin [lockReasons] es un bloqueo cuyo motivo la app no conoce: nunca se trata como desbloqueada.
     */
    data class Content(
        val current: String,
        val options: List<String>,
        val isLocked: Boolean,
        val lockReasons: Set<CurrencyLockReason>,
    ) : CurrencySection
}

/** Una invitación enviada que se muestra en «Mi familia» (RF-15, RF-16). */
data class SentInvitationRow(
    val id: String,
    val email: String,
    val role: FamilyRole,
    val expiresAt: Instant,
    /** Su fecha ya pasó: se muestra como «Vencida» con «Volver a invitar» y «Quitar». */
    val isExpired: Boolean,
)

/** Sección de invitaciones enviadas (RF-15): solo la ve un Propietario y requiere conexión; no se guarda nada (RF-25). */
sealed interface InvitationsSection {
    data object Hidden : InvitationsSection
    data object Loading : InvitationsSection

    /** No se pudo cargar; [failure] distingue «sin conexión» del resto. Se puede reintentar. */
    data class Error(val failure: FamilyAccessFailure) : InvitationsSection

    /** [rows]: las vigentes por vencimiento y, al final, las vencidas que no tienen otra vigente ni son de un miembro. */
    data class Content(val rows: List<SentInvitationRow>, val activeCount: Int) : InvitationsSection
}

/** Lo que falla junto al campo del correo al invitar (RF-14, RF-17); lo escrito se conserva siempre. */
sealed interface InviteFormError {
    data object Required : InviteFormError
    data object InvalidFormat : InviteFormError
    data object TooLong : InviteFormError

    /** Ya hay una invitación vigente a ese correo: antes hay que revocarla (RF-17). */
    data object AlreadyInvited : InviteFormError

    /** Ese correo ya es de un miembro de la familia. */
    data object AlreadyMember : InviteFormError

    data class Failure(val failure: FamilyAccessFailure) : InviteFormError
}

/** Formulario de «Invitar miembro» (RF-14); vive en el ViewModel y sobrevive a la recreación (CA-31). */
data class InviteFormState(
    val email: String,
    val role: FamilyRole,
    /** Con lo que se abrió (vacío al invitar; el correo y rol de la vencida al volver a invitar): sin cambios se cierra directo. */
    val initialEmail: String = "",
    val initialRole: FamilyRole = FamilyRole.MEMBER,
    val error: InviteFormError? = null,
    val isSaving: Boolean = false,
    val confirmingDiscard: Boolean = false,
) {
    val hasChanges: Boolean get() = email.trim() != initialEmail.trim() || role != initialRole
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

    /** Revocar una invitación vigente (RF-15). */
    data class RevokeInvitation(val invitationId: String, val email: String) : FamilyPendingAction
}

/** Algo que la pantalla muestra una sola vez y luego confirma con `consumeEvent()`. */
sealed interface FamilyManagementEvent {
    data class Failed(val failure: FamilyAccessFailure) : FamilyManagementEvent

    /** La invitación se creó: el aviso explica que no se envía ningún correo (RF-14). */
    data class InvitationSent(val email: String) : FamilyManagementEvent
}

data class FamilyManagementUiState(
    /** Nulo si la cabecera ya no tiene esta familia (la pantalla se cierra sola). */
    val family: FamilyInfo? = null,
    /** El rol actual es Propietario: se ve en la cabecera en cada momento y decide qué acciones hay (RF-08). */
    val isOwner: Boolean = false,
    val members: MembersSection = MembersSection.Loading,
    val currency: CurrencySection = CurrencySection.Hidden,
    val invitations: InvitationsSection = InvitationsSection.Hidden,
    val inviteForm: InviteFormState? = null,
    val leave: LeaveState = LeaveState.NeedsConnection,
    val pendingAction: FamilyPendingAction? = null,
    /** Hay una acción en curso: las demás quedan deshabilitadas (CA-30). */
    val isBusy: Boolean = false,
    val event: FamilyManagementEvent? = null,
) {
    /** «Invitar miembro» solo se ofrece a un Propietario con la lista de invitaciones cargada: sin ella no se puede evitar un duplicado. */
    val canInvite: Boolean get() = isOwner && invitations is InvitationsSection.Content
}
