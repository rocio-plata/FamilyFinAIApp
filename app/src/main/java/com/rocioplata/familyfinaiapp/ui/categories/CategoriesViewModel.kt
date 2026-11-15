package com.rocioplata.familyfinaiapp.ui.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rocioplata.familyfinaiapp.data.network.ConnectivityObserver
import com.rocioplata.familyfinaiapp.domain.categories.CategoriesData
import com.rocioplata.familyfinaiapp.domain.categories.CategoriesRepository
import com.rocioplata.familyfinaiapp.domain.categories.Category
import com.rocioplata.familyfinaiapp.domain.categories.CategoryFailure
import com.rocioplata.familyfinaiapp.domain.categories.CategoryResult
import com.rocioplata.familyfinaiapp.domain.categories.CategoryStatus
import com.rocioplata.familyfinaiapp.domain.categories.CategoryType
import com.rocioplata.familyfinaiapp.domain.categories.TagStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.Normalizer
import java.util.Locale

/**
 * Estado del listado de categorías de una familia (RF-02 a RF-05, RF-20). El tipo visible, la búsqueda y
 * la sección plegada viven aquí: sobreviven a la recreación y a ir y volver del detalle. Se crea con
 * una clave `sesión-familia`, por lo que cambiar de familia o de usuario nunca reutiliza su estado.
 */
class CategoriesViewModel(
    private val repository: CategoriesRepository,
    private val familyId: String,
    connectivity: ConnectivityObserver,
    private val onInsufficientRole: () -> Unit = {},
) : ViewModel() {
    private val selectedType = MutableStateFlow(CategoryType.EXPENSE)
    private val query = MutableStateFlow("")
    private val archivedExpanded = MutableStateFlow(false)
    private val refreshState = MutableStateFlow(RefreshState())
    private val opState = MutableStateFlow(OpState())
    private var refreshJob: Job? = null

    private val data = repository.categories(familyId)

    // Para comprobar, al borrar, que la categoría sigue estando archivada.
    private val latest: StateFlow<List<Category>?> = data.map { it.categories }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val uiState: StateFlow<CategoriesUiState> = combine(
        data,
        selectedType,
        query,
        archivedExpanded,
        combine(refreshState, opState) { refresh, op -> refresh to op },
    ) { data, type, query, expanded, (refresh, op) -> buildState(data, type, query, expanded, refresh, op) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CategoriesUiState.Loading)

    init {
        refresh()
        viewModelScope.launch {
            connectivity.isOnline.drop(1).filter { it }.collect { refresh() }
        }
    }

    /** Al volver a primer plano. Ignorado si ya hay una carga en curso. */
    fun onStart() = refresh()

    fun retry() = refresh()

    fun selectType(type: CategoryType) {
        selectedType.value = type
    }

    fun setQuery(text: String) {
        query.value = text
    }

    fun toggleArchived() {
        archivedExpanded.value = !archivedExpanded.value
    }

    /**
     * Eliminar una categoría archivada, ya confirmado por la persona (RF-13). Con movimientos asociados solo
     * se informa: archivada no admite la oferta de archivar. Una operación a la vez (CA-21).
     */
    fun deleteArchived(categoryId: String) {
        val category = latest.value?.firstOrNull { it.id == categoryId } ?: return
        if (category.status != CategoryStatus.DEPRECATED) return
        // El control síncrono evita que dos pulsaciones seguidas envíen dos operaciones.
        if (opState.getAndUpdate { it.copy(busy = true) }.busy) return
        viewModelScope.launch {
            try {
                when (val result = repository.deleteCategory(familyId, categoryId)) {
                    is CategoryResult.Success -> Unit
                    is CategoryResult.Failure -> handle(result.failure)
                }
            } finally {
                opState.value = opState.value.copy(busy = false)
            }
        }
    }

    /** La pantalla ya mostró o gestionó el evento pendiente. */
    fun consumeEvent() {
        opState.value = opState.value.copy(event = null)
    }

    private fun handle(failure: CategoryFailure) {
        when (failure) {
            CategoryFailure.HasAssociatedItems -> setEvent(CategoriesEvent.CannotDelete)
            CategoryFailure.InsufficientRole -> {
                setEvent(CategoriesEvent.Failed(failure))
                onInsufficientRole()
            }
            CategoryFailure.NotFound -> {
                setEvent(CategoriesEvent.Failed(failure))
                refresh()
            }
            // Lo resuelve la sesión (003): no hay mensaje propio.
            CategoryFailure.Unauthorized -> Unit
            else -> setEvent(CategoriesEvent.Failed(failure))
        }
    }

    private fun setEvent(event: CategoriesEvent) {
        opState.value = opState.value.copy(event = event)
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
        data: CategoriesData,
        type: CategoryType,
        query: String,
        archivedExpanded: Boolean,
        refresh: RefreshState,
        op: OpState,
    ): CategoriesUiState {
        val categories = data.categories ?: return when {
            refresh.inFlight || refresh.lastResult == null -> CategoriesUiState.Loading
            refresh.lastResult is CategoryResult.Failure -> CategoriesUiState.Error
            else -> CategoriesUiState.Loading
        }
        val ofType = categories.filter { it.type == type }
        val needle = normalize(query)
        val visible = if (needle.isEmpty()) ofType else ofType.filter { it.matches(needle) }
        return CategoriesUiState.Content(
            selectedType = type,
            expenseActiveCount = categories.countActive(CategoryType.EXPENSE),
            incomeActiveCount = categories.countActive(CategoryType.INCOME),
            query = query,
            active = visible.filter { it.status == CategoryStatus.ACTIVE }.map { it.toItem() },
            archived = visible.filter { it.status == CategoryStatus.DEPRECATED }.map { it.toItem() },
            archivedExpanded = archivedExpanded,
            isEmpty = ofType.isEmpty(),
            noResults = ofType.isNotEmpty() && visible.isEmpty(),
            isStale = data.isStale,
            isRefreshing = refresh.inFlight,
            isBusy = op.busy,
            event = op.event,
        )
    }

    private fun List<Category>.countActive(type: CategoryType) =
        count { it.type == type && it.status == CategoryStatus.ACTIVE }

    private fun Category.toItem() = CategoryItem(
        category = this,
        activeTags = tags.filter { it.status == TagStatus.ACTIVE }.sortedBy { it.displayOrder },
    )

    /**
     * Coincide por el nombre de la categoría o por el de algún tag. En una categoría activa se buscan
     * los tags que se ven (los activos); en una archivada, todos, porque su fila no los muestra.
     */
    private fun Category.matches(needle: String): Boolean {
        if (normalize(name).contains(needle)) return true
        val searchable = if (status == CategoryStatus.ACTIVE) tags.filter { it.status == TagStatus.ACTIVE } else tags
        return searchable.any { normalize(it.name).contains(needle) }
    }

    private data class OpState(val busy: Boolean = false, val event: CategoriesEvent? = null)

    private data class RefreshState(
        val inFlight: Boolean = false,
        val lastResult: CategoryResult<Unit>? = null,
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        val DIACRITICS = Regex("\\p{Mn}+")

        /** Sin mayúsculas, acentos ni espacios en los extremos. */
        fun normalize(text: String): String =
            DIACRITICS.replace(Normalizer.normalize(text.trim(), Normalizer.Form.NFD), "").lowercase(Locale.ROOT)
    }
}
