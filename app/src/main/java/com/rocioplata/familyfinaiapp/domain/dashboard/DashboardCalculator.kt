package com.rocioplata.familyfinaiapp.domain.dashboard

import com.rocioplata.familyfinaiapp.domain.categories.Category
import com.rocioplata.familyfinaiapp.domain.categories.CategoryStatus
import com.rocioplata.familyfinaiapp.domain.categories.CategoryType
import com.rocioplata.familyfinaiapp.domain.money.Money
import com.rocioplata.familyfinaiapp.domain.money.sum
import com.rocioplata.familyfinaiapp.domain.movements.Movement
import com.rocioplata.familyfinaiapp.domain.movements.MovementType
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethod
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethodStatus
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

/**
 * Reglas de negocio del dashboard (RF-02 a RF-09, RF-13), puras: sin Android, sin red y sin estado. Los importes
 * son enteros en la unidad menor y las sumas exactas; los porcentajes usan `BigDecimal`, nunca coma flotante.
 */
object DashboardCalculator {
    /**
     * Totales de los [movements] de un mes. La moneda es la de los propios movimientos y, si no hay ninguno, la de
     * la familia ([familyCurrency]); con movimientos en más de una moneda no se suma.
     */
    fun monthTotals(movements: List<Movement>, familyCurrency: String?): MonthTotalsResult {
        val currencies = movements.map { it.amount.currency }.distinct()
        if (currencies.size > 1) return MonthTotalsResult.MixedCurrencies
        val currency = currencies.singleOrNull() ?: familyCurrency ?: return MonthTotalsResult.UnknownCurrency
        return MonthTotalsResult.Valid(
            MonthTotals(
                income = movements.totalOf(MovementType.INCOME, currency),
                expenses = movements.totalOf(MovementType.EXPENSE, currency),
            ),
        )
    }

    private fun List<Movement>.totalOf(type: MovementType, currency: String): Money =
        filter { it.type == type }.map { it.amount }.sum(currency)

    /**
     * Reparto por categoría del tipo [type] (RF-04, RF-07 a RF-09). Las categorías activas del tipo aparecen todas,
     * también las que no tienen movimientos en el mes (con monto cero), en el orden de [categories], que es el de
     * la gestión; después, las archivadas con movimientos en el mes, en ese mismo orden. Una archivada sin
     * movimientos no aparece. El gráfico lleva solo las que tienen monto.
     *
     * [currency] es la de los movimientos (ver [monthTotals]): mezclar monedas aquí es un error de programación.
     * Un movimiento de una categoría que no está en [categories] cuenta en [CategoryBreakdown.total] pero no
     * aparece en las tarjetas ni en el gráfico, que por eso puede no completar el anillo.
     */
    fun categoryBreakdown(
        movements: List<Movement>,
        categories: List<Category>,
        type: MovementType,
        currency: String,
    ): CategoryBreakdown {
        val ofType = movements.filter { it.type == type }
        val ofCategoryType = categories.filter { it.type == type.toCategoryType() }
        val amounts = ofType.groupBy { it.categoryId }.mapValues { (_, list) -> list.map { it.amount }.sum(currency) }
        val all = ofCategoryType.mapIndexed { position, category ->
            CategoryAmount(category, amounts[category.id] ?: Money.zero(currency), position)
        }
        val entries = all.filter { it.category.status == CategoryStatus.ACTIVE } +
            all.filter { it.isArchived && it.hasAmount }
        val total = ofType.map { it.amount }.sum(currency)
        return CategoryBreakdown(total, entries, segmentsOf(entries, total))
    }

    /**
     * Reparto por medio de pago del tipo [type] (RF-16 a RF-18). Los medios activos aparecen todos, también los que no
     * tienen movimientos en el mes (con monto cero), en el orden de [methods], que es el del backend; después, los
     * archivados con movimientos en el mes y, al final, «Sin medio de pago» si hay movimientos sin medio. Un archivado
     * sin movimientos no aparece.
     *
     * [currency] es la de los movimientos (ver [monthTotals]). Un movimiento de un medio que no está en [methods] (lista
     * desactualizada) cuenta en [PaymentMethodBreakdown.total] pero no tiene tarjeta, igual que con las categorías.
     */
    fun paymentMethodBreakdown(
        movements: List<Movement>,
        methods: List<PaymentMethod>,
        type: MovementType,
        currency: String,
    ): PaymentMethodBreakdown {
        val ofType = movements.filter { it.type == type }
        val amounts = ofType.groupBy { it.paymentMethodId }.mapValues { (_, list) -> list.map { it.amount }.sum(currency) }
        val all = methods.map { PaymentMethodAmount(it, amounts[it.id] ?: Money.zero(currency)) }
        val unassigned = PaymentMethodAmount(null, amounts[null] ?: Money.zero(currency))
        val entries = all.filter { it.method?.status == PaymentMethodStatus.ACTIVE } +
            all.filter { it.isArchived && it.hasAmount } +
            listOfNotNull(unassigned.takeIf { it.hasAmount })
        return PaymentMethodBreakdown(ofType.map { it.amount }.sum(currency), entries)
    }

    private fun segmentsOf(entries: List<CategoryAmount>, total: Money): List<ChartSegment> {
        if (total.amountMinor <= 0) return emptyList()
        return entries.filter { it.hasAmount }.map {
            val share = BigDecimal.valueOf(it.amount.amountMinor)
                .divide(BigDecimal.valueOf(total.amountMinor), SHARE_SCALE, RoundingMode.HALF_UP)
            ChartSegment(it, share)
        }
    }

    private fun MovementType.toCategoryType(): CategoryType = when (this) {
        MovementType.EXPENSE -> CategoryType.EXPENSE
        MovementType.INCOME -> CategoryType.INCOME
    }

    private const val SHARE_SCALE = 10

    /**
     * Variación del total del tipo [type] respecto del mes anterior (RF-06). Si [shown] es el mes en curso (el
     * de [today]) se comparan los movimientos del día 1 al día de hoy en los dos meses: si hoy es un día que el mes
     * anterior no tiene (el 31 tras un mes de 30), el tramo del anterior es el mes completo. En un mes pasado se
     * comparan los meses completos.
     *
     * Es `null`, y el indicador no se muestra, si no hay datos del mes anterior ([previous] nulo), si tiene
     * movimientos en otra moneda que [currency] o si su total en el tramo es cero (no hay base). Un cambio que
     * redondea a 0,0 % es [ChangeDirection.UNCHANGED] y neutro. [current] debe estar en [currency].
     */
    fun comparison(
        current: List<Movement>,
        previous: List<Movement>?,
        type: MovementType,
        shown: YearMonth,
        today: LocalDate,
        currency: String,
    ): MonthComparison? {
        if (previous == null || previous.any { it.amount.currency != currency }) return null
        // El mismo último día para los dos meses: en el anterior, un día mayor que su longitud no existe.
        val lastDay = if (shown == YearMonth.from(today)) today.dayOfMonth else null
        val currentTotal = current.totalInRange(type, lastDay, currency)
        val previousTotal = previous.totalInRange(type, lastDay, currency)
        if (previousTotal.amountMinor == 0L) return null
        val variation = BigDecimal.valueOf(currentTotal.amountMinor - previousTotal.amountMinor)
            .multiply(BigDecimal(100))
            .divide(BigDecimal.valueOf(previousTotal.amountMinor), PERCENT_SCALE, RoundingMode.HALF_UP)
        val direction = when (variation.signum()) {
            1 -> ChangeDirection.UP
            -1 -> ChangeDirection.DOWN
            else -> ChangeDirection.UNCHANGED
        }
        return MonthComparison(currentTotal, previousTotal, variation, direction, outcomeOf(direction, type))
    }

    private fun List<Movement>.totalInRange(type: MovementType, lastDay: Int?, currency: String): Money =
        filter { it.type == type && (lastDay == null || it.date.dayOfMonth <= lastDay) }
            .map { it.amount }.sum(currency)

    private fun outcomeOf(direction: ChangeDirection, type: MovementType): ChangeOutcome = when (direction) {
        ChangeDirection.UNCHANGED -> ChangeOutcome.NEUTRAL
        ChangeDirection.DOWN ->
            if (type == MovementType.EXPENSE) ChangeOutcome.FAVORABLE else ChangeOutcome.UNFAVORABLE
        ChangeDirection.UP ->
            if (type == MovementType.INCOME) ChangeOutcome.FAVORABLE else ChangeOutcome.UNFAVORABLE
    }

    private const val PERCENT_SCALE = 1
}
