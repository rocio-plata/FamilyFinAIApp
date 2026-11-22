package com.rocioplata.familyfinaiapp.ui.dashboard

import com.rocioplata.familyfinaiapp.domain.dashboard.CategoryBreakdown
import com.rocioplata.familyfinaiapp.domain.dashboard.MonthComparison
import com.rocioplata.familyfinaiapp.domain.dashboard.MonthTotals
import com.rocioplata.familyfinaiapp.domain.dashboard.PaymentMethodBreakdown
import com.rocioplata.familyfinaiapp.domain.movements.MovementType
import java.math.BigDecimal
import java.time.YearMonth

sealed interface DashboardContentState {
    /** Mes sin datos todavía (ni caché ni respuesta): primera carga. */
    data object Loading : DashboardContentState

    /**
     * Mes sin datos y el último refresco falló, o los datos no se pueden usar (monedas mezcladas, o mes vacío y
     * moneda de la familia desconocida): error con «Reintentar» (CA-16).
     */
    data object Error : DashboardContentState

    data class Content(
        /** Ingresos, gastos y disponible del mes, con todos sus movimientos (RF-02). */
        val totals: MonthTotals,
        /** Porcentaje de la tarjeta activa y del centro del gráfico; nulo si el mes no tiene ingresos (RF-05). */
        val percentOfIncome: BigDecimal?,
        /**
         * Reparto por categoría del tipo activo (RF-04, RF-07 a RF-09). Nulo mientras las categorías de la familia no
         * tengan datos (cargando o fallo): los totales se ven igual y la sección por categoría espera.
         */
        val breakdown: CategoryBreakdown?,
        /** Variación frente al mes anterior; nula si no hay base o datos de ese mes (el indicador se oculta, RF-06). */
        val comparison: MonthComparison?,
        /** El mes no tiene ningún movimiento (RF-08, CA-15). */
        val isMonthEmpty: Boolean,
        /** La última descarga completa del mes tiene más de 24 h (CA-16). */
        val isStale: Boolean,
        val isRefreshing: Boolean,
        /**
         * Reparto por medio de pago del tipo activo (RF-16 a RF-18). Nulo mientras los medios de pago de la familia no
         * tengan datos (cargando o fallo): los totales se ven igual y la sección espera.
         */
        val paymentMethods: PaymentMethodBreakdown? = null,
    ) : DashboardContentState
}

/**
 * Estado del dashboard de Inicio. El selector de mes y el tipo activo se muestran también al cargar o fallar, para
 * poder cambiar de mes (RF-01, RF-03).
 */
data class DashboardUiState(
    val month: YearMonth,
    /** No se pueden elegir meses futuros (RF-01). */
    val canGoNext: Boolean,
    /** El mes mostrado es el mes en curso: «Mes actual» no hace falta. */
    val isCurrentMonth: Boolean,
    /** Tipo activo: gastos de entrada (RF-03). */
    val activeType: MovementType,
    val content: DashboardContentState,
)
