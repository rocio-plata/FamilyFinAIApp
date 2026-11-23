package com.rocioplata.familyfinaiapp.ui.dashboard

import androidx.annotation.StringRes
import com.rocioplata.familyfinaiapp.R
import com.rocioplata.familyfinaiapp.domain.dashboard.ChangeDirection
import com.rocioplata.familyfinaiapp.domain.dashboard.ChartSegment
import com.rocioplata.familyfinaiapp.domain.dashboard.MonthComparison
import com.rocioplata.familyfinaiapp.domain.movements.MovementType
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

// Textos y formatos del dashboard (RF-01 a RF-11). Los textos que dependen del tipo o de la dirección del cambio
// se resuelven aquí a su recurso; los números se formatean con la configuración regional del dispositivo. Los
// montos y el mes usan `Money.formatted` y `formatMonth` de `ui/movements/MoneyFormat.kt`.

/** Nombre del tipo en su tarjeta: «Gastos» o «Ingresos» (RF-03). */
@StringRes
fun MovementType.typeLabelRes(): Int = when (this) {
    MovementType.EXPENSE -> R.string.dashboard_type_expenses
    MovementType.INCOME -> R.string.dashboard_type_income
}

/** Título de la tarjeta de desglose: «Desglose de gastos» o «Desglose de ingresos» (RF-03). */
@StringRes
fun MovementType.breakdownTitleRes(): Int = when (this) {
    MovementType.EXPENSE -> R.string.dashboard_breakdown_title_expenses
    MovementType.INCOME -> R.string.dashboard_breakdown_title_income
}

/** Título de la sección por categoría: «Gastos por categoría» o «Ingresos por categoría» (RF-07). */
@StringRes
fun MovementType.categoriesTitleRes(): Int = when (this) {
    MovementType.EXPENSE -> R.string.dashboard_categories_title_expenses
    MovementType.INCOME -> R.string.dashboard_categories_title_income
}

/** Aviso de que el tipo no tiene categorías activas; «Gestionar» sigue disponible (RF-10). */
@StringRes
fun MovementType.noCategoriesRes(): Int = when (this) {
    MovementType.EXPENSE -> R.string.dashboard_categories_empty_expenses
    MovementType.INCOME -> R.string.dashboard_categories_empty_income
}

/** Comienzo de la descripción del gráfico para el lector de pantalla; lleva el total como `%1$s` (RF-04). */
@StringRes
fun MovementType.chartDescriptionRes(): Int = when (this) {
    MovementType.EXPENSE -> R.string.dashboard_chart_description_expenses
    MovementType.INCOME -> R.string.dashboard_chart_description_income
}

/** Descripción del indicador de comparación para el lector de pantalla; lleva el porcentaje sin signo (RF-06). */
@StringRes
fun ChangeDirection.variationDescriptionRes(): Int = when (this) {
    ChangeDirection.UP -> R.string.dashboard_variation_up_description
    ChangeDirection.DOWN -> R.string.dashboard_variation_down_description
    ChangeDirection.UNCHANGED -> R.string.dashboard_variation_unchanged_description
}

/**
 * Porcentaje con la configuración regional, con un decimal como máximo y sin él si es entero (RF-05, RF-06):
 * «60,6 %» y «100 %» en español de Chile, «60.6%» y «100%» en inglés de Estados Unidos. [value] ya está en
 * porcentaje (60,6 es el 60,6 %); el cálculo es exacto, sin coma flotante.
 */
fun formatPercent(value: BigDecimal, locale: Locale = Locale.getDefault()): String =
    percentFormat(locale).format(value.movePointLeft(2))

/**
 * Variación frente al mes anterior con su signo explícito, por la dirección y no por el valor: «−4,8 %», «+6,2 %»
 * y, si no cambió, «0 %» (RF-06). El texto «vs mes anterior» lo pone el recurso `dashboard_variation_vs_previous`.
 */
fun MonthComparison.formattedVariation(locale: Locale = Locale.getDefault()): String {
    val magnitude = formatPercent(variationPercent.abs(), locale)
    return when (direction) {
        ChangeDirection.UP -> "+$magnitude"
        ChangeDirection.DOWN -> "−$magnitude"
        ChangeDirection.UNCHANGED -> magnitude
    }
}

/**
 * Descripción del gráfico para el lector de pantalla (RF-04): [header] (tipo y total, ya con su texto) y, si hay
 * segmentos, cada categoría con su parte del total: «Gastos del mes, total $1.120.450. Comestibles 38 %,
 * Salud 10 %». Sin segmentos queda solo el [header].
 */
fun chartDescription(header: String, segments: List<ChartSegment>, locale: Locale = Locale.getDefault()): String {
    if (segments.isEmpty()) return header
    val parts = segments.joinToString(", ") { "${it.entry.category.name} ${formatShare(it.share, locale)}" }
    return "$header. $parts"
}

/** Parte de un segmento (0 a 1) como porcentaje con un decimal como máximo: 0,376 → «37,6 %». */
fun formatShare(share: BigDecimal, locale: Locale = Locale.getDefault()): String =
    percentFormat(locale).format(share)

// El formato de porcentaje multiplica por 100 él mismo: recibe la fracción (0,606 es el 60,6 %).
private fun percentFormat(locale: Locale): NumberFormat =
    NumberFormat.getPercentInstance(locale).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 1
        roundingMode = RoundingMode.HALF_UP
    }
