package com.rocioplata.familyfinaiapp.data.family

import com.rocioplata.familyfinaiapp.data.network.ConnectivityObserver
import com.rocioplata.familyfinaiapp.domain.family.FamilyAccessFailure
import com.rocioplata.familyfinaiapp.domain.family.FamilyAccessResult
import com.rocioplata.familyfinaiapp.domain.family.InvitationsRepository
import com.rocioplata.familyfinaiapp.domain.family.ReceivedInvitation
import com.rocioplata.familyfinaiapp.domain.header.Family
import com.rocioplata.familyfinaiapp.domain.header.HeaderRepository
import kotlinx.coroutines.flow.first

/**
 * Invitaciones recibidas (RF-18 a RF-21). Requieren conexión y no se guardan (RF-25): lo único que persiste es el
 * contador de la cabecera, que se fija con la lista recibida y baja tras cada respuesta correcta.
 */
class InvitationsRepositoryImpl(
    private val remote: FamilyAccessRemoteDataSource,
    private val header: HeaderRepository,
    private val connectivity: ConnectivityObserver,
) : InvitationsRepository {
    override suspend fun received(): FamilyAccessResult<List<ReceivedInvitation>> = online {
        val result = remote.receivedInvitations()
        if (result is FamilyAccessResult.Success) header.setPendingInvitations(result.value.size)
        result
    }

    override suspend fun accept(invitation: ReceivedInvitation): FamilyAccessResult<Unit> = online {
        val result = remote.acceptInvitation(invitation.id)
        if (result is FamilyAccessResult.Success) {
            // La respuesta no trae la familia: se añade con lo que dice la invitación (sin moneda ni número de
            // miembros, que el refresco completa). Sin moneda no se puede registrar un movimiento.
            header.addFamily(Family(invitation.familyId, invitation.familyName, invitation.role))
            decrementCount()
            // El resultado no importa: la familia ya está en la lista y el refresco solo la completa.
            header.refresh()
        }
        result
    }

    override suspend fun decline(invitationId: String): FamilyAccessResult<Unit> = online {
        val result = remote.declineInvitation(invitationId)
        if (result is FamilyAccessResult.Success) decrementCount()
        result
    }

    private suspend fun decrementCount() {
        val count = header.header.first().pendingInvitations?.value ?: return
        header.setPendingInvitations((count - 1).coerceAtLeast(0))
    }

    private suspend fun <T> online(block: suspend () -> FamilyAccessResult<T>): FamilyAccessResult<T> =
        if (connectivity.isOnline.first()) block() else FamilyAccessResult.Failure(FamilyAccessFailure.Offline)
}
