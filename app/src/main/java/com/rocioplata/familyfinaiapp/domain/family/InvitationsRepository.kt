package com.rocioplata.familyfinaiapp.domain.family

/**
 * Invitaciones recibidas (RF-18 a RF-21). Sin guardar nada: el contador de la cabecera se fija con la lista recibida y
 * baja tras cada respuesta.
 */
interface InvitationsRepository {
    /** Las pendientes y vigentes del usuario; fija el contador de la cabecera. */
    suspend fun received(): FamilyAccessResult<List<ReceivedInvitation>>

    /** Acepta: la familia se añade a la cabecera con lo que dice la invitación y se refresca para completarla. */
    suspend fun accept(invitation: ReceivedInvitation): FamilyAccessResult<Unit>

    suspend fun decline(invitationId: String): FamilyAccessResult<Unit>
}
