package com.rocioplata.familyfinaiapp.ui.family

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rocioplata.familyfinaiapp.data.network.ConnectivityObserver
import com.rocioplata.familyfinaiapp.domain.family.FamilyAccessFailure
import com.rocioplata.familyfinaiapp.domain.family.FamilyAccessResult
import com.rocioplata.familyfinaiapp.domain.family.FamilyManagementRepository
import com.rocioplata.familyfinaiapp.domain.header.Family
import com.rocioplata.familyfinaiapp.domain.header.FamilyRole
import com.rocioplata.familyfinaiapp.domain.header.HeaderData
import com.rocioplata.familyfinaiapp.domain.header.HeaderRepository
import com.rocioplata.familyfinaiapp.domain.members.FamilyMember
import com.rocioplata.familyfinaiapp.domain.members.FamilyMembersData
import com.rocioplata.familyfinaiapp.domain.members.FamilyMembersFailure
import com.rocioplata.familyfinaiapp.domain.members.FamilyMembersRepository
import com.rocioplata.familyfinaiapp.domain.members.FamilyMembersResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.Collator

/**
 * Estado de «Mi familia» de [familyId] (RF-06 a RF-12, RF-25). Se crea con una clave `sesión-familia`, así que cambiar de
 * familia o de usuario nunca reutiliza su estado. La familia y el rol salen en vivo de la cabecera: un cambio de rol
 * propio deja la pantalla en solo lectura sin recargarla (RF-08, RF-09).
 *
 * [onInsufficientRole] avisa de que el backend rechazó una acción por rol o por no ser ya miembro: la cabecera se vuelve a
 * consultar y, si la familia ya no es del usuario, la gestión se cierra sola (RF-22).
 */
class FamilyManagementViewModel(
    private val familyId: String,
    private val currentUserId: String?,
    private val header: HeaderRepository,
    private val members: FamilyMembersRepository,
    private val management: FamilyManagementRepository,
    connectivity: ConnectivityObserver,
    private val onInsufficientRole: () -> Unit = {},
) : ViewModel() {
    // Los correos solo viven aquí: ni el repositorio de miembros ni el disco los guardan (RF-07).
    private val emails = MutableStateFlow<Map<String, String>>(emptyMap())
    private val refreshState = MutableStateFlow(RefreshState())
    private val opState = MutableStateFlow(OpState())
    private val online = MutableStateFlow(true)
    private var refreshJob: Job? = null

    val uiState: StateFlow<FamilyManagementUiState> = combine(
        header.header,
        members.members(familyId),
        emails,
        combine(refreshState, opState, online, ::Aux),
    ) { headerData, membersData, emails, aux -> buildState(headerData, membersData, emails, aux) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), FamilyManagementUiState())

    init {
        refresh()
        viewModelScope.launch {
            var first = true
            connectivity.isOnline.collect { isOnline ->
                online.value = isOnline
                // La primera emisión es el estado de partida: solo se refresca al recuperar la conexión.
                if (isOnline && !first) refresh()
                first = false
            }
        }
    }

    /** Al volver a primer plano. Ignorado si ya hay una carga en curso. */
    fun onStart() = refresh()

    fun retry() = refresh()

    fun consumeEvent() {
        opState.update { it.copy(event = null) }
    }

    /** «Cambiar rol» de un miembro: pide confirmación (RF-09). Ignorado si esa fila no lo permite (RF-08, RF-11). */
    fun requestChangeRole(userId: String) {
        val row = rowOf(userId) ?: return
        if (!row.canChangeRole) return
        requestAction(FamilyPendingAction.ChangeRole(row.userId, row.displayName, row.newRole, row.isYou))
    }

    /** «Quitar» a otra persona: pide confirmación (RF-10). */
    fun requestRemove(userId: String) {
        val row = rowOf(userId) ?: return
        if (!row.canRemove) return
        requestAction(FamilyPendingAction.Remove(row.userId, row.displayName))
    }

    /** «Salir de la familia»: pide confirmación (RF-12). Ignorado si está deshabilitado (única familia, único Propietario). */
    fun requestLeave() {
        val state = uiState.value
        if (state.leave != LeaveState.Allowed) return
        requestAction(FamilyPendingAction.Leave(state.family?.name ?: return))
    }

    /** Acepta el diálogo pendiente. Una sola operación a la vez: el indicador se fija antes de lanzar la corrutina (CA-30). */
    fun confirmPendingAction() {
        val action = opState.value.pending ?: return
        if (opState.getAndUpdate { it.copy(busy = true) }.busy) return
        opState.update { it.copy(pending = null) }
        viewModelScope.launch {
            val result = try {
                when (action) {
                    is FamilyPendingAction.ChangeRole -> management.changeMemberRole(familyId, action.userId, action.newRole)
                    is FamilyPendingAction.Remove -> management.removeMember(familyId, action.userId)
                    is FamilyPendingAction.Leave -> management.leaveFamily(familyId)
                }
            } finally {
                opState.update { it.copy(busy = false) }
            }
            // Con éxito no hay nada más que hacer: la lista, el rol y, al salir, la familia ya cambiaron en la cabecera.
            if (result is FamilyAccessResult.Failure) onFailed(result.failure)
        }
    }

    fun dismissPendingAction() {
        opState.update { if (it.busy) it else it.copy(pending = null) }
    }

    private fun rowOf(userId: String): MemberRow? =
        (uiState.value.members as? MembersSection.Content)?.rows?.firstOrNull { it.userId == userId }

    private fun requestAction(action: FamilyPendingAction) {
        opState.update { if (it.busy || it.pending != null) it else it.copy(pending = action) }
    }

    private fun onFailed(failure: FamilyAccessFailure) {
        opState.update { it.copy(event = FamilyManagementEvent.Failed(failure)) }
        when (failure) {
            // Los miembros cambiaron por otro lado: se vuelve a leer la lista (RF-11, CA-13).
            FamilyAccessFailure.LastOwner, FamilyAccessFailure.MemberNotFound -> refresh()
            // El rol o la pertenencia cambiaron: se vuelve a consultar la cabecera (RF-22, CA-26).
            FamilyAccessFailure.InsufficientRole, FamilyAccessFailure.NotAMember -> onInsufficientRole()
            else -> Unit
        }
    }

    private fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            refreshState.update { it.copy(inFlight = true) }
            val result = try {
                load()
            } finally {
                refreshState.update { it.copy(inFlight = false) }
            }
            refreshState.update { it.copy(hasRun = true, failure = (result as? FamilyMembersResult.Failure)?.failure) }
            if ((result as? FamilyMembersResult.Failure)?.failure == FamilyMembersFailure.NotAllowed) onInsufficientRole()
        }
    }

    // Un Propietario con conexión pide también los correos; el resto, solo la lista (RF-07, RF-08).
    private suspend fun load(): FamilyMembersResult<Unit> {
        if (!isOwnerNow() || !online.value) return members.refresh(familyId)
        return when (val result = members.refreshWithEmails(familyId)) {
            is FamilyMembersResult.Success -> {
                emails.value = result.value
                FamilyMembersResult.Success(Unit)
            }
            is FamilyMembersResult.Failure -> result
        }
    }

    private suspend fun isOwnerNow(): Boolean =
        header.header.first().families?.value?.firstOrNull { it.id == familyId }?.role == FamilyRole.OWNER

    private fun buildState(
        headerData: HeaderData,
        membersData: FamilyMembersData,
        emails: Map<String, String>,
        aux: Aux,
    ): FamilyManagementUiState {
        val families = headerData.families?.value
        val family = families?.firstOrNull { it.id == familyId }
        val isOwner = family?.role == FamilyRole.OWNER
        val list = membersData.members
        val showEmails = isOwner && aux.online
        val ownersCount = list?.count { it.role == FamilyRole.OWNER } ?: 0
        val section = when {
            list == null -> when {
                aux.refresh.inFlight || !aux.refresh.hasRun -> MembersSection.Loading
                aux.refresh.failure != null -> MembersSection.Error
                else -> MembersSection.Loading
            }
            else -> MembersSection.Content(
                rows = orderMembers(list, currentUserId).map { member ->
                    val isYou = member.userId == currentUserId
                    MemberRow(
                        userId = member.userId,
                        displayName = member.displayName,
                        role = member.role,
                        isYou = isYou,
                        email = emails[member.userId].takeIf { showEmails },
                        newRole = if (member.role == FamilyRole.OWNER) FamilyRole.MEMBER else FamilyRole.OWNER,
                        // Al único Propietario no se le ofrece dejar de serlo (RF-11).
                        canChangeRole = isOwner && !(member.role == FamilyRole.OWNER && ownersCount <= 1),
                        canRemove = isOwner && !isYou,
                    )
                },
                isStale = membersData.isStale,
                isRefreshing = aux.refresh.inFlight,
            )
        }
        return FamilyManagementUiState(
            family = family?.let {
                FamilyInfo(it.name, it.currency, it.role, list?.size ?: it.memberCount)
            },
            isOwner = isOwner,
            members = section,
            leave = leaveState(families, isOwner, list, ownersCount),
            pendingAction = aux.op.pending,
            isBusy = aux.op.busy,
            event = aux.op.event,
        )
    }

    private fun leaveState(
        families: List<Family>?,
        isOwner: Boolean,
        list: List<FamilyMember>?,
        ownersCount: Int,
    ): LeaveState = when {
        (families?.size ?: 0) <= 1 -> LeaveState.OnlyFamily
        // Un Integrante puede salir sin más; un Propietario necesita la lista para saber si es el único.
        !isOwner -> LeaveState.Allowed
        list == null -> LeaveState.NeedsConnection
        ownersCount <= 1 -> LeaveState.OnlyOwner
        else -> LeaveState.Allowed
    }

    private data class RefreshState(
        val inFlight: Boolean = false,
        val hasRun: Boolean = false,
        val failure: FamilyMembersFailure? = null,
    )

    private data class OpState(
        val busy: Boolean = false,
        val pending: FamilyPendingAction? = null,
        val event: FamilyManagementEvent? = null,
    )

    private data class Aux(val refresh: RefreshState, val op: OpState, val online: Boolean)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/**
 * Orden de la lista (RF-06): primero el propio usuario, después los Propietarios y después los Integrantes, cada grupo
 * por nombre sin distinguir mayúsculas; quien no tiene nombre va al final de su grupo.
 */
internal fun orderMembers(members: List<FamilyMember>, currentUserId: String?): List<FamilyMember> {
    // Con el criterio del idioma del dispositivo y sin distinguir mayúsculas ni tildes: «Álvaro» va con la A.
    val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
    return members.sortedWith(
        compareBy<FamilyMember> { if (it.userId == currentUserId) 0 else 1 }
            .thenBy { if (it.role == FamilyRole.OWNER) 0 else 1 }
            .thenBy { if (it.displayName.isNullOrBlank()) 1 else 0 }
            .thenComparator { a, b -> collator.compare(a.displayName.orEmpty().trim(), b.displayName.orEmpty().trim()) }
            .thenBy { it.userId },
    )
}
