package com.rocioplata.familyfinaiapp.ui.family

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rocioplata.familyfinaiapp.data.network.ConnectivityObserver
import com.rocioplata.familyfinaiapp.domain.family.FamilyAccessFailure
import com.rocioplata.familyfinaiapp.domain.family.FamilyAccessResult
import com.rocioplata.familyfinaiapp.domain.family.FamilyManagementRepository
import com.rocioplata.familyfinaiapp.domain.family.FamilySettings
import com.rocioplata.familyfinaiapp.domain.family.InvitationEmailError
import com.rocioplata.familyfinaiapp.domain.family.InvitationEmailValidation
import com.rocioplata.familyfinaiapp.domain.family.InvitationValidation
import com.rocioplata.familyfinaiapp.domain.family.SentInvitation
import com.rocioplata.familyfinaiapp.domain.family.SupportedCurrencies
import com.rocioplata.familyfinaiapp.domain.header.Family
import com.rocioplata.familyfinaiapp.domain.header.FamilyRole
import com.rocioplata.familyfinaiapp.domain.header.HeaderData
import com.rocioplata.familyfinaiapp.domain.header.HeaderRepository
import com.rocioplata.familyfinaiapp.domain.members.FamilyMember
import com.rocioplata.familyfinaiapp.domain.members.FamilyMembersData
import com.rocioplata.familyfinaiapp.domain.members.FamilyMembersFailure
import com.rocioplata.familyfinaiapp.domain.members.FamilyMembersRepository
import com.rocioplata.familyfinaiapp.domain.members.FamilyMembersResult
import java.time.Instant
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
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
    private val now: () -> Instant = Instant::now,
) : ViewModel() {
    // Los correos solo viven aquí: ni el repositorio de miembros ni el disco los guardan (RF-07).
    private val emails = MutableStateFlow<Map<String, String>>(emptyMap())
    private val refreshState = MutableStateFlow(RefreshState())
    private val opState = MutableStateFlow(OpState())
    private val online = MutableStateFlow(true)
    private val settings = MutableStateFlow<SettingsLoad>(SettingsLoad.Idle)
    private var refreshJob: Job? = null
    private var settingsJob: Job? = null
    private var invitationsJob: Job? = null
    private val sent = MutableStateFlow<SentLoad>(SentLoad.Idle)
    private val form = MutableStateFlow<InviteFormState?>(null)

    // Un intento de invitar terminó en un fallo de red: pudo llegar al backend. Antes de enviar otro se comprueba si la
    // invitación ya está, para no duplicarla (el correo y las invitaciones que había antes del intento).
    private var uncertainInvite: UncertainInvite? = null

    val uiState: StateFlow<FamilyManagementUiState> = combine(
        header.header,
        members.members(familyId),
        emails,
        combine(refreshState, opState, online, ::Aux),
        combine(settings, sent, form, ::Owned),
    ) { headerData, membersData, emails, aux, owned -> buildState(headerData, membersData, emails, aux, owned) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), FamilyManagementUiState())

    init {
        refresh()
        viewModelScope.launch {
            // Si pasa a ser Propietario con la pantalla abierta, se piden sus datos (correos y moneda) sin esperar a otra carga.
            header.header
                .map { data -> data.families?.value?.firstOrNull { it.id == familyId }?.role == FamilyRole.OWNER }
                .distinctUntilChanged()
                .drop(1)
                .filter { it }
                .collect { refresh() }
        }
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

    /** «Reintentar» de la sección de la moneda. */
    fun retryCurrency() = loadSettings()

    /** «Reintentar» de la sección de invitaciones enviadas. */
    fun retryInvitations() = loadInvitations()

    // --- Invitaciones enviadas (RF-14 a RF-17) ---

    /** «Invitar miembro»: abre el formulario vacío con Integrante preseleccionado. Solo con la lista de invitaciones cargada. */
    fun openInviteForm() {
        if (!canInviteNow()) return
        form.value = InviteFormState(email = "", role = FamilyRole.MEMBER)
    }

    /** «Volver a invitar» de una vencida: abre el formulario con su correo y su rol (RF-16). */
    fun reinvite(invitationId: String) {
        if (!canInviteNow()) return
        val old = (sent.value as? SentLoad.Loaded)?.list?.firstOrNull { it.id == invitationId } ?: return
        form.value = InviteFormState(email = old.email, role = old.role, initialEmail = old.email, initialRole = old.role)
    }

    fun onInviteEmailChange(text: String) {
        form.update { current ->
            if (current == null || current.isSaving || current.confirmingDiscard) current
            else current.copy(email = text.take(InvitationValidation.MAX_EMAIL_LENGTH), error = null)
        }
    }

    fun onInviteRoleChange(role: FamilyRole) {
        form.update { current ->
            if (current == null || current.isSaving || current.confirmingDiscard) current else current.copy(role = role)
        }
    }

    /**
     * Valida y crea la invitación. Una sola operación a la vez: el indicador se fija antes de lanzar la corrutina (CA-30).
     * Sin un correo válido no se llama al backend; tras un fallo de red incierto primero se comprueba si ya se creó.
     */
    fun submitInvite() {
        val current = form.value ?: return
        if (current.isSaving || current.confirmingDiscard) return
        if (opState.getAndUpdate { it.copy(busy = true) }.busy) return
        form.value = current.copy(isSaving = true, error = null)
        viewModelScope.launch {
            val outcome = try {
                invite(current)
            } finally {
                opState.update { it.copy(busy = false) }
                form.update { it?.copy(isSaving = false) }
            }
            when (outcome) {
                is InviteOutcome.Sent -> {
                    form.value = null
                    opState.update { it.copy(event = FamilyManagementEvent.InvitationSent(outcome.email)) }
                }
                is InviteOutcome.Error -> form.update { it?.copy(error = outcome.error) }
            }
        }
    }

    /** Atrás, Cancelar y tocar fuera: con cambios piden confirmación; durante el envío no se puede salir. */
    fun requestCloseInvite() {
        val current = form.value ?: return
        if (current.isSaving || current.confirmingDiscard) return
        form.value = if (current.hasChanges) current.copy(confirmingDiscard = true) else null
    }

    fun confirmDiscardInvite() {
        if (form.value?.confirmingDiscard == true) form.value = null
    }

    fun dismissDiscardInvite() {
        form.update { it?.copy(confirmingDiscard = false) }
    }

    /** «Revocar» una invitación vigente: pide confirmación (RF-15). */
    fun requestRevoke(invitationId: String) {
        val invitation = (sent.value as? SentLoad.Loaded)?.list?.firstOrNull { it.id == invitationId } ?: return
        if (invitation.isExpired(now())) return
        requestAction(FamilyPendingAction.RevokeInvitation(invitation.id, invitation.email))
    }

    /** «Quitar» una vencida de la lista: la revoca en el backend sin pedir confirmación (RF-16). */
    fun removeExpired(invitationId: String) {
        val invitation = (sent.value as? SentLoad.Loaded)?.list?.firstOrNull { it.id == invitationId } ?: return
        if (!invitation.isExpired(now())) return
        if (opState.getAndUpdate { it.copy(busy = true) }.busy) return
        viewModelScope.launch { revoke(invitationId) }
    }

    private fun canInviteNow(): Boolean {
        // Lo pendiente y lo en curso se leen del estado interno, que va por delante del derivado que ve la pantalla.
        val op = opState.value
        return uiState.value.canInvite && !op.busy && op.pending == null && form.value == null
    }

    private suspend fun revoke(invitationId: String) {
        val result = try {
            management.revokeInvitation(invitationId)
        } finally {
            opState.update { it.copy(busy = false) }
        }
        when (result) {
            is FamilyAccessResult.Success -> sent.update { load ->
                if (load is SentLoad.Loaded) SentLoad.Loaded(load.list.filterNot { it.id == invitationId }) else load
            }
            is FamilyAccessResult.Failure -> onFailed(result.failure)
        }
    }

    private suspend fun invite(current: InviteFormState): InviteOutcome {
        // Tras un fallo de red incierto manda lo que haya en el backend, aunque el texto haya cambiado desde entonces.
        uncertainInvite?.let { previous ->
            when (val reloaded = management.sentInvitations(familyId)) {
                is FamilyAccessResult.Failure -> return InviteOutcome.Error(InviteFormError.Failure(FamilyAccessFailure.Network))
                is FamilyAccessResult.Success -> {
                    sent.value = SentLoad.Loaded(reloaded.value)
                    val created = reloaded.value.firstOrNull {
                        it.id !in previous.idsBefore && !it.isExpired(now()) && InvitationValidation.sameEmail(it.email, previous.email)
                    }
                    uncertainInvite = null
                    if (created != null) return InviteOutcome.Sent(created.email)
                }
            }
        }
        val known = (sent.value as? SentLoad.Loaded)?.list.orEmpty()
        val email = when (val validation = InvitationValidation.validateEmail(current.email, known, now())) {
            is InvitationEmailValidation.Valid -> validation.email
            is InvitationEmailValidation.Invalid -> return InviteOutcome.Error(validation.error.toFormError())
        }
        // Un correo que ya es de un miembro se detecta sin llamar al backend cuando se conocen los correos (RF-07).
        if (showEmailsNow() && emails.value.values.any { InvitationValidation.sameEmail(it, email) }) {
            return InviteOutcome.Error(InviteFormError.AlreadyMember)
        }
        val idsBefore = known.map { it.id }.toSet()
        return when (val result = management.invite(familyId, email, current.role)) {
            is FamilyAccessResult.Success -> {
                sent.update { load ->
                    if (load is SentLoad.Loaded) SentLoad.Loaded(load.list.filterNot { it.id == result.value.id } + result.value) else load
                }
                InviteOutcome.Sent(email)
            }
            is FamilyAccessResult.Failure -> {
                when (result.failure) {
                    FamilyAccessFailure.Network -> uncertainInvite = UncertainInvite(email, idsBefore)
                    FamilyAccessFailure.InsufficientRole, FamilyAccessFailure.NotAMember -> onInsufficientRole()
                    else -> Unit
                }
                InviteOutcome.Error(
                    if (result.failure == FamilyAccessFailure.AlreadyMember) InviteFormError.AlreadyMember
                    else InviteFormError.Failure(result.failure),
                )
            }
        }
    }

    private fun showEmailsNow(): Boolean = uiState.value.isOwner && online.value

    private fun InvitationEmailError.toFormError(): InviteFormError = when (this) {
        InvitationEmailError.REQUIRED -> InviteFormError.Required
        InvitationEmailError.INVALID_FORMAT -> InviteFormError.InvalidFormat
        InvitationEmailError.TOO_LONG -> InviteFormError.TooLong
        InvitationEmailError.ALREADY_INVITED -> InviteFormError.AlreadyInvited
    }

    /**
     * Cambia la moneda de la familia (RF-13). Solo si la sección está cargada y desbloqueada; una sola operación a la vez.
     * Con éxito la cabecera ya tiene la moneda nueva y toda la app la usa.
     */
    fun changeCurrency(code: String) {
        val state = uiState.value
        val section = state.currency as? CurrencySection.Content ?: return
        if (!state.isOwner || section.isLocked || code == section.current || code !in section.options) return
        if (opState.getAndUpdate { it.copy(busy = true) }.busy) return
        viewModelScope.launch {
            val result = try {
                management.changeCurrency(familyId, code)
            } finally {
                opState.update { it.copy(busy = false) }
            }
            when (result) {
                is FamilyAccessResult.Success ->
                    settings.update { if (it is SettingsLoad.Loaded) SettingsLoad.Loaded(it.settings.copy(currency = code)) else it }
                is FamilyAccessResult.Failure -> onFailed(result.failure)
            }
        }
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
                    is FamilyPendingAction.RevokeInvitation -> management.revokeInvitation(action.invitationId)
                }
            } finally {
                opState.update { it.copy(busy = false) }
            }
            // Con éxito la lista, el rol y, al salir, la familia ya cambiaron en la cabecera; una invitación revocada sale de
            // la lista de enviadas, que solo vive aquí.
            when (result) {
                is FamilyAccessResult.Failure -> onFailed(result.failure)
                is FamilyAccessResult.Success ->
                    if (action is FamilyPendingAction.RevokeInvitation) sent.update { load ->
                        if (load is SentLoad.Loaded) SentLoad.Loaded(load.list.filterNot { it.id == action.invitationId }) else load
                    }
            }
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
            // Se bloqueó entretanto: se vuelve a leer el estado para mostrar el motivo (CA-17).
            FamilyAccessFailure.CurrencyLocked -> loadSettings()
            // La invitación ya no es lo que se creía (otra persona la revocó o venció): se vuelve a leer la lista (RF-21).
            FamilyAccessFailure.InvitationNotFound,
            FamilyAccessFailure.InvitationNotPending,
            FamilyAccessFailure.InvitationExpired,
            -> loadInvitations()
            // El rol o la pertenencia cambiaron: se vuelve a consultar la cabecera (RF-22, CA-26).
            FamilyAccessFailure.InsufficientRole, FamilyAccessFailure.NotAMember -> onInsufficientRole()
            else -> Unit
        }
    }

    private fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            // Los ajustes se cargan aparte: un fallo de la lista de miembros no oculta la moneda ni al revés.
            if (isOwnerNow()) {
                loadSettings()
                loadInvitations()
            }
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

    private fun loadSettings() {
        if (settingsJob?.isActive == true) return
        settingsJob = viewModelScope.launch {
            // Mientras se vuelve a leer se conserva lo que ya se sabía.
            settings.update { if (it is SettingsLoad.Loaded) it else SettingsLoad.Loading }
            when (val result = management.settings(familyId)) {
                is FamilyAccessResult.Success -> settings.value = SettingsLoad.Loaded(result.value)
                is FamilyAccessResult.Failure -> {
                    settings.update { if (it is SettingsLoad.Loaded) it else SettingsLoad.Failed }
                    if (result.failure == FamilyAccessFailure.NotAMember || result.failure == FamilyAccessFailure.InsufficientRole) {
                        onInsufficientRole()
                    }
                }
            }
        }
    }

    private fun loadInvitations() {
        if (invitationsJob?.isActive == true) return
        invitationsJob = viewModelScope.launch {
            sent.update { if (it is SentLoad.Loaded) it else SentLoad.Loading }
            when (val result = management.sentInvitations(familyId)) {
                is FamilyAccessResult.Success -> sent.value = SentLoad.Loaded(result.value)
                is FamilyAccessResult.Failure -> {
                    sent.update { if (it is SentLoad.Loaded) it else SentLoad.Failed(result.failure) }
                    if (result.failure == FamilyAccessFailure.NotAMember || result.failure == FamilyAccessFailure.InsufficientRole) {
                        onInsufficientRole()
                    }
                }
            }
        }
    }

    private suspend fun isOwnerNow(): Boolean =
        header.header.first().families?.value?.firstOrNull { it.id == familyId }?.role == FamilyRole.OWNER

    private fun buildState(
        headerData: HeaderData,
        membersData: FamilyMembersData,
        emails: Map<String, String>,
        aux: Aux,
        owned: Owned,
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
            currency = currencySection(isOwner, owned.settings),
            invitations = invitationsSection(isOwner, owned.sent, if (showEmails) emails.values.toSet() else emptySet()),
            inviteForm = owned.form,
            leave = leaveState(families, isOwner, list, ownersCount),
            pendingAction = aux.op.pending,
            isBusy = aux.op.busy,
            event = aux.op.event,
        )
    }

    private fun invitationsSection(isOwner: Boolean, load: SentLoad, memberEmails: Set<String>): InvitationsSection = when {
        !isOwner -> InvitationsSection.Hidden
        load is SentLoad.Loaded -> {
            val moment = now()
            // Una vencida no se muestra si ese correo tiene otra vigente o ya es de un miembro (RF-16). Sin los correos de
            // los miembros no se puede saber lo segundo y no se oculta nada por ello.
            val validEmails = load.list.filterNot { it.isExpired(moment) }.map { it.email.trim().lowercase(Locale.ROOT) }.toSet()
            val members = memberEmails.map { it.trim().lowercase(Locale.ROOT) }.toSet()
            val rows = load.list
                .map { SentInvitationRow(it.id, it.email, it.role, it.expiresAt, it.isExpired(moment)) }
                .filter { row ->
                    val key = row.email.trim().lowercase(Locale.ROOT)
                    !row.isExpired || (key !in validEmails && key !in members)
                }
                .sortedWith(compareBy<SentInvitationRow> { it.isExpired }.thenBy { it.expiresAt }.thenBy { it.id })
            InvitationsSection.Content(rows, activeCount = rows.count { !it.isExpired })
        }
        load is SentLoad.Failed -> InvitationsSection.Error(load.failure)
        else -> InvitationsSection.Loading
    }

    private fun currencySection(isOwner: Boolean, load: SettingsLoad): CurrencySection = when {
        !isOwner -> CurrencySection.Hidden
        load is SettingsLoad.Loaded -> CurrencySection.Content(
            current = load.settings.currency,
            options = SupportedCurrencies.codes,
            isLocked = load.settings.isLocked,
            lockReasons = load.settings.lockReasons,
        )
        load is SettingsLoad.Failed -> CurrencySection.Error
        else -> CurrencySection.Loading
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

    private sealed interface SettingsLoad {
        data object Idle : SettingsLoad
        data object Loading : SettingsLoad
        data object Failed : SettingsLoad
        data class Loaded(val settings: FamilySettings) : SettingsLoad
    }

    private sealed interface SentLoad {
        data object Idle : SentLoad
        data object Loading : SentLoad
        data class Failed(val failure: FamilyAccessFailure) : SentLoad
        data class Loaded(val list: List<SentInvitation>) : SentLoad
    }

    private class UncertainInvite(val email: String, val idsBefore: Set<String>)

    private sealed interface InviteOutcome {
        data class Sent(val email: String) : InviteOutcome
        data class Error(val error: InviteFormError) : InviteOutcome
    }

    private data class Aux(val refresh: RefreshState, val op: OpState, val online: Boolean)

    private data class Owned(val settings: SettingsLoad, val sent: SentLoad, val form: InviteFormState?)

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
