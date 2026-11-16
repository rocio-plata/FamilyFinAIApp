package com.rocioplata.familyfinaiapp.ui.paymentmethods

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rocioplata.familyfinaiapp.data.network.ConnectivityObserver
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethod
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethodFailure
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethodNameError
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethodNameValidation
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethodResult
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethodStatus
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethodsData
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethodValidation
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethodsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Estado del listado de medios de pago de una familia (RF-02, RF-03, RF-11) y de su formulario de alta
 * y renombrado (RF-06, RF-07, RF-13). La sección de archivados desplegada y el formulario con su texto
 * viven aquí: sobreviven a la recreación. Se crea con una clave `sesión-familia`, por lo que
 * cambiar de familia o de usuario nunca reutiliza su estado.
 */
class PaymentMethodsViewModel(
    private val repository: PaymentMethodsRepository,
    private val familyId: String,
    connectivity: ConnectivityObserver,
    /** El backend dijo que el usuario ya no puede gestionar esta familia: que la pantalla la reevalúe. */
    private val onInsufficientRole: () -> Unit = {},
) : ViewModel() {
    private val archivedExpanded = MutableStateFlow(false)
    private val refreshState = MutableStateFlow(RefreshState())
    private val formState = MutableStateFlow<PaymentMethodFormState?>(null)
    private val opState = MutableStateFlow(OpState())
    private var refreshJob: Job? = null

    val uiState: StateFlow<PaymentMethodsUiState> = combine(
        repository.paymentMethods(familyId),
        archivedExpanded,
        refreshState,
        formState,
        opState,
    ) { data, expanded, refresh, form, op -> buildState(data, expanded, refresh, form, op) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PaymentMethodsUiState.Loading)

    init {
        refresh()
        viewModelScope.launch {
            connectivity.isOnline.drop(1).filter { it }.collect { refresh() }
        }
    }

    /** Al volver a primer plano. Ignorado si ya hay una carga en curso. */
    fun onStart() = refresh()

    fun retry() = refresh()

    fun toggleArchived() {
        archivedExpanded.value = !archivedExpanded.value
    }

    /** Abre el formulario de alta. Ignorado si ya hay uno abierto. */
    fun openCreate() = openForm(PaymentMethodFormTarget.Create, initialName = "")

    /** Abre el formulario con el nombre actual. Un medio archivado no se renombra (spec, «Datos y reglas»). */
    fun openRename(method: PaymentMethod) {
        if (method.status != PaymentMethodStatus.ACTIVE) return
        openForm(PaymentMethodFormTarget.Rename(method.id), initialName = method.name)
    }

    fun onNameChange(text: String) {
        formState.update { form ->
            if (form == null || form.isSaving) form
            else form.copy(text = text.take(PaymentMethodValidation.MAX_NAME_LENGTH), error = null)
        }
    }

    /**
     * Guarda el formulario. Nombre vacío: error del campo sin llamar al backend. Renombrar sin cambios:
     * cierra sin llamar. Una sola operación a la vez (CA-17): `isSaving` se fija antes de lanzar la corrutina.
     */
    fun save() {
        val form = formState.value ?: return
        if (form.isSaving || form.confirmingDiscard) return
        val name = when (val validation = PaymentMethodValidation.validateName(form.text)) {
            is PaymentMethodNameValidation.Invalid -> {
                formState.value = form.copy(error = validation.error.toFormError())
                return
            }
            is PaymentMethodNameValidation.Valid -> validation.name
        }
        if (form.target is PaymentMethodFormTarget.Rename && name == form.initialName) {
            formState.value = null
            return
        }
        if (!startOperation()) return
        formState.value = form.copy(isSaving = true, error = null)
        viewModelScope.launch {
            val result = try {
                submit(form.target, name)
            } catch (e: CancellationException) {
                formState.update { it?.copy(isSaving = false) }
                throw e
            } finally {
                finishOperation()
            }
            onSaveResult(result)
        }
    }

    /** Cancelar, atrás y tocar fuera: un único punto. Con cambios pide confirmar el descarte (CA-19). */
    fun requestClose() {
        val form = formState.value ?: return
        if (form.isSaving || form.confirmingDiscard) return
        formState.value = if (form.text.trim() != form.initialName) form.copy(confirmingDiscard = true) else null
    }

    fun confirmDiscard() {
        if (formState.value?.confirmingDiscard == true) formState.value = null
    }

    /** Seguir en el formulario con lo escrito. */
    fun dismissDiscard() {
        formState.update { it?.copy(confirmingDiscard = false) }
    }

    /** Pide confirmación para archivar un activo (RF-08). */
    fun requestArchive(method: PaymentMethod) {
        if (method.status == PaymentMethodStatus.ACTIVE) requestAction(PaymentMethodPendingAction.ConfirmArchive(method))
    }

    /** Pide confirmación para eliminar un medio, activo o archivado (RF-09). */
    fun requestDelete(method: PaymentMethod) = requestAction(PaymentMethodPendingAction.ConfirmDelete(method))

    /** Acepta el diálogo pendiente (confirmar archivar, confirmar eliminar u ofrecer archivar). */
    fun confirmPendingAction() {
        val action = opState.value.pending ?: return
        if (!startOperation()) return
        opState.update { it.copy(pending = null) }
        viewModelScope.launch {
            val result = try {
                when (action) {
                    is PaymentMethodPendingAction.ConfirmDelete ->
                        repository.deletePaymentMethod(familyId, action.method.id)
                    is PaymentMethodPendingAction.ConfirmArchive,
                    is PaymentMethodPendingAction.OfferArchive,
                    -> repository.deprecatePaymentMethod(familyId, action.method.id)
                }
            } finally {
                finishOperation()
            }
            if (result is PaymentMethodResult.Failure) onActionFailed(action, result.failure)
        }
    }

    fun dismissPendingAction() {
        opState.update { if (it.busy) it else it.copy(pending = null) }
    }

    fun consumeEvent() {
        opState.update { it.copy(event = null) }
    }

    private fun requestAction(action: PaymentMethodPendingAction) {
        opState.update { if (it.busy || it.pending != null || formState.value != null) it else it.copy(pending = action) }
    }

    /** Una sola operación a la vez: devuelve false si ya hay una en curso (CA-17). */
    private fun startOperation(): Boolean = !opState.getAndUpdate { it.copy(busy = true) }.busy

    private fun finishOperation() = opState.update { it.copy(busy = false) }

    private fun onActionFailed(action: PaymentMethodPendingAction, failure: PaymentMethodFailure) {
        when {
            // Eliminar un activo con movimientos: se ofrece archivarlo; en un archivado solo se informa (RF-09).
            failure == PaymentMethodFailure.HasAssociatedItems &&
                action is PaymentMethodPendingAction.ConfirmDelete &&
                action.method.status == PaymentMethodStatus.ACTIVE ->
                opState.update { it.copy(pending = PaymentMethodPendingAction.OfferArchive(action.method)) }
            else -> notifyFailure(failure)
        }
    }

    private fun notifyFailure(failure: PaymentMethodFailure) {
        opState.update { it.copy(event = PaymentMethodsEvent.Failed(failure)) }
        when (failure) {
            PaymentMethodFailure.NotFound -> refresh()
            PaymentMethodFailure.InsufficientRole -> onInsufficientRole()
            else -> Unit
        }
    }

    private fun openForm(target: PaymentMethodFormTarget, initialName: String) {
        if (formState.value != null) return
        formState.value = PaymentMethodFormState(target, initialName, text = initialName)
    }

    private suspend fun submit(target: PaymentMethodFormTarget, name: String): PaymentMethodResult<Unit> =
        when (target) {
            PaymentMethodFormTarget.Create -> when (val created = repository.createPaymentMethod(familyId, name)) {
                is PaymentMethodResult.Success -> PaymentMethodResult.Success(Unit)
                is PaymentMethodResult.Failure -> created
            }
            is PaymentMethodFormTarget.Rename ->
                repository.renamePaymentMethod(familyId, target.paymentMethodId, name)
        }

    private fun onSaveResult(result: PaymentMethodResult<Unit>) {
        when (result) {
            is PaymentMethodResult.Success -> formState.value = null
            is PaymentMethodResult.Failure -> when (result.failure) {
                PaymentMethodFailure.NotFound -> {
                    formState.value = null
                    notifyFailure(PaymentMethodFailure.NotFound)
                }
                PaymentMethodFailure.DuplicateName -> showFormError(PaymentMethodFormError.Duplicate)
                else -> {
                    showFormError(PaymentMethodFormError.Failure(result.failure))
                    if (result.failure == PaymentMethodFailure.InsufficientRole) onInsufficientRole()
                }
            }
        }
    }

    private fun showFormError(error: PaymentMethodFormError) {
        formState.update { it?.copy(isSaving = false, error = error) }
    }

    private fun PaymentMethodNameError.toFormError() = when (this) {
        PaymentMethodNameError.REQUIRED -> PaymentMethodFormError.Required
        PaymentMethodNameError.TOO_LONG -> PaymentMethodFormError.TooLong
    }

    private fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            refreshState.value = refreshState.value.copy(inFlight = true)
            val result = try {
                repository.refresh(familyId)
            } finally {
                refreshState.value = refreshState.value.copy(inFlight = false)
            }
            refreshState.value = refreshState.value.copy(lastResult = result)
        }
    }

    private fun buildState(
        data: PaymentMethodsData,
        archivedExpanded: Boolean,
        refresh: RefreshState,
        form: PaymentMethodFormState?,
        op: OpState,
    ): PaymentMethodsUiState {
        val methods = data.paymentMethods ?: return when {
            refresh.inFlight || refresh.lastResult == null -> PaymentMethodsUiState.Loading
            refresh.lastResult is PaymentMethodResult.Failure -> PaymentMethodsUiState.Error
            else -> PaymentMethodsUiState.Loading
        }
        return PaymentMethodsUiState.Content(
            active = methods.filter { it.status == PaymentMethodStatus.ACTIVE },
            archived = methods.filter { it.status == PaymentMethodStatus.DEPRECATED },
            archivedExpanded = archivedExpanded,
            isStale = data.isStale,
            isRefreshing = refresh.inFlight,
            form = form,
            pendingAction = op.pending,
            isBusy = op.busy,
            event = op.event,
        )
    }

    private data class OpState(
        val busy: Boolean = false,
        val pending: PaymentMethodPendingAction? = null,
        val event: PaymentMethodsEvent? = null,
    )

    private data class RefreshState(
        val inFlight: Boolean = false,
        val lastResult: PaymentMethodResult<Unit>? = null,
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
