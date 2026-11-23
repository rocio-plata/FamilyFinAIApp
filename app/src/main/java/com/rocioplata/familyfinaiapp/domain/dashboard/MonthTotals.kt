package com.rocioplata.familyfinaiapp.domain.dashboard

import com.rocioplata.familyfinaiapp.domain.money.Money
import com.rocioplata.familyfinaiapp.domain.movements.MovementType
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Totales de un mes de la familia (RF-02): lo gastado y lo ingresado, ambos en la misma moneda, y lo que queda
 * disponible. Se calculan con todos los movimientos del mes, también los de categorías que ya no se muestran
 * (RF-13).
 */
data class MonthTotals(
    val income: Money,
    val expenses: Money,
) {
    /** Ingresos menos gastos; puede ser negativo (RF-02). */
    val available: Money get() = income - expenses

    /** Total del tipo [type]. */
    fun totalOf(type: MovementType): Money = when (type) {
        MovementType.EXPENSE -> expenses
        MovementType.INCOME -> income
    }

    /**
     * Porcentaje de la tarjeta activa y del centro del gráfico (RF-05): en gastos, los gastos como porcentaje de
     * los ingresos del mes; en ingresos, siempre 100. Sin ingresos en el mes no hay porcentaje (`null`). Siempre
     * con un decimal y redondeo `HALF_UP`; los gastos pueden superar el 100 %.
     */
    fun percentOfIncome(type: MovementType): BigDecimal? {
        if (income.amountMinor == 0L) return null
        return when (type) {
            MovementType.INCOME -> BigDecimal(100).setScale(PERCENT_SCALE, RoundingMode.HALF_UP)
            MovementType.EXPENSE -> BigDecimal.valueOf(expenses.amountMinor)
                .multiply(BigDecimal(100))
                .divide(BigDecimal.valueOf(income.amountMinor), PERCENT_SCALE, RoundingMode.HALF_UP)
        }
    }

    private companion object {
        const val PERCENT_SCALE = 1
    }
}

/** Resultado de totalizar un mes: los totales o el motivo por el que no se pueden calcular. */
sealed interface MonthTotalsResult {
    data class Valid(val totals: MonthTotals) : MonthTotalsResult

    /** Hay movimientos en más de una moneda (la de la familia cambió con datos guardados): no se suma (RF-13). */
    data object MixedCurrencies : MonthTotalsResult

    /** Mes sin movimientos y sin moneda de la familia conocida: no hay en qué moneda mostrar los ceros. */
    data object UnknownCurrency : MonthTotalsResult
}
