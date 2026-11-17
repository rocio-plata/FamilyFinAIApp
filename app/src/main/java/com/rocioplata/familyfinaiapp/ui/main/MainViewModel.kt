package com.rocioplata.familyfinaiapp.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rocioplata.familyfinaiapp.data.network.ConnectivityObserver
import com.rocioplata.familyfinaiapp.domain.categories.CategoryType
import com.rocioplata.familyfinaiapp.domain.header.HeaderData
import com.rocioplata.familyfinaiapp.domain.header.HeaderRefreshResult
import com.rocioplata.familyfinaiapp.domain.header.HeaderRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(
    private val repository: HeaderRepository,
    connectivity: ConnectivityObserver,
) : ViewModel() {
    // Solo en memoria: tras terminar el proceso vuelve a la favorita (RF-03).
    private val activeFamilyId = MutableStateFlow<String?>(null)
    private val selectorOpen = MutableStateFlow(false)
    private val refreshState = MutableStateFlow(RefreshState())

    // Ligada a la familia con la que se abrió: si esa familia deja de ser la activa, se descarta.
    private val managementRoute = MutableStateFlow<OpenRoute?>(null)

    // Solo en memoria: tras terminar el proceso se vuelve a Inicio (RF-08 de la 001).
    private val selectedTab = MutableStateFlow(MainDestination.Home)
    private var refreshJob: Job? = null
    private var nextRouteInstance = 1

    val uiState: StateFlow<MainUiState> = combine(
        repository.header,
        activeFamilyId,
        selectorOpen,
        refreshState,
        combine(managementRoute, selectedTab, ::Navigation),
    ) { data, activeId, selectorOpen, refresh, navigation ->
        buildState(data, activeId, selectorOpen, refresh, navigation.route, navigation.tab)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), MainUiState())

    init {
        refresh()
        viewModelScope.launch {
            // Si la familia con la que se abrió la gestión ya no existe (o se pierden los datos), se vuelve a las pestañas.
            repository.header.collect { data ->
                val ids = data.families?.value?.map { it.id }
                if (managementRoute.value?.let { ids == null || it.familyId !in ids } == true) {
                    managementRoute.value = null
                }
            }
        }
        viewModelScope.launch {
            connectivity.isOnline.drop(1).filter { it }.collect { refresh() }
        }
    }

    /** Al volver a primer plano. Ignorado si ya hay una carga en curso. */
    fun onStart() = refresh()

    fun retry() = refresh()

    fun openSelector() {
        // La familia no puede cambiarse dentro de una gestión (RF-01).
        if (managementRoute.value != null) return
        if (uiState.value.families.let { it is FamiliesState.Content && it.hasSelector }) selectorOpen.value = true
    }

    /** Elige una pestaña de la barra inferior. Ignorado mientras hay una gestión abierta (la barra está oculta). */
    fun selectTab(destination: MainDestination) {
        if (managementRoute.value != null) return
        selectedTab.value = destination
    }

    fun dismissSelector() {
        selectorOpen.value = false
    }

    fun selectFamily(familyId: String) {
        if (managementRoute.value != null) return
        val content = uiState.value.families as? FamiliesState.Content
        if (content != null && content.families.any { it.id == familyId }) activeFamilyId.value = familyId
        selectorOpen.value = false
    }

    /** "Gestionar" en Inicio (RF-01): abre el listado de la familia activa. Ignorado sin familia activa. */
    fun openCategories() = openRoute(CategoriesRoute.List)

    /** "Gestionar" en la sección de medios de pago de Inicio (RF-01 de 006). Ignorado sin familia activa. */
    fun openPaymentMethods() = openRoute(PaymentMethodsRoute)

    /** "+" en Inicio y en Movimientos (RF-04): abre "Nuevo movimiento" de la familia activa. */
    fun openMovementCreate() = openRoute(MovementRoute.Create)

    /** Tocar un movimiento del listado (RF-08). */
    fun openMovementDetail(movementId: String) = openRoute(MovementRoute.Detail(movementId))

    /** "Editar" en el detalle (RF-09); solo del movimiento cuyo detalle está abierto. */
    fun openMovementEdit(movementId: String) = openRoute(MovementRoute.Edit(movementId))

    fun openCategoryCreate(type: CategoryType) = openRoute(CategoriesRoute.Create(type))

    /** Tocar una categoría (RF-06) o, tras crearla, pasar a editar la recién creada (RF-09). */
    fun openCategoryEdit(categoryId: String) = openRoute(CategoriesRoute.Edit(categoryId))

    /**
     * Un nivel atrás: en categorías, edición o alta → listado → pestañas; en medios de pago, directamente
     * a las pestañas; en un movimiento, la edición vuelve a su detalle y el alta y el detalle a las pestañas
     * (la pestaña seleccionada no cambia). Sin gestión abierta no hace nada.
     */
    fun managementBack() {
        val open = managementRoute.value ?: return
        managementRoute.value = when (open.route) {
            CategoriesRoute.List, PaymentMethodsRoute -> null
            is CategoriesRoute.Create, is CategoriesRoute.Edit -> open.copy(route = CategoriesRoute.List)
            MovementRoute.Create, is MovementRoute.Detail -> null
            is MovementRoute.Edit -> open.copy(route = MovementRoute.Detail(open.route.movementId))
        }
    }

    /** Cierra la gestión abierta de una vez, sea cual sea su nivel (p. ej. de la edición de un movimiento al listado). */
    fun closeManagement() {
        managementRoute.value = null
    }

    private fun openRoute(route: ManagementRoute) {
        val content = uiState.value.families as? FamiliesState.Content ?: return
        // Desde las pestañas solo se abre un listado, el alta o el detalle de un movimiento; el alta y la edición de
        // categorías solo desde su gestión, la edición de un movimiento solo desde su detalle, y una gestión nunca
        // se abre sobre otra.
        val current = managementRoute.value?.route
        val allowed = when (route) {
            CategoriesRoute.List -> current == null || current is CategoriesRoute
            is CategoriesRoute.Create, is CategoriesRoute.Edit -> current is CategoriesRoute
            PaymentMethodsRoute -> current == null || current == PaymentMethodsRoute
            MovementRoute.Create, is MovementRoute.Detail -> current == null
            is MovementRoute.Edit -> current == MovementRoute.Detail(route.movementId)
        }
        if (!allowed) return
        // `uiState` se recalcula de forma asíncrona: la elección más reciente está en `activeFamilyId`.
        val active = content.families.firstOrNull { it.id == activeFamilyId.value } ?: content.activeFamily
        managementRoute.value = OpenRoute(active.id, route, nextRouteInstance++)
    }

    /** El backend rechazó una acción por rol: se vuelve a consultar el cargo sin cambiar la familia activa. */
    fun onInsufficientRole() {
        val previous = refreshJob
        refreshJob = viewModelScope.launch {
            previous?.join()
            runRefresh()
        }
    }

    private fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch { runRefresh() }
    }

    private suspend fun runRefresh() {
        refreshState.value = refreshState.value.copy(inFlight = true)
        val result = try {
            repository.refresh()
        } finally {
            refreshState.value = refreshState.value.copy(inFlight = false)
        }
        refreshState.value = refreshState.value.copy(lastResult = result)
    }

    private fun buildState(
        data: HeaderData,
        activeId: String?,
        selectorOpen: Boolean,
        refresh: RefreshState,
        route: OpenRoute?,
        tab: MainDestination,
    ): MainUiState {
        val families = data.families?.value
        val familiesState = when {
            families == null -> when {
                refresh.inFlight || refresh.lastResult == null -> FamiliesState.Loading
                refresh.lastResult.families != null -> FamiliesState.Error
                else -> FamiliesState.Loading
            }
            families.isEmpty() -> FamiliesState.NoData
            else -> {
                // Si la activa ya no existe, vuelve a la favorita (la primera).
                val active = families.firstOrNull { it.id == activeId } ?: families.first()
                FamiliesState.Content(families, active, data.isStale)
            }
        }
        return MainUiState(
            families = familiesState,
            pendingInvitations = data.pendingInvitations?.value,
            displayName = data.displayName?.value,
            selectorOpen = selectorOpen && familiesState is FamiliesState.Content && familiesState.hasSelector,
            isRefreshing = refresh.inFlight,
            selectedTab = tab,
            managementRoute = route?.route.takeIf {
                familiesState is FamiliesState.Content && familiesState.activeFamily.id == route?.familyId
            },
            managementRouteInstance = route?.instance ?: 0,
        )
    }

    private data class Navigation(val route: OpenRoute?, val tab: MainDestination)

    private data class OpenRoute(val familyId: String, val route: ManagementRoute, val instance: Int)

    private data class RefreshState(
        val inFlight: Boolean = false,
        val lastResult: HeaderRefreshResult? = null,
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
