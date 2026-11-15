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
    private val categoriesRoute = MutableStateFlow<OpenRoute?>(null)
    private var refreshJob: Job? = null

    val uiState: StateFlow<MainUiState> = combine(
        repository.header,
        activeFamilyId,
        selectorOpen,
        refreshState,
        categoriesRoute,
    ) { data, activeId, selectorOpen, refresh, route -> buildState(data, activeId, selectorOpen, refresh, route) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), MainUiState())

    init {
        refresh()
        viewModelScope.launch {
            // Si la familia con la que se abrió la gestión ya no existe (o se pierden los datos), se vuelve a las pestañas.
            repository.header.collect { data ->
                val ids = data.families?.value?.map { it.id }
                if (categoriesRoute.value?.let { ids == null || it.familyId !in ids } == true) {
                    categoriesRoute.value = null
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
        // La familia no puede cambiarse dentro de la gestión de categorías (RF-01).
        if (categoriesRoute.value != null) return
        if (uiState.value.families.let { it is FamiliesState.Content && it.hasSelector }) selectorOpen.value = true
    }

    fun dismissSelector() {
        selectorOpen.value = false
    }

    fun selectFamily(familyId: String) {
        if (categoriesRoute.value != null) return
        val content = uiState.value.families as? FamiliesState.Content
        if (content != null && content.families.any { it.id == familyId }) activeFamilyId.value = familyId
        selectorOpen.value = false
    }

    /** "Gestionar" en Inicio (RF-01): abre el listado de la familia activa. Ignorado sin familia activa. */
    fun openCategories() = openRoute(CategoriesRoute.List)

    fun openCategoryCreate(type: CategoryType) = openRoute(CategoriesRoute.Create(type))

    /** Tocar una categoría (RF-06) o, tras crearla, pasar a editar la recién creada (RF-09). */
    fun openCategoryEdit(categoryId: String) = openRoute(CategoriesRoute.Edit(categoryId))

    /** Un nivel atrás: edición o alta → listado → pestañas. Sin gestión abierta no hace nada. */
    fun categoriesBack() {
        val open = categoriesRoute.value ?: return
        categoriesRoute.value = when (open.route) {
            CategoriesRoute.List -> null
            is CategoriesRoute.Create, is CategoriesRoute.Edit -> open.copy(route = CategoriesRoute.List)
        }
    }

    private fun openRoute(route: CategoriesRoute) {
        val content = uiState.value.families as? FamiliesState.Content ?: return
        // Desde el listado se puede abrir alta o edición; desde las pestañas solo el listado.
        val current = categoriesRoute.value
        if (route != CategoriesRoute.List && current == null) return
        // `uiState` se recalcula de forma asíncrona: la elección más reciente está en `activeFamilyId`.
        val active = content.families.firstOrNull { it.id == activeFamilyId.value } ?: content.activeFamily
        categoriesRoute.value = OpenRoute(active.id, route)
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
            categoriesRoute = route?.route.takeIf {
                familiesState is FamiliesState.Content && familiesState.activeFamily.id == route?.familyId
            },
        )
    }

    private data class OpenRoute(val familyId: String, val route: CategoriesRoute)

    private data class RefreshState(
        val inFlight: Boolean = false,
        val lastResult: HeaderRefreshResult? = null,
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
