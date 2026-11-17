package com.rocioplata.familyfinaiapp.ui.movements

import com.rocioplata.familyfinaiapp.domain.money.Money
import com.rocioplata.familyfinaiapp.domain.movements.MovementType
import java.time.LocalDate
import java.time.YearMonth

/** Filtro por tipo del listado (RF-03): todos de entrada; se mantiene al cambiar de mes. */
enum class MovementFilter {
    ALL,
    EXPENSES,
    INCOMES,
    ;

    fun accepts(type: MovementType): Boolean = when (this) {
        ALL -> true
        EXPENSES -> type == MovementType.EXPENSE
        INCOMES -> type == MovementType.INCOME
    }
}

/** Una fila del listado con todo resuelto; si falta el nombre de algo (borrado del catálogo) queda nulo (RF-02). */
data class MovementRow(
    val id: String,
    val type: MovementType,
    val title: String,
    val amount: Money,
    val categoryName: String?,
    /** Clave del icono de la categoría; nula si no se encontró la categoría o no tiene icono. */
    val categoryIcon: String?,
    val tagName: String?,
    val paymentMethodName: String?,
    /** El listado omite la fila de quién registró si es [MemberLabel.Unavailable] (RF-15). */
    val recordedBy: MemberLabel,
)

/** Movimientos de un día con el subtotal de los visibles con el filtro (ingresos menos gastos, RF-17). */
data class DayGroup(
    val date: LocalDate,
    val subtotal: Money,
    val rows: List<MovementRow>,
)

/** Resumen del mes con todos sus movimientos, sin importar el filtro (RF-17). */
data class MonthSummary(
    val income: Money,
    val expenses: Money,
    /** Ingresos menos gastos; puede ser negativo. */
    val balance: Money,
)

sealed interface MovementsListState {
    data object Loading : MovementsListState

    /** Mes sin datos (ni caché ni backend) y el último refresco falló, o los datos no son utilizables. */
    data object Error : MovementsListState

    data class Content(
        /** Nulo solo si el mes no tiene movimientos y no se conoce la moneda de la familia. */
        val summary: MonthSummary?,
        val groups: List<DayGroup>,
        /** El mes no tiene ningún movimiento. */
        val isMonthEmpty: Boolean,
        /** Hay movimientos en el mes pero el filtro los oculta todos. */
        val isFilterEmpty: Boolean,
        /** La última descarga completa del mes tiene más de 24 h (RF-13). */
        val isStale: Boolean,
        val isRefreshing: Boolean,
    ) : MovementsListState
}

/** El selector de mes y el filtro se muestran también al cargar o fallar, para poder cambiar de mes. */
data class MovementsUiState(
    val month: YearMonth,
    /** No se pueden elegir meses futuros (RF-01). */
    val canGoNext: Boolean,
    val filter: MovementFilter,
    val list: MovementsListState,
)
