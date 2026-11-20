package com.rocioplata.familyfinaiapp.domain.dashboard

import com.rocioplata.familyfinaiapp.domain.categories.Category
import com.rocioplata.familyfinaiapp.domain.categories.CategoryStatus
import com.rocioplata.familyfinaiapp.domain.categories.CategoryType
import com.rocioplata.familyfinaiapp.domain.money.Money
import com.rocioplata.familyfinaiapp.domain.movements.Movement
import com.rocioplata.familyfinaiapp.domain.movements.MovementType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

class CategoryBreakdownTest {
    private var nextId = 0

    private fun category(
        id: String,
        type: CategoryType = CategoryType.EXPENSE,
        status: CategoryStatus = CategoryStatus.ACTIVE,
        color: String? = null,
    ) = Category(id, type, "Cat $id", null, status, emptyList(), color)

    private fun movement(categoryId: String, amount: Long, type: MovementType = MovementType.EXPENSE, currency: String = "CLP") =
        Movement(
            id = "m-${nextId++}", type = type, amount = Money(amount, currency), categoryId = categoryId, tagId = null,
            paymentMethodId = "p-1", title = "x", note = null, date = LocalDate.of(2026, 10, 5), recordedBy = "u-1",
            createdAt = Instant.EPOCH,
        )

    private fun expenses(movements: List<Movement>, categories: List<Category>) =
        DashboardCalculator.categoryBreakdown(movements, categories, MovementType.EXPENSE, "CLP")

    private fun incomes(movements: List<Movement>, categories: List<Category>) =
        DashboardCalculator.categoryBreakdown(movements, categories, MovementType.INCOME, "CLP")

    private fun CategoryBreakdown.ids() = entries.map { it.category.id }

    // --- Orden, categorías en $0 y archivadas (RF-07 a RF-09, CA-09) ---

    @Test
    fun lasActivasAparecenTodas_tambienLasDe0_enElOrdenDeLaLista() {
        val list = listOf(category("a"), category("b"), category("c"))

        val result = expenses(listOf(movement("c", 100), movement("a", 50)), list)

        assertEquals(listOf("a", "b", "c"), result.ids())
        assertEquals(
            listOf(Money(50, "CLP"), Money.zero("CLP"), Money(100, "CLP")),
            result.entries.map { it.amount },
        )
        assertEquals(3, result.count)
    }

    @Test
    fun unaArchivadaConMovimientosVaAlFinal_aunqueEstuvieraAntesEnLaLista() {
        val list = listOf(category("a", status = CategoryStatus.DEPRECATED), category("b"), category("c"))

        val result = expenses(listOf(movement("a", 70), movement("b", 10)), list)

        assertEquals(listOf("b", "c", "a"), result.ids())
        assertTrue(result.entries.last().isArchived)
        assertEquals(3, result.count)
    }

    @Test
    fun unaArchivadaSinMovimientosEnElMesNoAparece() {
        val list = listOf(category("a"), category("old", status = CategoryStatus.DEPRECATED))

        val result = expenses(listOf(movement("a", 5)), list)

        assertEquals(listOf("a"), result.ids())
        assertEquals(1, result.count)
    }

    @Test
    fun variasArchivadasConservanElOrdenDeLaLista() {
        val list = listOf(
            category("x", status = CategoryStatus.DEPRECATED), category("a"),
            category("y", status = CategoryStatus.DEPRECATED), category("b"),
        )

        val result = expenses(listOf(movement("y", 1), movement("x", 2)), list)

        assertEquals(listOf("a", "b", "x", "y"), result.ids())
    }

    @Test
    fun soloSeMuestranLasCategoriasDelTipoActivo() {
        val list = listOf(
            category("g1"), category("i1", CategoryType.INCOME), category("g2"), category("i2", CategoryType.INCOME),
        )
        val movements = listOf(movement("g1", 100), movement("i1", 900, MovementType.INCOME))

        assertEquals(listOf("g1", "g2"), expenses(movements, list).ids())
        assertEquals(listOf("i1", "i2"), incomes(movements, list).ids())
        assertEquals(Money(900, "CLP"), incomes(movements, list).entries.first().amount)
        assertEquals(Money(100, "CLP"), expenses(movements, list).entries.first().amount)
    }

    @Test
    fun elMontoDeUnaCategoriaSumaTodosSusMovimientosDelMes() {
        val list = listOf(category("a"))

        val result = expenses(listOf(movement("a", 100), movement("a", 250), movement("a", 1)), list)

        assertEquals(Money(351, "CLP"), result.entries.single().amount)
        assertEquals(Money(351, "CLP"), result.total)
    }

    @Test
    fun sinCategoriasDelTipo_noHayTarjetas_perElTotalSeCalcula() {
        val list = listOf(category("i1", CategoryType.INCOME))

        val result = expenses(listOf(movement("fantasma", 400)), list)

        assertTrue(result.entries.isEmpty())
        assertEquals(0, result.count)
        assertTrue(result.segments.isEmpty())
        assertEquals(Money(400, "CLP"), result.total)
    }

    @Test
    fun sinCategoriasNiMovimientos_todoVacio() {
        val result = expenses(emptyList(), emptyList())

        assertEquals(Money.zero("CLP"), result.total)
        assertTrue(result.entries.isEmpty())
        assertTrue(result.segments.isEmpty())
    }

    // --- Gráfico (RF-04, CA-05) ---

    @Test
    fun elGraficoLlevaSoloLasCategoriasConMonto_enElOrdenDeLasTarjetas() {
        val list = listOf(
            category("a"), category("b"), category("c"), category("old", status = CategoryStatus.DEPRECATED),
        )

        val result = expenses(listOf(movement("c", 300), movement("a", 100), movement("old", 600)), list)

        assertEquals(listOf("a", "b", "c", "old"), result.ids())
        assertEquals(listOf("a", "c", "old"), result.segments.map { it.entry.category.id })
        assertTrue(result.segments.none { it.entry.amount.amountMinor == 0L })
    }

    @Test
    fun laParteDeCadaSegmentoEsSuMontoEntreElTotalDelTipo() {
        val list = listOf(category("a"), category("b"))

        val result = expenses(listOf(movement("a", 270_000), movement("b", 180_000)), list)

        assertEquals(BigDecimal("0.6000000000"), result.segments[0].share)
        assertEquals(BigDecimal("0.4000000000"), result.segments[1].share)
    }

    @Test
    fun laSumaDeLasPartesEsUnoConMontosQueNoDividenExacto() {
        val list = listOf(category("a"), category("b"), category("c"))

        val result = expenses(listOf(movement("a", 1), movement("b", 1), movement("c", 1)), list)

        val sum = result.segments.fold(BigDecimal.ZERO) { acc, s -> acc + s.share }
        assertTrue("suma $sum", (sum - BigDecimal.ONE).abs() < BigDecimal("0.000001"))
        assertEquals(BigDecimal("0.3333333333"), result.segments[0].share)
    }

    @Test
    fun unaSolaCategoriaConMonto_ocupaTodoElAnillo() {
        val result = expenses(listOf(movement("a", 5)), listOf(category("a"), category("b")))

        assertEquals(1, result.segments.size)
        assertEquals(BigDecimal("1.0000000000"), result.segments.single().share)
    }

    @Test
    fun sinMovimientosNoHaySegmentos_aunqueHayaTarjetas() {
        val result = expenses(emptyList(), listOf(category("a"), category("b")))

        assertEquals(2, result.count)
        assertTrue(result.segments.isEmpty())
        assertEquals(Money.zero("CLP"), result.total)
    }

    // --- Categoría que no está en la lista (RF-13) ---

    @Test
    fun unMovimientoDeUnaCategoriaDesconocidaCuentaEnElTotalPeroNoApareceEnLasTarjetas() {
        val list = listOf(category("a"), category("b"))

        val result = expenses(listOf(movement("a", 100), movement("fantasma", 300)), list)

        assertEquals(Money(400, "CLP"), result.total)
        assertEquals(Money(100, "CLP"), result.entries.sumOf { it.amount.amountMinor }.let { Money(it, "CLP") })
        assertEquals(listOf("a"), result.segments.map { it.entry.category.id })
        // La parte es sobre el total del tipo: el anillo no se completa y no se oculta la diferencia.
        assertEquals(BigDecimal("0.2500000000"), result.segments.single().share)
    }

    @Test
    fun elTotalDeLaDesgloseCoincideConElDeLosTotalesDelMes() {
        val movements = listOf(movement("a", 100), movement("fantasma", 50), movement("i", 900, MovementType.INCOME))
        val list = listOf(category("a"))

        val totals = (DashboardCalculator.monthTotals(movements, "CLP") as MonthTotalsResult.Valid).totals

        assertEquals(totals.expenses, expenses(movements, list).total)
        assertEquals(totals.income, incomes(movements, list).total)
    }

    // --- Posición para el color de respaldo (RF-14) ---

    @Test
    fun laPosicionDelColorEsLaIndiceEntreLasDeSuTipo_contandoArchivadas_ynoLasDelOtroTipo() {
        val list = listOf(
            category("g0"), category("i0", CategoryType.INCOME), category("g1", status = CategoryStatus.DEPRECATED),
            category("i1", CategoryType.INCOME), category("g2"),
        )

        val gastos = expenses(listOf(movement("g1", 10)), list)

        assertEquals(mapOf("g0" to 0, "g2" to 2, "g1" to 1), gastos.entries.associate { it.category.id to it.colorPosition })
        assertEquals(
            mapOf("i0" to 0, "i1" to 1),
            incomes(emptyList(), list).entries.associate { it.category.id to it.colorPosition },
        )
    }

    @Test
    fun archivarUnaCategoriaNoCambiaLaPosicionDeLasDemas() {
        val activa = listOf(category("a"), category("b"), category("c"))
        val archivada = listOf(category("a", status = CategoryStatus.DEPRECATED), category("b"), category("c"))

        val antes = expenses(emptyList(), activa).entries.associate { it.category.id to it.colorPosition }
        val despues = expenses(emptyList(), archivada).entries.associate { it.category.id to it.colorPosition }

        assertEquals(antes.filterKeys { it != "a" }, despues)
    }

    @Test
    fun laCategoriaConservaSuColorGuardado() {
        val result = expenses(emptyList(), listOf(category("a", color = "plum"), category("b")))

        assertEquals(listOf("plum", null), result.entries.map { it.category.color })
    }

    // --- Moneda y precisión ---

    @Test
    fun conUsdLosMontosConservanLosCentavos() {
        val list = listOf(category("a"), category("b"))

        val result = DashboardCalculator.categoryBreakdown(
            listOf(movement("a", 12_345, currency = "USD"), movement("b", 1, currency = "USD")), list, MovementType.EXPENSE, "USD",
        )

        assertEquals(Money(12_345, "USD"), result.entries[0].amount)
        assertEquals(Money(12_346, "USD"), result.total)
        assertEquals(Money.zero("USD"), expensesWithoutMovements("USD").total)
    }

    @Test
    fun importesDeDoceDigitos_sumanSinPerderPrecision() {
        val big = 999_999_999_999L
        val result = expenses(listOf(movement("a", big), movement("a", big), movement("b", big)), listOf(category("a"), category("b")))

        assertEquals(Money(2 * big, "CLP"), result.entries[0].amount)
        assertEquals(Money(3 * big, "CLP"), result.total)
        assertEquals(BigDecimal("0.6666666667"), result.segments[0].share)
        assertFalse(result.segments.isEmpty())
    }

    private fun expensesWithoutMovements(currency: String) =
        DashboardCalculator.categoryBreakdown(emptyList(), listOf(category("a")), MovementType.EXPENSE, currency)
}
