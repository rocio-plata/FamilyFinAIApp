package com.rocioplata.familyfinaiapp.ui.family

import com.rocioplata.familyfinaiapp.domain.family.FamilyAccessFailure
import com.rocioplata.familyfinaiapp.domain.family.ReceivedInvitation
import com.rocioplata.familyfinaiapp.domain.header.FamilyRole
import java.time.Instant

/** Una invitación recibida (RF-18). [invitedByName] nulo o vacío: la interfaz muestra un texto genérico. */
data class ReceivedInvitationRow(
    val id: String,
    val familyId: String,
    val familyName: String,
    val invitedByName: String?,
    val role: FamilyRole,
    val expiresAt: Instant,
)

sealed interface ReceivedSection {
    /** Sin lista todavía. */
    data object Loading : ReceivedSection

    /** Sin lista y la carga falló: error con «Reintentar» (RF-25). */
    data class Error(val failure: FamilyAccessFailure) : ReceivedSection

    /** Sin invitaciones pendientes (RF-18). */
    data object Empty : ReceivedSection

    data class Content(val rows: List<ReceivedInvitationRow>) : ReceivedSection
}

/** Aviso de una sola vez que la pantalla muestra y consume. */
sealed interface InvitationsEvent {
    /** Se aceptó: ofrece «Cambiar a esta familia» (RF-19). */
    data class Accepted(val familyId: String, val familyName: String) : InvitationsEvent

    data object Declined : InvitationsEvent

    data class Failed(val failure: FamilyAccessFailure) : InvitationsEvent
}

data class InvitationsUiState(
    val section: ReceivedSection = ReceivedSection.Loading,
    /** Recargando la lista con otra ya visible. */
    val isRefreshing: Boolean = false,
    /** Invitación que se está aceptando o rechazando; solo hay una operación a la vez (CA-30). */
    val busyId: String? = null,
    /** Rechazo pendiente de confirmar (RF-20). */
    val pendingDecline: ReceivedInvitationRow? = null,
    val event: InvitationsEvent? = null,
) {
    val isBusy: Boolean get() = busyId != null
}

internal fun ReceivedInvitation.toRow() = ReceivedInvitationRow(id, familyId, familyName, invitedByName, role, expiresAt)
