package com.rocioplata.familyfinaiapp.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rocioplata.familyfinaiapp.data.network.ConnectivityObserver
import com.rocioplata.familyfinaiapp.domain.categories.CategoryType
import com.rocioplata.familyfinaiapp.domain.family.FamilyAccessFailure
import com.rocioplata.familyfinaiapp.domain.family.FamilyAccessResult
import com.rocioplata.familyfinaiapp.domain.family.FamilyManagementRepository
import com.rocioplata.familyfinaiapp.domain.header.Family
import com.rocioplata.familyfinaiapp.domain.header.HeaderData
import com.rocioplata.familyfinaiapp.domain.header.HeaderRefreshResult
import com.rocioplata.familyfinaiapp.domain.header.HeaderRepository
import com.rocioplata.familyfinaiapp.domain.movements.MovementType
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
    private val familyManagement: FamilyManagementRepository,
) : ViewModel() {
    // Solo en memoria: tras terminar el proceso vuelve a la favorita (RF-03).
    private val activeFamilyId = MutableStateFlow<String?>(null)
    private val selectorOpen = MutableStateFlow(false)

    // Orden de las familias mientras se guarda (RF-02): se ve al soltar y se descarta si el backend lo rechaza.
    private val pendingOrder = MutableStateFlow<List<String>?>(null)
    private val selectorError = MutableStateFlow<FamilyAccessFailure?>(null)
    private val refreshState = MutableStateFlow(RefreshState())

    // Ligada a la familia con la que se abrió (salvo crear familia e invitaciones, que no dependen de ninguna): si esa
    // familia deja de ser la activa, se descarta.
    private val managementRoute = MutableStateFlow<OpenRoute?>(null)
    private val notice = MutableStateFlow<MainNotice?>(null)

    // Solo en memoria: tras terminar el proceso se vuelve a Inicio (RF-08 de la 001).
    private val selectedTab = MutableStateFlow(MainDestination.Home)
    private var refreshJob: Job? = null
    private var nextRouteInstance = 1

    // Familia efectiva vista por última vez y cuántas veces cambió: ver `MainUiState.activeFamilyInstance`.
    private var lastActiveFamilyId: String? = null
    private var activeFamilyInstance = 0

    val uiState: StateFlow<MainUiState> = combine(
        repository.header,
        activeFamilyId,
        combine(selectorOpen, pendingOrder, selectorError, ::Selector),
        refreshState,
        combine(managementRoute, selectedTab, notice, ::Navigation),
    ) { data, activeId, selector, refresh, navigation ->
        buildState(data, activeId, selector, refresh, navigation.route, navigation.tab, navigation.notice)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), MainUiState())

    init {
        refresh()
        viewModelScope.launch {
            // Si la familia con la que se abrió la gestión ya no existe (o se pierden los datos), se vuelve a las pestañas.
            repository.header.collect { data ->
                val ids = data.families?.value?.map { it.id }
                val open = managementRoute.value ?: return@collect
                val familyId = open.familyId ?: return@collect
                if (ids != null && familyId in ids) return@collect
                managementRoute.value = null
                // Con la lista a la vista, la familia ya no es del usuario: se le avisa (RF-22). Al salir de «Mi familia»
                // se vuelve a Inicio con la favorita activa (RF-12).
                if (ids != null) {
                    notice.value = MainNotice.NoLongerMember(open.familyName.orEmpty())
                    if (open.route == FamilyRoute.Manage) selectedTab.value = MainDestination.Home
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
        // La familia no puede cambiarse dentro de una gestión (RF-01). El selector se abre también con una sola familia:
        // desde él se crea otra y se gestiona la activa (RF-01 de la 009).
        if (managementRoute.value != null) return
        if (uiState.value.families is FamiliesState.Content) selectorOpen.value = true
    }

    /** Elige una pestaña de la barra inferior. Ignorado mientras hay una gestión abierta (la barra está oculta). */
    fun selectTab(destination: MainDestination) {
        if (managementRoute.value != null) return
        selectedTab.value = destination
    }

    fun dismissSelector() = closeSelector()

    fun selectFamily(familyId: String) {
        if (managementRoute.value != null) return
        val content = uiState.value.families as? FamiliesState.Content
        if (content != null && content.families.any { it.id == familyId }) activeFamilyId.value = familyId
        closeSelector()
    }

    /**
     * Mueve una familia a la posición [targetIndex] de la lista (RF-02): arrastrando al soltar o con subir/bajar. El
     * orden se ve enseguida y se guarda; si no se puede, vuelve el anterior y [MainUiState.selectorError] dice por qué.
     * La primera de la lista es la favorita, la que se abre al iniciar. Una sola operación a la vez (CA-30).
     */
    fun moveFamily(familyId: String, targetIndex: Int) {
        if (pendingOrder.value != null) return
        val content = uiState.value.families as? FamiliesState.Content ?: return
        val ids = content.families.map { it.id }
        val from = ids.indexOf(familyId)
        if (from < 0) return
        val to = targetIndex.coerceIn(0, ids.lastIndex)
        if (to == from) return
        val reordered = ids.toMutableList().apply { add(to, removeAt(from)) }
        // Sin elección previa la activa es «la primera»: se fija antes de mover para que reordenar no la cambie.
        if (activeFamilyId.value == null) activeFamilyId.value = content.activeFamily.id
        selectorError.value = null
        pendingOrder.value = reordered
        viewModelScope.launch {
            try {
                val result = familyManagement.reorderFamilies(reordered)
                if (result is FamilyAccessResult.Failure) {
                    selectorError.value = result.failure
                    // Las familias del usuario cambiaron por otro lado: se vuelven a consultar.
                    if (result.failure == FamilyAccessFailure.InvalidOrder) refresh()
                }
            } finally {
                pendingOrder.value = null
            }
        }
    }

    private fun closeSelector() {
        selectorOpen.value = false
        selectorError.value = null
    }

    /** "Gestionar" en Inicio (RF-01): abre el listado de la familia activa. Ignorado sin familia activa. */
    fun openCategories() = openRoute(CategoriesRoute.List)

    /** "Gestionar" en la sección de medios de pago de Inicio (RF-01 de 006). Ignorado sin familia activa. */
    fun openPaymentMethods() = openRoute(PaymentMethodsRoute)

    /**
     * "+" en Inicio y en Movimientos (RF-04): abre "Nuevo movimiento" de la familia activa. Una tarjeta de categoría del
     * dashboard lo abre con [type] y [categoryId] ya elegidos (RF-11 de la 008); sin ellos es el formulario vacío.
     */
    fun openMovementCreate(categoryId: String? = null, type: MovementType? = null) =
        openRoute(MovementRoute.Create(categoryId, type))

    /** Tocar un movimiento del listado (RF-08). */
    fun openMovementDetail(movementId: String) = openRoute(MovementRoute.Detail(movementId))

    /** "Editar" en el detalle (RF-09); solo del movimiento cuyo detalle está abierto. */
    fun openMovementEdit(movementId: String) = openRoute(MovementRoute.Edit(movementId))

    /** El icono de gestión de la familia activa en el selector (RF-03): abre «Mi familia». */
    fun openFamilyManagement() = openRoute(FamilyRoute.Manage)

    /** «Crear nueva familia» en el selector o sin familias (RF-04). */
    fun openFamilyCreate() = openRoute(FamilyRoute.Create)

    /** La campana de la cabecera o «Ver invitaciones» sin familias (RF-18). */
    fun openInvitations() = openRoute(InvitationsRoute)

    /**
     * La familia [family] se creó con éxito (RF-05): pasa a ser la activa, se vuelve a Inicio y se abre su «Mi familia»;
     * atrás desde ella lleva a Inicio. Solo tiene efecto con el formulario de alta abierto.
     */
    fun onFamilyCreated(family: Family) {
        if (managementRoute.value?.route != FamilyRoute.Create) return
        activeFamilyId.value = family.id
        selectedTab.value = MainDestination.Home
        closeSelector()
        managementRoute.value = OpenRoute(family.id, family.name, FamilyRoute.Manage, nextRouteInstance++)
    }

    /**
     * «Cambiar a esta familia» tras aceptar una invitación (RF-19): cierra la gestión abierta, activa la familia y vuelve
     * a Inicio. Si la familia no está en la lista solo se cierra la gestión.
     */
    fun switchToFamily(familyId: String) {
        managementRoute.value = null
        val content = uiState.value.families as? FamiliesState.Content ?: return
        if (content.families.none { it.id == familyId }) return
        activeFamilyId.value = familyId
        selectedTab.value = MainDestination.Home
        closeSelector()
    }

    fun consumeNotice() {
        notice.value = null
    }

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
            FamilyRoute.Manage, FamilyRoute.Create, InvitationsRoute -> null
            is CategoriesRoute.Create, is CategoriesRoute.Edit -> open.copy(route = CategoriesRoute.List)
            is MovementRoute.Create, is MovementRoute.Detail -> null
            is MovementRoute.Edit -> open.copy(route = MovementRoute.Detail(open.route.movementId))
        }
    }

    /** Cierra la gestión abierta de una vez, sea cual sea su nivel (p. ej. de la edición de un movimiento al listado). */
    fun closeManagement() {
        managementRoute.value = null
    }

    private fun openRoute(route: ManagementRoute) {
        // Crear familia e invitaciones no dependen de ninguna familia; el resto sí y necesitan una activa.
        val boundToFamily = route != FamilyRoute.Create && route != InvitationsRoute
        val content = uiState.value.families as? FamiliesState.Content
        if (boundToFamily && content == null) return
        // Desde las pestañas solo se abre un listado, el alta o el detalle de un movimiento; el alta y la edición de
        // categorías solo desde su gestión, la edición de un movimiento solo desde su detalle, y una gestión nunca
        // se abre sobre otra.
        val current = managementRoute.value?.route
        val allowed = when (route) {
            CategoriesRoute.List -> current == null || current is CategoriesRoute
            is CategoriesRoute.Create, is CategoriesRoute.Edit -> current is CategoriesRoute
            PaymentMethodsRoute -> current == null || current == PaymentMethodsRoute
            is MovementRoute.Create, is MovementRoute.Detail -> current == null
            is MovementRoute.Edit -> current == MovementRoute.Detail(route.movementId)
            FamilyRoute.Manage, FamilyRoute.Create, InvitationsRoute -> current == null
        }
        if (!allowed) return
        // `uiState` se recalcula de forma asíncrona: la elección más reciente está en `activeFamilyId`.
        val active = content?.let { c -> c.families.firstOrNull { it.id == activeFamilyId.value } ?: c.activeFamily }
        // Abrir una gestión desde el selector lo cierra.
        closeSelector()
        managementRoute.value = if (boundToFamily) {
            OpenRoute(active?.id, active?.name, route, nextRouteInstance++)
        } else {
            OpenRoute(null, null, route, nextRouteInstance++)
        }
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
        selector: Selector,
        refresh: RefreshState,
        route: OpenRoute?,
        tab: MainDestination,
        notice: MainNotice?,
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
                // Mientras se guarda un reordenado se muestra el orden nuevo; la activa se decide con el de la cabecera.
                val shown = selector.pendingOrder?.let { order ->
                    order.mapNotNull { id -> families.firstOrNull { it.id == id } } + families.filter { it.id !in order }
                } ?: families
                FamiliesState.Content(shown, active, data.isStale)
            }
        }
        // Solo cuenta con una familia activa visible: mientras la cabecera carga o falla no se pierde la cuenta.
        (familiesState as? FamiliesState.Content)?.activeFamily?.id?.let { id ->
            if (lastActiveFamilyId != null && id != lastActiveFamilyId) activeFamilyInstance++
            lastActiveFamilyId = id
        }
        return MainUiState(
            families = familiesState,
            pendingInvitations = data.pendingInvitations?.value,
            displayName = data.displayName?.value,
            selectorOpen = selector.open && familiesState is FamiliesState.Content,
            selectorError = selector.error,
            isReordering = selector.pendingOrder != null,
            isRefreshing = refresh.inFlight,
            selectedTab = tab,
            managementRoute = route?.takeIf {
                it.familyId == null || (familiesState is FamiliesState.Content && familiesState.activeFamily.id == it.familyId)
            }?.route,
            managementRouteInstance = route?.instance ?: 0,
            notice = notice,
            activeFamilyInstance = activeFamilyInstance,
        )
    }

    private data class Selector(val open: Boolean, val pendingOrder: List<String>?, val error: FamilyAccessFailure?)

    private data class Navigation(val route: OpenRoute?, val tab: MainDestination, val notice: MainNotice?)

    /** [familyId] y [familyName] son nulos en las rutas que no dependen de una familia. */
    private data class OpenRoute(val familyId: String?, val familyName: String?, val route: ManagementRoute, val instance: Int)

    private data class RefreshState(
        val inFlight: Boolean = false,
        val lastResult: HeaderRefreshResult? = null,
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
