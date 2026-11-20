package com.rocioplata.familyfinaiapp.domain.dashboard

import com.rocioplata.familyfinaiapp.domain.money.Money
import java.math.BigDecimal

/** Hacia dónde se movió el total del tipo activo respecto del mes anterior. */
enum class ChangeDirection { UP, DOWN, UNCHANGED }

/** Si el cambio es bueno o malo para la familia: gastos que bajan o ingresos que suben son favorables (RF-06). */
enum class ChangeOutcome { FAVORABLE, UNFAVORABLE, NEUTRAL }

/**
 * Comparación del total del tipo activo con el mes anterior (RF-06): el indicador «−4,8 % vs mes anterior».
 * En el mes en curso compara el mismo tramo de días de los dos meses; en un mes pasado, los meses completos.
 */
data class MonthComparison(
    /** Total del mes mostrado en el tramo comparado. */
    val current: Money,
    /** Total del mes anterior en el mismo tramo: la base de la variación; nunca cero. */
    val previous: Money,
    /** (actual − anterior) / anterior, en porcentaje con un decimal y redondeo `HALF_UP`; negativa si bajó. */
    val variationPercent: BigDecimal,
    val direction: ChangeDirection,
    val outcome: ChangeOutcome,
)
