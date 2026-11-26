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
import com.rocioplata.familyfinaiapp.domain.categories.ReactivationResult
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.Normalizer
import java.time.YearMonth
import java.time.ZoneId
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
    /** Zona en la que se lee el mes de archivado (RF-09 de la 010). */
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val onInsufficientRole: () -> Unit = {},
) : ViewModel() {
    private val selectedType = MutableStateFlow(CategoryType.EXPENSE)
    private val query = MutableStateFlow("")
    private val archivedExpanded = MutableStateFlow(false)
    private val refreshState = MutableStateFlow(RefreshState())
    private val opState = MutableStateFlow(OpState())
    private val reorderState = MutableStateFlow(ReorderState())
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
        combine(refreshState, opState, reorderState, ::Ops),
    ) { data, type, query, expanded, ops -> buildState(data, type, query, expanded, ops) }
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
        // Con texto la lista está filtrada y su orden sería parcial (RF-10 de la 010).
        if (text.isNotEmpty()) reorderState.update { it.copy(active = false) }
    }

    /**
     * Abre el modo de orden de las activas del tipo visible (RF-10 de la 010). Se ignora con texto en el buscador, con
     * menos de dos activas o durante otra operación.
     */
    fun startReorder() {
        if (query.value.isNotEmpty() || opState.value.busy) return
        if (activeIds(selectedType.value).size < 2) return
        reorderState.update { it.copy(active = true) }
    }

    fun finishReorder() {
        reorderState.update { it.copy(active = false) }
    }

    /** Sube una categoría activa una posición: la alternativa sin arrastre, utilizable con lector de pantalla. */
    fun moveCategoryUp(categoryId: String) {
        val index = activeIds(selectedType.value).indexOf(categoryId)
        if (index > 0) moveCategory(categoryId, index - 1)
    }

    fun moveCategoryDown(categoryId: String) {
        val ids = activeIds(selectedType.value)
        val index = ids.indexOf(categoryId)
        if (index in 0 until ids.lastIndex) moveCategory(categoryId, index + 1)
    }

    /**
     * Mueve una categoría activa a [toIndex] entre las activas del tipo visible y guarda el orden al momento (RF-10 de la
     * 010). Se muestra enseguida (cambio optimista); si falla se restaura el anterior y, salvo sin conexión, se avisa y se
     * actualiza la lista por si cambió en otro dispositivo (RF-12).
     */
    fun moveCategory(categoryId: String, toIndex: Int) {
        if (!reorderState.value.active) return
        val type = selectedType.value
        val ids = activeIds(type)
        val from = ids.indexOf(categoryId)
        if (from < 0) return
        val target = toIndex.coerceIn(0, ids.lastIndex)
        if (target == from) return
        if (opState.getAndUpdate { it.copy(busy = true) }.busy) return
        val newOrder = ids.toMutableList().apply {
            removeAt(from)
            add(target, categoryId)
        }
        reorderState.update { it.copy(pending = type to newOrder) }
        viewModelScope.launch {
            try {
                val result = repository.reorderCategories(familyId, type, newOrder)
                if (result is CategoryResult.Failure) handleReorderFailure(result.failure)
            } finally {
                // Con éxito ya lo muestra el repositorio; con fallo o cancelación vuelve el orden anterior.
                reorderState.update { it.copy(pending = null) }
                opState.value = opState.value.copy(busy = false)
            }
        }
    }

    private fun handleReorderFailure(failure: CategoryFailure) {
        when (failure) {
            CategoryFailure.Offline,
            CategoryFailure.InsufficientRole,
            CategoryFailure.NotFound,
            CategoryFailure.Unauthorized,
            -> handle(failure)
            // Incluye el rechazo del orden (INVALID_CATEGORY_ORDER), que no se distingue.
            else -> {
                setEvent(CategoriesEvent.ReorderFailed)
                refresh()
            }
        }
    }

    /** Activas de [type] en su orden guardado (el del repositorio). */
    private fun activeIds(type: CategoryType): List<String> =
        latest.value.orEmpty().filter { it.type == type && it.status == CategoryStatus.ACTIVE }.map { it.id }

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

    /**
     * Reactivar una categoría archivada, ya confirmado por la persona (RF-01 de la 010). Queda como la última activa de
     * su tipo; si eso no se pudo guardar, se avisa y se actualiza la lista (RF-11). Una operación a la vez (RF-06).
     */
    fun reactivate(categoryId: String) {
        val category = latest.value?.firstOrNull { it.id == categoryId } ?: return
        if (category.status != CategoryStatus.DEPRECATED) return
        if (opState.getAndUpdate { it.copy(busy = true) }.busy) return
        viewModelScope.launch {
            try {
                when (val result = repository.reactivateCategory(familyId, categoryId)) {
                    ReactivationResult.Success -> setEvent(CategoriesEvent.Reactivated)
                    is ReactivationResult.Partial -> {
                        setEvent(CategoriesEvent.ReactivatedNotMoved)
                        if (result.orderFailure == CategoryFailure.InsufficientRole) onInsufficientRole()
                        refresh()
                    }
                    is ReactivationResult.Failure -> handle(result.failure)
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
        ops: Ops,
    ): CategoriesUiState {
        val (refresh, op, reorder) = ops
        val categories = data.categories ?: return when {
            refresh.inFlight || refresh.lastResult == null -> CategoriesUiState.Loading
            refresh.lastResult is CategoryResult.Failure -> CategoriesUiState.Error
            else -> CategoriesUiState.Loading
        }
        val ofType = categories.filter { it.type == type }
        val needle = normalize(query)
        val visible = if (needle.isEmpty()) ofType else ofType.filter { it.matches(needle) }
        val active = visible.filter { it.status == CategoryStatus.ACTIVE }.inPendingOrder(reorder.pending, type)
        return CategoriesUiState.Content(
            selectedType = type,
            expenseActiveCount = categories.countActive(CategoryType.EXPENSE),
            incomeActiveCount = categories.countActive(CategoryType.INCOME),
            query = query,
            active = active.map { it.toItem(categories) },
            archived = visible.filter { it.status == CategoryStatus.DEPRECATED }.map { it.toItem(categories) },
            archivedExpanded = archivedExpanded,
            isEmpty = ofType.isEmpty(),
            noResults = ofType.isNotEmpty() && visible.isEmpty(),
            isStale = data.isStale,
            isRefreshing = refresh.inFlight,
            isBusy = op.busy,
            event = op.event,
            reorderMode = reorder.active,
            canReorder = needle.isEmpty() && active.size > 1 && !op.busy,
        )
    }

    /** Las activas en el orden que se está guardando, si hay uno para este tipo; las que no estén en él, al final. */
    private fun List<Category>.inPendingOrder(pending: Pair<CategoryType, List<String>>?, type: CategoryType): List<Category> {
        val ids = pending?.takeIf { it.first == type }?.second ?: return this
        val byId = associateBy { it.id }
        return ids.mapNotNull { byId[it] } + filter { it.id !in ids }
    }

    private fun List<Category>.countActive(type: CategoryType) =
        count { it.type == type && it.status == CategoryStatus.ACTIVE }

    // El color de respaldo depende de la posición entre las categorías de su tipo, así que usa la lista completa.
    private fun Category.toItem(all: List<Category>) = CategoryItem(
        category = this,
        activeTags = tags.filter { it.status == TagStatus.ACTIVE }.sortedBy { it.displayOrder },
        color = CategoryColors.resolve(this, all),
        archivedAt = deprecatedAt?.takeIf { status == CategoryStatus.DEPRECATED }?.let { YearMonth.from(it.atZone(zone)) },
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

    /** Modo de orden abierto y, mientras se guarda, el orden mostrado de un tipo (RF-10 de la 010). */
    private data class ReorderState(
        val active: Boolean = false,
        val pending: Pair<CategoryType, List<String>>? = null,
    )

    private data class Ops(val refresh: RefreshState, val op: OpState, val reorder: ReorderState)

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
