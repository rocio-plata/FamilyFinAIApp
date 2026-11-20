package com.rocioplata.familyfinaiapp.domain.dashboard

import com.rocioplata.familyfinaiapp.domain.categories.Category
import com.rocioplata.familyfinaiapp.domain.categories.CategoryStatus
import com.rocioplata.familyfinaiapp.domain.money.Money
import java.math.BigDecimal

/** Lo que una categoría suma en el mes mostrado, para su tarjeta de la cuadrícula (RF-07). */
data class CategoryAmount(
    val category: Category,
    /** Suma de los movimientos del mes del tipo activo con esta categoría; cero si no tiene ninguno (RF-08). */
    val amount: Money,
    /**
     * Posición de la categoría entre las de su tipo en la lista del repositorio (la de la gestión), contando las
     * archivadas: es la que usa el color de respaldo (RF-14), la misma que en la gestión de categorías.
     */
    val colorPosition: Int,
) {
    val isArchived: Boolean get() = category.status == CategoryStatus.DEPRECATED
    val hasAmount: Boolean get() = amount.amountMinor > 0
}

/** Un segmento del gráfico circular: una categoría con monto y la parte del total del tipo que representa (RF-04). */
data class ChartSegment(
    val entry: CategoryAmount,
    /** Monto de la categoría entre el total del tipo (0 a 1), con diez decimales; solo sirve para dibujar. */
    val share: BigDecimal,
)

/**
 * Reparto del mes por categoría para el tipo activo (RF-04, RF-07 a RF-09): lo que se muestra en la cuadrícula y en
 * el gráfico circular.
 */
data class CategoryBreakdown(
    /** Total del tipo activo en el mes, con todos los movimientos, también los de categorías que no están en la lista. */
    val total: Money,
    /** Tarjetas de la cuadrícula, ya en su orden (RF-09). */
    val entries: List<CategoryAmount>,
    /** Segmentos del gráfico: las tarjetas con monto, en el mismo orden. Vacío si el total es cero. */
    val segments: List<ChartSegment>,
) {
    /** Cuántas categorías se muestran (el contador de la sección, RF-07). */
    val count: Int get() = entries.size
}
