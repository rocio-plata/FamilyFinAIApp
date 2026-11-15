package com.rocioplata.familyfinaiapp.ui.categories

import com.rocioplata.familyfinaiapp.domain.categories.Category
import com.rocioplata.familyfinaiapp.domain.categories.CategoryFailure
import com.rocioplata.familyfinaiapp.domain.categories.CategoryType
import com.rocioplata.familyfinaiapp.domain.categories.Tag

/** Una fila del listado: la categoría y los tags activos que se muestran, ya en su orden (RF-03). */
data class CategoryItem(
    val category: Category,
    val activeTags: List<Tag>,
)

/** Algo que el listado debe mostrar una sola vez y luego confirmar con `consumeEvent()`. */
sealed interface CategoriesEvent {
    /** Un cambio falló: aviso, sin cambiar nada (RF-21). */
    data class Failed(val failure: CategoryFailure) : CategoriesEvent

    /** Eliminar una categoría archivada con movimientos asociados: solo se informa (RF-13). */
    data object CannotDelete : CategoriesEvent
}

sealed interface CategoriesUiState {
    /** Sin datos todavía (ni caché ni respuesta): primera carga. */
    data object Loading : CategoriesUiState

    /** Sin datos y el refresco falló: error con «Reintentar». */
    data object Error : CategoriesUiState

    data class Content(
        val selectedType: CategoryType,
        /** Categorías activas por tipo, sin tener en cuenta la búsqueda (RF-02). */
        val expenseActiveCount: Int,
        val incomeActiveCount: Int,
        val query: String,
        /** Activas del tipo mostrado, filtradas por la búsqueda. */
        val active: List<CategoryItem>,
        /** Archivadas del tipo mostrado, filtradas por la búsqueda (RF-04). */
        val archived: List<CategoryItem>,
        val archivedExpanded: Boolean,
        /** El tipo mostrado no tiene ninguna categoría, activa ni archivada. */
        val isEmpty: Boolean,
        /** Hay búsqueda y ninguna categoría del tipo coincide. */
        val noResults: Boolean,
        /** Los datos tienen más de 24 h (RF-20). */
        val isStale: Boolean,
        val isRefreshing: Boolean,
        /** Hay una operación de cambio en curso: las acciones quedan deshabilitadas (CA-21). */
        val isBusy: Boolean = false,
        val event: CategoriesEvent? = null,
    ) : CategoriesUiState {
        val selectedActiveCount: Int
            get() = if (selectedType == CategoryType.EXPENSE) expenseActiveCount else incomeActiveCount
    }
}
