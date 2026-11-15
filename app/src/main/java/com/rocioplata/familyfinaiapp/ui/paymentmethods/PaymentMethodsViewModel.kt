package com.rocioplata.familyfinaiapp.ui.paymentmethods

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rocioplata.familyfinaiapp.data.network.ConnectivityObserver
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethodResult
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethodStatus
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethodsData
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethodsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Estado del listado de medios de pago de una familia (RF-02, RF-03, RF-11). La sección de archivados
 * desplegada vive aquí: sobrevive a la recreación. Se crea con una clave `sesión-familia`, por lo que
 * cambiar de familia o de usuario nunca reutiliza su estado.
 */
class PaymentMethodsViewModel(
    private val repository: PaymentMethodsRepository,
    private val familyId: String,
    connectivity: ConnectivityObserver,
) : ViewModel() {
    private val archivedExpanded = MutableStateFlow(false)
    private val refreshState = MutableStateFlow(RefreshState())
    private var refreshJob: Job? = null

    val uiState: StateFlow<PaymentMethodsUiState> = combine(
        repository.paymentMethods(familyId),
        archivedExpanded,
        refreshState,
    ) { data, expanded, refresh -> buildState(data, expanded, refresh) }
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
        )
    }

    private data class RefreshState(
        val inFlight: Boolean = false,
        val lastResult: PaymentMethodResult<Unit>? = null,
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
