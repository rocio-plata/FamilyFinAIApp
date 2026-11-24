package com.rocioplata.familyfinaiapp.ui.family

import com.rocioplata.familyfinaiapp.data.family.FakeHeaderRepository
import com.rocioplata.familyfinaiapp.data.network.ConnectivityObserver
import com.rocioplata.familyfinaiapp.domain.family.FamilyAccessFailure
import com.rocioplata.familyfinaiapp.domain.family.FamilyAccessResult
import com.rocioplata.familyfinaiapp.domain.family.FamilyManagementRepository
import com.rocioplata.familyfinaiapp.domain.family.FamilySettings
import com.rocioplata.familyfinaiapp.domain.family.SentInvitation
import com.rocioplata.familyfinaiapp.domain.header.Family
import com.rocioplata.familyfinaiapp.domain.header.FamilyRole
import com.rocioplata.familyfinaiapp.domain.members.FamilyMember
import com.rocioplata.familyfinaiapp.domain.members.FamilyMembersData
import com.rocioplata.familyfinaiapp.domain.members.FamilyMembersFailure
import com.rocioplata.familyfinaiapp.domain.members.FamilyMembersRepository
import com.rocioplata.familyfinaiapp.domain.members.FamilyMembersResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Falsos de los tests de «Mi familia» (ViewModel y, después, de sus invitaciones y moneda). */

/** Miembros que el test controla: la lista, los correos, el resultado de cada refresco y una compuerta. */
internal class ControllableMembers(initial: List<FamilyMember>? = null, stale: Boolean = false) : FamilyMembersRepository {
    val data = MutableStateFlow(FamilyMembersData(initial, stale))
    var refreshes = 0
    var refreshesWithEmails = 0
    var failure: FamilyMembersFailure? = null
    var emails: Map<String, String> = emptyMap()
    var gate: CompletableDeferred<Unit>? = null

    /** Lo que trae el backend al refrescar; si es nulo, la lista no cambia. */
    var remote: List<FamilyMember>? = null

    override fun members(familyId: String): Flow<FamilyMembersData> = data

    override suspend fun refresh(familyId: String): FamilyMembersResult<Unit> {
        refreshes++
        return finish().let { failure -> if (failure == null) FamilyMembersResult.Success(Unit) else FamilyMembersResult.Failure(failure) }
    }

    override suspend fun refreshWithEmails(familyId: String): FamilyMembersResult<Map<String, String>> {
        refreshesWithEmails++
        return finish().let { failure -> if (failure == null) FamilyMembersResult.Success(emails) else FamilyMembersResult.Failure(failure) }
    }

    private suspend fun finish(): FamilyMembersFailure? {
        gate?.await()
        failure?.let { return it }
        remote?.let { data.value = data.value.copy(members = it) }
        return null
    }

    val roles = mutableListOf<Triple<String, String, FamilyRole>>()
    val removals = mutableListOf<Pair<String, String>>()

    override suspend fun applyRole(familyId: String, userId: String, role: FamilyRole) {
        roles += Triple(familyId, userId, role)
        data.value = data.value.copy(members = data.value.members?.map { if (it.userId == userId) it.copy(role = role) else it })
    }

    override suspend fun applyRemoval(familyId: String, userId: String) {
        removals += familyId to userId
        data.value = data.value.copy(members = data.value.members?.filterNot { it.userId == userId })
    }

    override suspend fun clear() = Unit
    override suspend fun retainFamilies(familyIds: Set<String>) = Unit
}

/** Gestión que registra lo pedido y aplica el efecto como el repositorio real (cabecera y miembros). */
internal class RecordingManagement(
    private val header: FakeHeaderRepository,
    private val members: ControllableMembers,
    private val currentUserId: String?,
) : FamilyManagementRepository {
    val roleChanges = mutableListOf<Triple<String, String, FamilyRole>>()
    val removals = mutableListOf<Pair<String, String>>()
    val leaves = mutableListOf<String>()
    var failure: FamilyAccessFailure? = null
    var gate: CompletableDeferred<Unit>? = null

    private suspend fun <T> result(value: T, apply: suspend () -> Unit): FamilyAccessResult<T> {
        gate?.await()
        failure?.let { return FamilyAccessResult.Failure(it) }
        apply()
        return FamilyAccessResult.Success(value)
    }

    override suspend fun changeMemberRole(familyId: String, memberId: String, newRole: FamilyRole): FamilyAccessResult<Unit> {
        roleChanges += Triple(familyId, memberId, newRole)
        return result(Unit) {
            members.applyRole(familyId, memberId, newRole)
            if (memberId == currentUserId) header.updateFamily(familyId) { it.copy(role = newRole) }
        }
    }

    override suspend fun removeMember(familyId: String, memberId: String): FamilyAccessResult<Unit> {
        removals += familyId to memberId
        return result(Unit) { members.applyRemoval(familyId, memberId) }
    }

    override suspend fun leaveFamily(familyId: String): FamilyAccessResult<Unit> {
        leaves += familyId
        return result(Unit) { header.removeFamily(familyId) }
    }

    override suspend fun createFamily(name: String): FamilyAccessResult<Family> = error("no se usa")
    override suspend fun reorderFamilies(orderedFamilyIds: List<String>): FamilyAccessResult<Unit> = error("no se usa")
    override suspend fun settings(familyId: String): FamilyAccessResult<FamilySettings> = error("no se usa")
    override suspend fun changeCurrency(familyId: String, currency: String): FamilyAccessResult<Unit> = error("no se usa")
    override suspend fun sentInvitations(familyId: String): FamilyAccessResult<List<SentInvitation>> = error("no se usa")
    override suspend fun invite(familyId: String, email: String, role: FamilyRole): FamilyAccessResult<SentInvitation> = error("no se usa")
    override suspend fun revokeInvitation(invitationId: String): FamilyAccessResult<Unit> = error("no se usa")
}

internal class ControllableConnectivity(initial: Boolean = true) : ConnectivityObserver {
    private val state = MutableStateFlow(initial)
    override val isOnline: Flow<Boolean> get() = state
    fun set(online: Boolean) {
        state.value = online
    }
}
