package com.rocioplata.familyfinaiapp.domain.dashboard

import com.rocioplata.familyfinaiapp.domain.money.Money
import com.rocioplata.familyfinaiapp.domain.movements.Movement
import com.rocioplata.familyfinaiapp.domain.movements.MovementType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

class DashboardCalculatorTest {
    private var nextId = 0

    private fun movement(type: MovementType, amount: Long, currency: String = "CLP", categoryId: String = "c-1") =
        Movement(
            id = "m-${nextId++}", type = type, amount = Money(amount, currency), categoryId = categoryId, tagId = null,
            paymentMethodId = "p-1", title = "x", note = null, date = LocalDate.of(2026, 10, 5), recordedBy = "u-1",
            createdAt = Instant.EPOCH,
        )

    private fun expense(amount: Long, currency: String = "CLP") = movement(MovementType.EXPENSE, amount, currency)
    private fun income(amount: Long, currency: String = "CLP") = movement(MovementType.INCOME, amount, currency)

    private fun totals(movements: List<Movement>, familyCurrency: String? = "CLP"): MonthTotals =
        (DashboardCalculator.monthTotals(movements, familyCurrency) as MonthTotalsResult.Valid).totals

    private fun pct(text: String) = BigDecimal(text)

    // --- Totales y disponible (RF-02, CA-03) ---

    @Test
    fun totales_conLosDatosDeLaSpec() {
        val result = totals(listOf(income(1_850_000), expense(425_800), expense(694_650)))

        assertEquals(Money(1_850_000, "CLP"), result.income)
        assertEquals(Money(1_120_450, "CLP"), result.expenses)
        assertEquals(Money(729_550, "CLP"), result.available)
    }

    @Test
    fun totalOf_devuelveElTotalDelTipo() {
        val result = totals(listOf(income(1_000), expense(300)))

        assertEquals(Money(1_000, "CLP"), result.totalOf(MovementType.INCOME))
        assertEquals(Money(300, "CLP"), result.totalOf(MovementType.EXPENSE))
    }

    @Test
    fun disponible_conMasGastosQueIngresos_esNegativo() {
        val result = totals(listOf(income(500_000), expense(800_000)))

        assertEquals(Money(-300_000, "CLP"), result.available)
    }

    @Test
    fun cadaTipoSumaSoloSusMovimientos() {
        val result = totals(listOf(income(10), income(20), expense(1), expense(2), expense(3)))

        assertEquals(Money(30, "CLP"), result.income)
        assertEquals(Money(6, "CLP"), result.expenses)
    }

    @Test
    fun mesVacio_conMonedaDeLaFamilia_esTodoCero() {
        val result = totals(emptyList(), familyCurrency = "USD")

        assertEquals(Money(0, "USD"), result.income)
        assertEquals(Money(0, "USD"), result.expenses)
        assertEquals(Money(0, "USD"), result.available)
        assertNull(result.percentOfIncome(MovementType.EXPENSE))
    }

    @Test
    fun mesVacio_sinMonedaConocida_noSePuedeTotalizar() {
        assertEquals(MonthTotalsResult.UnknownCurrency, DashboardCalculator.monthTotals(emptyList(), null))
    }

    @Test
    fun conMovimientos_laMonedaEsLaDeLosMovimientos_auncuandoNoSeaLaDeLaFamilia() {
        val result = totals(listOf(income(5_000, "USD"), expense(1_250, "USD")), familyCurrency = "CLP")

        assertEquals(Money(3_750, "USD"), result.available)
    }

    @Test
    fun conMovimientos_noHaceFaltaLaMonedaDeLaFamilia() {
        val result = totals(listOf(expense(100)), familyCurrency = null)

        assertEquals(Money(100, "CLP"), result.expenses)
    }

    @Test
    fun monedasMezcladas_noSeSuman() {
        val result = DashboardCalculator.monthTotals(listOf(expense(100, "CLP"), expense(100, "USD")), "CLP")

        assertEquals(MonthTotalsResult.MixedCurrencies, result)
    }

    @Test
    fun usdConCentavos_sumaExacta() {
        val result = totals(listOf(income(100_000, "USD"), expense(33_333, "USD"), expense(1, "USD")))

        assertEquals(Money(66_666, "USD"), result.available)
        assertEquals(Money(33_334, "USD"), result.expenses)
    }

    @Test
    fun importesDeDoceDigitos_sumanSinPerderPrecision() {
        val big = 999_999_999_999L
        val result = totals(listOf(income(big), income(big), expense(big)))

        assertEquals(Money(2 * big, "CLP"), result.income)
        assertEquals(Money(big, "CLP"), result.available)
    }

    // --- Porcentaje gastos sobre ingresos (RF-05, CA-06) ---

    @Test
    fun porcentaje_conLosDatosDeLaSpec_esSesentaComaSeis_enGastosYCien_enIngresos() {
        val result = totals(listOf(income(1_850_000), expense(1_120_450)))

        assertEquals(pct("60.6"), result.percentOfIncome(MovementType.EXPENSE))
        assertEquals(pct("100.0"), result.percentOfIncome(MovementType.INCOME))
    }

    @Test
    fun porcentaje_sinIngresos_noSeMuestraParaNingunTipo() {
        val result = totals(listOf(expense(100_000)))

        assertNull(result.percentOfIncome(MovementType.EXPENSE))
        assertNull(result.percentOfIncome(MovementType.INCOME))
    }

    @Test
    fun porcentaje_sinGastos_esCeroComaCero() {
        val result = totals(listOf(income(100_000)))

        assertEquals(pct("0.0"), result.percentOfIncome(MovementType.EXPENSE))
    }

    @Test
    fun porcentaje_puedeSuperarElCien() {
        assertEquals(pct("200.0"), totals(listOf(income(500), expense(1_000))).percentOfIncome(MovementType.EXPENSE))
        assertEquals(pct("160.0"), totals(listOf(income(500), expense(800))).percentOfIncome(MovementType.EXPENSE))
    }

    @Test
    fun porcentaje_redondeaMitadHaciaArriba_aUnDecimal() {
        // 1 / 2000 = 0,05 % -> 0,1 ; 1 / 3000 = 0,0333 % -> 0,0 ; 1 / 8 = 12,5 % exacto.
        assertEquals(pct("0.1"), totals(listOf(income(2_000), expense(1))).percentOfIncome(MovementType.EXPENSE))
        assertEquals(pct("0.0"), totals(listOf(income(3_000), expense(1))).percentOfIncome(MovementType.EXPENSE))
        assertEquals(pct("12.5"), totals(listOf(income(8), expense(1))).percentOfIncome(MovementType.EXPENSE))
        // 2 / 3 = 66,666...% -> 66,7.
        assertEquals(pct("66.7"), totals(listOf(income(3), expense(2))).percentOfIncome(MovementType.EXPENSE))
    }

    @Test
    fun porcentaje_siempreTieneUnDecimal_yEsExactoConImportesGrandes() {
        val big = 999_999_999_999L
        val result = totals(listOf(income(big), expense(big - 1)))

        assertEquals(1, result.percentOfIncome(MovementType.EXPENSE)?.scale())
        assertEquals(1, result.percentOfIncome(MovementType.INCOME)?.scale())
        // (big - 1) * 100 / big = 99,9999999999 % -> 100,0 al redondear a un decimal.
        assertEquals(pct("100.0"), result.percentOfIncome(MovementType.EXPENSE))
    }

    @Test
    fun porcentaje_usaLaUnidadMenor_porLoQueLaMonedaNoCambiaElResultado() {
        val clp = totals(listOf(income(1_000), expense(250)))
        val usd = totals(listOf(income(1_000, "USD"), expense(250, "USD")))

        assertEquals(clp.percentOfIncome(MovementType.EXPENSE), usd.percentOfIncome(MovementType.EXPENSE))
        assertEquals(pct("25.0"), usd.percentOfIncome(MovementType.EXPENSE))
    }

    @Test
    fun elDominioNoDependeDeAndroid() {
        val sources = java.io.File("src/main/java/com/rocioplata/familyfinaiapp/domain/dashboard")
            .walkTopDown().filter { it.extension == "kt" }.toList()

        assertTrue(sources.isNotEmpty())
        sources.forEach { file ->
            val forbidden = file.readLines().filter { it.startsWith("import android") || it.startsWith("import io.ktor") }
            assertTrue("${file.name} importa ${forbidden}", forbidden.isEmpty())
        }
    }
}
