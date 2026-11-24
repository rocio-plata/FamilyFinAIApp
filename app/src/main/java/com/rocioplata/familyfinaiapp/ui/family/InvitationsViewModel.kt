package com.rocioplata.familyfinaiapp.ui.family

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rocioplata.familyfinaiapp.data.network.ConnectivityObserver
import com.rocioplata.familyfinaiapp.domain.family.FamilyAccessFailure
import com.rocioplata.familyfinaiapp.domain.family.FamilyAccessResult
import com.rocioplata.familyfinaiapp.domain.family.InvitationsRepository
import com.rocioplata.familyfinaiapp.domain.family.ReceivedInvitation
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Estado de «Invitaciones» (RF-18 a RF-21, RF-25). Se crea con una clave por apertura de la ruta. No hay copia local: la
 * lista sale siempre del backend, que además fija el contador de la campana.
 */
class InvitationsViewModel(
    private val repository: InvitationsRepository,
    connectivity: ConnectivityObserver,
) : ViewModel() {
    private val state = MutableStateFlow(InvitationsUiState())
    val uiState: StateFlow<InvitationsUiState> = state.asStateFlow()

    // Lo que devolvió la última carga, con lo que se aceptan (la aceptación necesita el nombre y el rol de la invitación).
    private var invitations: List<ReceivedInvitation> = emptyList()
    private var loadJob: Job? = null

    init {
        load()
        viewModelScope.launch {
            var first = true
            connectivity.isOnline.collect { isOnline ->
                // La primera emisión es el estado de partida: solo se recarga al recuperar la conexión.
                if (isOnline && !first) load()
                first = false
            }
        }
    }

    /** Al volver a primer plano. Ignorado si ya hay una carga u operación en curso. */
    fun onStart() = load()

    fun retry() = load()

    fun consumeEvent() {
        state.update { it.copy(event = null) }
    }

    /** «Aceptar». Una sola operación a la vez: `busyId` se fija antes de lanzar la corrutina (CA-30). */
    fun accept(invitationId: String) {
        val invitation = invitations.firstOrNull { it.id == invitationId } ?: return
        if (!startOperation(invitationId)) return
        viewModelScope.launch {
            val result = try {
                repository.accept(invitation)
            } finally {
                state.update { it.copy(busyId = null) }
            }
            when (result) {
                is FamilyAccessResult.Success -> {
                    removeLocally(invitationId)
                    state.update { it.copy(event = InvitationsEvent.Accepted(invitation.familyId, invitation.familyName)) }
                }
                is FamilyAccessResult.Failure -> fail(result.failure)
            }
        }
    }

    /** «Rechazar»: pide confirmación y advierte de que haría falta otra invitación (RF-20). */
    fun requestDecline(invitationId: String) {
        val current = state.value
        if (current.isBusy || current.pendingDecline != null) return
        val row = (current.section as? ReceivedSection.Content)?.rows?.firstOrNull { it.id == invitationId } ?: return
        state.value = current.copy(pendingDecline = row)
    }

    fun dismissDecline() {
        state.update { it.copy(pendingDecline = null) }
    }

    fun confirmDecline() {
        val row = state.value.pendingDecline ?: return
        if (!startOperation(row.id)) return
        viewModelScope.launch {
            val result = try {
                repository.decline(row.id)
            } finally {
                state.update { it.copy(busyId = null, pendingDecline = null) }
            }
            when (result) {
                is FamilyAccessResult.Success -> {
                    removeLocally(row.id)
                    state.update { it.copy(event = InvitationsEvent.Declined) }
                }
                is FamilyAccessResult.Failure -> fail(result.failure)
            }
        }
    }

    private fun startOperation(invitationId: String): Boolean {
        val current = state.value
        if (current.isBusy) return false
        state.value = current.copy(busyId = invitationId, event = null)
        return true
    }

    /**
     * Informa del fallo. Si la invitación ya no se puede responder, la lista se vuelve a pedir (RF-21); un fallo de red o
     * sin conexión solo informa y deja todo como estaba.
     */
    private suspend fun fail(failure: FamilyAccessFailure) {
        state.update { it.copy(event = InvitationsEvent.Failed(failure)) }
        if (failure in STALE_LIST_FAILURES) loadNow()
    }

    private fun removeLocally(invitationId: String) {
        invitations = invitations.filterNot { it.id == invitationId }
        state.update { it.copy(section = sectionFor(invitations)) }
    }

    private fun load() {
        if (loadJob?.isActive == true || state.value.isBusy) return
        loadJob = viewModelScope.launch { loadNow() }
    }

    private suspend fun loadNow() {
        val hasList = state.value.section is ReceivedSection.Content || state.value.section is ReceivedSection.Empty
        state.update { it.copy(isRefreshing = hasList) }
        val result = try {
            repository.received()
        } finally {
            state.update { it.copy(isRefreshing = false) }
        }
        when (result) {
            is FamilyAccessResult.Success -> {
                invitations = result.value
                state.update { it.copy(section = sectionFor(invitations)) }
            }
            // Con una lista a la vista se conserva y se informa; sin ella, error con «Reintentar».
            is FamilyAccessResult.Failure -> state.update {
                if (hasList) {
                    if (it.event == null) it.copy(event = InvitationsEvent.Failed(result.failure)) else it
                } else {
                    it.copy(section = ReceivedSection.Error(result.failure))
                }
            }
        }
    }

    private fun sectionFor(list: List<ReceivedInvitation>): ReceivedSection =
        if (list.isEmpty()) ReceivedSection.Empty else ReceivedSection.Content(list.map { it.toRow() })

    private companion object {
        /** La invitación ya no se puede responder: se pide la lista de nuevo (RF-21). */
        val STALE_LIST_FAILURES = setOf(
            FamilyAccessFailure.InvitationNotFound,
            FamilyAccessFailure.InvitationExpired,
            FamilyAccessFailure.InvitationNotPending,
            FamilyAccessFailure.InvitationNotForUser,
        )
    }
}
