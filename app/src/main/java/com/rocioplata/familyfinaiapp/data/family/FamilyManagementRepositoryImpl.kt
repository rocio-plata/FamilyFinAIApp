package com.rocioplata.familyfinaiapp.data.family

import com.rocioplata.familyfinaiapp.data.network.ConnectivityObserver
import com.rocioplata.familyfinaiapp.domain.family.FamilyAccessFailure
import com.rocioplata.familyfinaiapp.domain.family.FamilyAccessResult
import com.rocioplata.familyfinaiapp.domain.family.FamilyManagementRepository
import com.rocioplata.familyfinaiapp.domain.family.FamilySettings
import com.rocioplata.familyfinaiapp.domain.family.SentInvitation
import com.rocioplata.familyfinaiapp.domain.header.Family
import com.rocioplata.familyfinaiapp.domain.header.FamilyRole
import com.rocioplata.familyfinaiapp.domain.header.HeaderRepository
import com.rocioplata.familyfinaiapp.domain.members.FamilyMembersRepository
import com.rocioplata.familyfinaiapp.session.SessionFailure
import com.rocioplata.familyfinaiapp.session.SessionProvider
import com.rocioplata.familyfinaiapp.session.SessionUnavailableException
import kotlinx.coroutines.flow.first

/**
 * Los cambios requieren conexión (RF-25): sin ella se devuelve `Offline` sin llamar al backend; con éxito se aplican a la
 * cabecera y a la lista de miembros, y un fallo no cambia nada. No guarda datos propios: las invitaciones enviadas y los
 * ajustes se piden cada vez y viven en el ViewModel.
 */
class FamilyManagementRepositoryImpl(
    private val remote: FamilyAccessRemoteDataSource,
    private val header: HeaderRepository,
    private val members: FamilyMembersRepository,
    private val sessionProvider: SessionProvider,
    private val connectivity: ConnectivityObserver,
) : FamilyManagementRepository {
    override suspend fun createFamily(name: String): FamilyAccessResult<Family> = online {
        remote.createFamily(name).onSuccess { header.addFamily(it) }
    }

    override suspend fun reorderFamilies(orderedFamilyIds: List<String>): FamilyAccessResult<Unit> = online {
        remote.reorderFamilies(orderedFamilyIds).onSuccess { header.setFamilyOrder(orderedFamilyIds) }
    }

    override suspend fun settings(familyId: String): FamilyAccessResult<FamilySettings> = online {
        remote.settings(familyId)
    }

    override suspend fun changeCurrency(familyId: String, currency: String): FamilyAccessResult<Unit> = online {
        remote.changeCurrency(familyId, currency).onSuccess {
            header.updateFamily(familyId) { it.copy(currency = currency) }
        }
    }

    override suspend fun changeMemberRole(
        familyId: String,
        memberId: String,
        newRole: FamilyRole,
    ): FamilyAccessResult<Unit> = online {
        val userId = when (val session = userId()) {
            is FamilyAccessResult.Failure -> return@online session
            is FamilyAccessResult.Success -> session.value
        }
        remote.changeMemberRole(familyId, memberId, newRole).onSuccess {
            members.applyRole(familyId, memberId, newRole)
            // El rol propio es el que muestran la cabecera y el selector, y el que decide qué se puede hacer.
            if (memberId == userId) header.updateFamily(familyId) { it.copy(role = newRole) }
        }
    }

    override suspend fun removeMember(familyId: String, memberId: String): FamilyAccessResult<Unit> = online {
        remote.removeMember(familyId, memberId).onSuccess {
            members.applyRemoval(familyId, memberId)
            header.updateFamily(familyId) { family ->
                family.copy(memberCount = family.memberCount?.let { (it - 1).coerceAtLeast(1) })
            }
        }
    }

    override suspend fun leaveFamily(familyId: String): FamilyAccessResult<Unit> = online {
        val userId = when (val session = userId()) {
            is FamilyAccessResult.Failure -> return@online session
            is FamilyAccessResult.Success -> session.value
        }
        // Salir es quitarse a uno mismo (BG-01): la baja de la cabecera borra también lo guardado de la familia.
        remote.removeMember(familyId, userId).onSuccess { header.removeFamily(familyId) }
    }

    override suspend fun sentInvitations(familyId: String): FamilyAccessResult<List<SentInvitation>> = online {
        remote.sentInvitations(familyId)
    }

    override suspend fun invite(familyId: String, email: String, role: FamilyRole): FamilyAccessResult<SentInvitation> =
        online { remote.invite(familyId, email, role) }

    override suspend fun revokeInvitation(invitationId: String): FamilyAccessResult<Unit> = online {
        remote.revokeInvitation(invitationId)
    }

    private suspend fun <T> online(block: suspend () -> FamilyAccessResult<T>): FamilyAccessResult<T> =
        if (connectivity.isOnline.first()) block() else FamilyAccessResult.Failure(FamilyAccessFailure.Offline)

    private suspend fun userId(): FamilyAccessResult<String> = try {
        FamilyAccessResult.Success(sessionProvider.current().userId)
    } catch (e: SessionUnavailableException) {
        FamilyAccessResult.Failure(
            when (e.failure) {
                SessionFailure.NOT_SIGNED_IN -> FamilyAccessFailure.Unauthorized
                SessionFailure.UNKNOWN -> FamilyAccessFailure.Unknown
            },
        )
    }

    private suspend fun <T> FamilyAccessResult<T>.onSuccess(apply: suspend (T) -> Unit): FamilyAccessResult<T> {
        if (this is FamilyAccessResult.Success) apply(value)
        return this
    }
}
