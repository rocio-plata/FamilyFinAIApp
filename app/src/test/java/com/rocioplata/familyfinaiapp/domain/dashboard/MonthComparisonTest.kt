package com.rocioplata.familyfinaiapp.domain.dashboard

import com.rocioplata.familyfinaiapp.domain.money.Money
import com.rocioplata.familyfinaiapp.domain.movements.Movement
import com.rocioplata.familyfinaiapp.domain.movements.MovementType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

class MonthComparisonTest {
    private var nextId = 0

    private fun movement(
        date: String,
        amount: Long,
        type: MovementType = MovementType.EXPENSE,
        currency: String = "CLP",
    ) = Movement(
        id = "m-${nextId++}", type = type, amount = Money(amount, currency), categoryId = "c-1", tagId = null,
        paymentMethodId = "p-1", title = "x", note = null, date = LocalDate.parse(date), recordedBy = "u-1",
        createdAt = Instant.EPOCH,
    )

    private fun expense(date: String, amount: Long) = movement(date, amount)
    private fun income(date: String, amount: Long) = movement(date, amount, MovementType.INCOME)

    private fun compare(
        current: List<Movement>,
        previous: List<Movement>?,
        shown: String,
        today: String,
        type: MovementType = MovementType.EXPENSE,
        currency: String = "CLP",
    ) = DashboardCalculator.comparison(current, previous, type, YearMonth.parse(shown), LocalDate.parse(today), currency)

    private fun pct(text: String) = BigDecimal(text)

    // --- Variación, dirección y color (RF-06, CA-07) ---

    @Test
    fun mesPasado_gastosBajan_esMenos4coma8_bajaYFavorable() {
        val result = compare(
            current = listOf(expense("2026-09-10", 95_200)),
            previous = listOf(expense("2026-08-10", 100_000)),
            shown = "2026-09", today = "2026-10-20",
        )

        assertEquals(pct("-4.8"), result?.variationPercent)
        assertEquals(ChangeDirection.DOWN, result?.direction)
        assertEquals(ChangeOutcome.FAVORABLE, result?.outcome)
        assertEquals(Money(95_200, "CLP"), result?.current)
        assertEquals(Money(100_000, "CLP"), result?.previous)
    }

    @Test
    fun losMismosNumerosEnIngresos_bajanYSonDesfavorables() {
        val result = compare(
            current = listOf(income("2026-09-10", 95_200)),
            previous = listOf(income("2026-08-10", 100_000)),
            shown = "2026-09", today = "2026-10-20", type = MovementType.INCOME,
        )

        assertEquals(pct("-4.8"), result?.variationPercent)
        assertEquals(ChangeDirection.DOWN, result?.direction)
        assertEquals(ChangeOutcome.UNFAVORABLE, result?.outcome)
    }

    @Test
    fun subida_gastosDesfavorable_ingresosFavorable() {
        val gastos = compare(
            listOf(expense("2026-09-10", 120_000)), listOf(expense("2026-08-10", 100_000)), "2026-09", "2026-10-20",
        )
        val ingresos = compare(
            listOf(income("2026-09-10", 120_000)), listOf(income("2026-08-10", 100_000)), "2026-09", "2026-10-20",
            type = MovementType.INCOME,
        )

        assertEquals(pct("20.0"), gastos?.variationPercent)
        assertEquals(ChangeDirection.UP, gastos?.direction)
        assertEquals(ChangeOutcome.UNFAVORABLE, gastos?.outcome)
        assertEquals(ChangeDirection.UP, ingresos?.direction)
        assertEquals(ChangeOutcome.FAVORABLE, ingresos?.outcome)
    }

    @Test
    fun puedeSuperarElCienPorCiento() {
        val result = compare(
            listOf(expense("2026-09-10", 350_000)), listOf(expense("2026-08-10", 100_000)), "2026-09", "2026-10-20",
        )

        assertEquals(pct("250.0"), result?.variationPercent)
    }

    @Test
    fun sinGastosEnElMesActual_bajaUnCienPorCiento() {
        val result = compare(emptyList(), listOf(expense("2026-08-10", 100_000)), "2026-09", "2026-10-20")

        assertEquals(pct("-100.0"), result?.variationPercent)
        assertEquals(Money.zero("CLP"), result?.current)
    }

    // --- Sin comparación (RF-06, CA-07) ---

    @Test
    fun conBaseCero_noHayComparacion() {
        assertNull(compare(listOf(expense("2026-09-10", 100)), emptyList(), "2026-09", "2026-10-20"))
        assertNull(compare(listOf(expense("2026-09-10", 100)), listOf(income("2026-08-10", 500)), "2026-09", "2026-10-20"))
        assertNull(compare(emptyList(), emptyList(), "2026-09", "2026-10-20"))
    }

    @Test
    fun sinDatosDelMesAnterior_noHayComparacion() {
        assertNull(compare(listOf(expense("2026-09-10", 100)), previous = null, shown = "2026-09", today = "2026-10-20"))
    }

    @Test
    fun anteriorEnOtraMoneda_noSeCompara() {
        val result = compare(
            listOf(expense("2026-09-10", 100)), listOf(movement("2026-08-10", 100, currency = "USD")),
            "2026-09", "2026-10-20",
        )

        assertNull(result)
    }

    // --- Cambio que redondea a cero y redondeo (RF-06) ---

    @Test
    fun siRedondeaACeroComaCero_esSinCambioYNeutro() {
        val up = compare(listOf(expense("2026-09-10", 100_004)), listOf(expense("2026-08-10", 100_000)), "2026-09", "2026-10-20")
        val down = compare(listOf(expense("2026-09-10", 99_996)), listOf(expense("2026-08-10", 100_000)), "2026-09", "2026-10-20")
        val same = compare(listOf(expense("2026-09-10", 100_000)), listOf(expense("2026-08-10", 100_000)), "2026-09", "2026-10-20")

        listOf(up, down, same).forEach {
            assertEquals(0, it?.variationPercent?.signum())
            assertEquals(ChangeDirection.UNCHANGED, it?.direction)
            assertEquals(ChangeOutcome.NEUTRAL, it?.outcome)
        }
        assertEquals(pct("0.0"), up?.variationPercent)
    }

    @Test
    fun redondeaMitadHaciaArriba_aUnDecimal_tambienEnNegativo() {
        // +0,05 % -> +0,1 ; -0,05 % -> -0,1.
        val up = compare(listOf(expense("2026-09-10", 2_001)), listOf(expense("2026-08-10", 2_000)), "2026-09", "2026-10-20")
        val down = compare(listOf(expense("2026-09-10", 1_999)), listOf(expense("2026-08-10", 2_000)), "2026-09", "2026-10-20")

        assertEquals(pct("0.1"), up?.variationPercent)
        assertEquals(pct("-0.1"), down?.variationPercent)
    }

    @Test
    fun siempreUnDecimal_yExactoConImportesGrandes() {
        val big = 999_999_999_999L
        val result = compare(listOf(expense("2026-09-10", big)), listOf(expense("2026-08-10", big - 1)), "2026-09", "2026-10-20")

        assertEquals(1, result?.variationPercent?.scale())
        assertEquals(pct("0.0"), result?.variationPercent)
    }

    // --- Tramo de días del mes en curso (RF-06, CA-08) ---

    @Test
    fun mesEnCurso_hoyEsElDia10_comparaDel1Al10EnLosDosMeses() {
        val current = listOf(expense("2026-10-03", 100), expense("2026-10-10", 50), expense("2026-10-15", 1_000))
        val previous = listOf(
            expense("2026-09-01", 100), expense("2026-09-10", 100), expense("2026-09-11", 5_000), expense("2026-09-30", 9_000),
        )

        val result = compare(current, previous, shown = "2026-10", today = "2026-10-10")

        // Del 1 al 10: actual 150 (el del día 15 no entra), anterior 200 (los del 11 y el 30 no entran).
        assertEquals(Money(150, "CLP"), result?.current)
        assertEquals(Money(200, "CLP"), result?.previous)
        assertEquals(pct("-25.0"), result?.variationPercent)
    }

    @Test
    fun elDiaDeHoyEntraEnElTramo_yElSiguienteNo() {
        val previous = listOf(expense("2026-09-10", 100), expense("2026-09-11", 100))

        val result = compare(listOf(expense("2026-10-10", 100)), previous, "2026-10", "2026-10-10")

        assertEquals(Money(100, "CLP"), result?.previous)
        assertEquals(pct("0.0"), result?.variationPercent)
    }

    @Test
    fun hoyEsElDia31_trasUnMesDe30_elAnteriorSeCuentaCompleto() {
        val previous = listOf(expense("2026-09-01", 100), expense("2026-09-30", 300))

        val result = compare(listOf(expense("2026-10-31", 200)), previous, shown = "2026-10", today = "2026-10-31")

        assertEquals(Money(400, "CLP"), result?.previous)
        assertEquals(pct("-50.0"), result?.variationPercent)
    }

    @Test
    fun finDeMarzo_frenteAFebreroDe28Dias_29_30Y31ComparanFebreroCompleto_yElDia28TambienPorSerElUltimo() {
        val february = listOf(expense("2026-02-01", 100), expense("2026-02-28", 100))

        listOf("2026-03-28", "2026-03-29", "2026-03-30", "2026-03-31").forEach { today ->
            val result = compare(listOf(expense("2026-03-01", 100)), february, shown = "2026-03", today = today)
            assertEquals(today, Money(200, "CLP"), result?.previous)
        }
    }

    @Test
    fun febreroDeAnoBisiesto_29Dias() {
        val february = listOf(expense("2028-02-01", 100), expense("2028-02-29", 100))

        val full = compare(listOf(expense("2028-03-01", 100)), february, "2028-03", "2028-03-29")
        val partial = compare(listOf(expense("2028-03-01", 100)), february, "2028-03", "2028-03-28")

        assertEquals(Money(200, "CLP"), full?.previous)
        assertEquals(Money(100, "CLP"), partial?.previous)
    }

    @Test
    fun enEnero_elMesAnteriorEsDiciembreDelAnoAnterior() {
        val december = listOf(expense("2025-12-02", 100), expense("2025-12-20", 900))

        val result = compare(listOf(expense("2026-01-05", 50)), december, shown = "2026-01", today = "2026-01-15")

        assertEquals(Money(100, "CLP"), result?.previous)
        assertEquals(pct("-50.0"), result?.variationPercent)
    }

    @Test
    fun hoyEsElDia1_soloComparaElPrimerDia() {
        val previous = listOf(expense("2026-09-01", 100), expense("2026-09-02", 900))

        val result = compare(listOf(expense("2026-10-01", 100)), previous, "2026-10", "2026-10-01")

        assertEquals(Money(100, "CLP"), result?.previous)
        assertEquals(pct("0.0"), result?.variationPercent)
    }

    @Test
    fun mesPasado_comparaLosMesesCompletos_seaCualSeaElDiaDeHoy() {
        val current = listOf(expense("2026-09-02", 100), expense("2026-09-30", 100))
        val previous = listOf(expense("2026-08-02", 100), expense("2026-08-31", 300))

        listOf("2026-10-01", "2026-10-10", "2026-12-31").forEach { today ->
            val result = compare(current, previous, shown = "2026-09", today = today)
            assertEquals(today, Money(200, "CLP"), result?.current)
            assertEquals(today, Money(400, "CLP"), result?.previous)
            assertEquals(today, pct("-50.0"), result?.variationPercent)
        }
    }

    @Test
    fun soloCuentaElTipoActivo() {
        val current = listOf(expense("2026-09-10", 100), income("2026-09-10", 9_999))
        val previous = listOf(expense("2026-08-10", 200), income("2026-08-10", 1))

        val result = compare(current, previous, "2026-09", "2026-10-20")

        assertEquals(Money(100, "CLP"), result?.current)
        assertEquals(Money(200, "CLP"), result?.previous)
        assertNotNull(result)
    }

    @Test
    fun usdConCentavos() {
        val result = compare(
            listOf(movement("2026-09-10", 12_345, currency = "USD")),
            listOf(movement("2026-08-10", 10_000, currency = "USD")),
            "2026-09", "2026-10-20", currency = "USD",
        )

        assertEquals(Money(12_345, "USD"), result?.current)
        assertEquals(pct("23.5"), result?.variationPercent)
    }
}
