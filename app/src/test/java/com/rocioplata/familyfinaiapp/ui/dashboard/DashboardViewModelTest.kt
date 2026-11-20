package com.rocioplata.familyfinaiapp.ui.dashboard

import com.rocioplata.familyfinaiapp.domain.categories.CategoriesData
import com.rocioplata.familyfinaiapp.domain.categories.Category
import com.rocioplata.familyfinaiapp.domain.categories.CategoryStatus
import com.rocioplata.familyfinaiapp.domain.categories.CategoryType
import com.rocioplata.familyfinaiapp.domain.dashboard.ChangeDirection
import com.rocioplata.familyfinaiapp.domain.dashboard.ChangeOutcome
import com.rocioplata.familyfinaiapp.domain.money.Money
import com.rocioplata.familyfinaiapp.domain.movements.Movement
import com.rocioplata.familyfinaiapp.domain.movements.MovementDates
import com.rocioplata.familyfinaiapp.domain.movements.MovementFailure
import com.rocioplata.familyfinaiapp.domain.movements.MovementResult
import com.rocioplata.familyfinaiapp.domain.movements.MovementType
import com.rocioplata.familyfinaiapp.domain.movements.MovementsData
import com.rocioplata.familyfinaiapp.ui.movements.FakeCategories
import com.rocioplata.familyfinaiapp.ui.movements.FakeConnectivity
import com.rocioplata.familyfinaiapp.ui.movements.FakeMovements
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {
    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    // Hoy es el 16 de noviembre de 2026.
    private val dates = MovementDates(Clock.fixed(Instant.parse("2026-11-16T15:00:00Z"), ZoneId.of("UTC")))
    private val nov = YearMonth.of(2026, 11)
    private val oct = YearMonth.of(2026, 10)
    private val sep = YearMonth.of(2026, 9)

    private fun category(id: String, type: CategoryType, status: CategoryStatus = CategoryStatus.ACTIVE) =
        Category(id, type, "Cat $id", null, status, emptyList())

    private val food = category("c-food", CategoryType.EXPENSE)
    private val transport = category("c-transport", CategoryType.EXPENSE)
    private val old = category("c-old", CategoryType.EXPENSE, CategoryStatus.DEPRECATED)
    private val salary = category("c-salary", CategoryType.INCOME)

    private fun mov(
        id: String,
        type: MovementType,
        amount: Long,
        day: Int,
        month: YearMonth = nov,
        categoryId: String = if (type == MovementType.EXPENSE) "c-food" else "c-salary",
        currency: String = "CLP",
    ) = Movement(
        id = id, type = type, amount = Money(amount, currency), categoryId = categoryId, tagId = null,
        paymentMethodId = "m-card", title = "Mov $id", note = null, date = month.atDay(day), recordedBy = "u-1",
        createdAt = Instant.EPOCH,
    )

    private val sueldo = mov("m-sueldo", MovementType.INCOME, 1_200_000, day = 15)
    private val super1 = mov("m-super", MovementType.EXPENSE, 45_900, day = 15)
    private val taxi = mov("m-taxi", MovementType.EXPENSE, 5_000, day = 3)
    private val november = listOf(super1, sueldo, taxi)

    // Octubre: del 1 al 16 hay 100.000 de gastos (el del día 20 no entra en el tramo).
    private val october = listOf(
        mov("o-1", MovementType.EXPENSE, 100_000, day = 2, month = oct),
        mov("o-2", MovementType.EXPENSE, 50_000, day = 20, month = oct),
    )

    private class Rig(
        val movements: FakeMovements,
        val categories: FakeCategories,
        val connectivity: FakeConnectivity,
        val vm: DashboardViewModel,
        val insufficientRole: () -> Int,
    ) {
        val state get() = vm.uiState.value
        val content get() = state.content as DashboardContentState.Content
    }

    private fun TestScope.create(
        currency: String? = "CLP",
        withData: Boolean = true,
        configure: (FakeMovements, FakeCategories) -> Unit = { _, _ -> },
    ): Rig {
        val movements = FakeMovements()
        val categories = FakeCategories().apply { data.value = CategoriesData(listOf(food, transport, old, salary)) }
        if (withData) {
            movements.flowOf(nov).value = MovementsData(november)
            movements.flowOf(oct).value = MovementsData(october)
        }
        configure(movements, categories)
        val connectivity = FakeConnectivity()
        var insufficient = 0
        val vm = DashboardViewModel(
            movements, categories, "f-1", currency, dates, connectivity, onInsufficientRole = { insufficient++ },
        )
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        return Rig(movements, categories, connectivity, vm) { insufficient }
    }

    private fun money(amount: Long) = Money(amount, "CLP")

    // --- Mes y navegación (RF-01, CA-01, CA-02) ---

    @Test
    fun alAbrir_muestraElMesEnCursoConGastosActivos_ySinPoderAvanzar() = runTest {
        val rig = create()

        assertEquals(nov, rig.state.month)
        assertTrue(rig.state.isCurrentMonth)
        assertFalse(rig.state.canGoNext)
        assertEquals(MovementType.EXPENSE, rig.state.activeType)
        assertTrue(rig.state.content is DashboardContentState.Content)
    }

    @Test
    fun anteriorYSiguiente_navegan_ySiguienteNoPasaDelMesEnCurso() = runTest {
        val rig = create()

        rig.vm.nextMonth()
        advanceUntilIdle()
        assertEquals(nov, rig.state.month)

        rig.vm.previousMonth()
        advanceUntilIdle()
        assertEquals(oct, rig.state.month)
        assertTrue(rig.state.canGoNext)
        assertFalse(rig.state.isCurrentMonth)

        rig.vm.previousMonth()
        advanceUntilIdle()
        assertEquals(sep, rig.state.month)

        rig.vm.nextMonth()
        rig.vm.nextMonth()
        rig.vm.nextMonth()
        advanceUntilIdle()
        assertEquals(nov, rig.state.month)
        assertFalse(rig.state.canGoNext)
        assertTrue(rig.state.isCurrentMonth)
    }

    @Test
    fun mesActual_vuelveAlMesEnCurso_ySiYaEstaAhiNoHaceNada() = runTest {
        val rig = create()
        rig.vm.previousMonth()
        rig.vm.previousMonth()
        advanceUntilIdle()
        assertEquals(sep, rig.state.month)

        rig.vm.goToCurrentMonth()
        advanceUntilIdle()
        assertEquals(nov, rig.state.month)
        assertTrue(rig.state.isCurrentMonth)

        val before = rig.movements.refreshed.size
        rig.vm.goToCurrentMonth()
        advanceUntilIdle()
        assertEquals(before, rig.movements.refreshed.size)
    }

    @Test
    fun elMesElegidoMuestraLosMovimientosDeEseMes() = runTest {
        val rig = create()

        rig.vm.previousMonth()
        advanceUntilIdle()

        assertEquals(money(150_000), rig.content.totals.expenses)
        assertEquals(money(0), rig.content.totals.income)
    }

    // --- Tipo activo (RF-03, CA-04) ---

    @Test
    fun cambiarDeTipo_cambiaElRepartoYElPorcentaje_peroNoLosTotales() = runTest {
        val rig = create()
        val totals = rig.content.totals
        assertEquals(listOf("c-food", "c-transport"), rig.content.breakdown?.entries?.map { it.category.id })
        assertEquals(BigDecimal("4.2"), rig.content.percentOfIncome)

        rig.vm.selectType(MovementType.INCOME)
        advanceUntilIdle()

        assertEquals(MovementType.INCOME, rig.state.activeType)
        assertEquals(listOf("c-salary"), rig.content.breakdown?.entries?.map { it.category.id })
        assertEquals(BigDecimal("100.0"), rig.content.percentOfIncome)
        assertEquals(totals, rig.content.totals)

        rig.vm.selectType(MovementType.EXPENSE)
        advanceUntilIdle()
        assertEquals(MovementType.EXPENSE, rig.state.activeType)
    }

    @Test
    fun elTipoActivoSeConservaAlCambiarDeMes() = runTest {
        val rig = create()
        rig.vm.selectType(MovementType.INCOME)

        rig.vm.previousMonth()
        advanceUntilIdle()

        assertEquals(MovementType.INCOME, rig.state.activeType)
        assertEquals(oct, rig.state.month)
    }

    // --- Refrescos (RF-13, CA-16) ---

    @Test
    fun alAbrir_refrescaElMesElAnteriorYLasCategorias_unaVezCadaUno() = runTest {
        val rig = create()

        assertEquals(setOf(nov, oct), rig.movements.refreshed.toSet())
        assertEquals(2, rig.movements.refreshed.size)
        assertEquals(1, rig.categories.refreshes)
    }

    @Test
    fun alCambiarDeMes_refrescaElNuevoMesYSuAnterior() = runTest {
        val rig = create()
        rig.movements.refreshed.clear()

        rig.vm.previousMonth()
        advanceUntilIdle()

        assertEquals(setOf(oct, sep), rig.movements.refreshed.toSet())
        assertEquals(2, rig.movements.refreshed.size)
    }

    @Test
    fun conUnaCargaEnCurso_noSeDuplicaElRefresco() = runTest {
        val rig = create(withData = false) { movements, _ -> movements.gate = CompletableDeferred() }
        assertEquals(2, rig.movements.refreshed.size)

        rig.vm.onStart()
        rig.vm.retry()
        advanceUntilIdle()

        assertEquals(2, rig.movements.refreshed.size)
    }

    @Test
    fun alVolverAPrimerPlano_refrescaDeNuevo() = runTest {
        val rig = create()
        assertEquals(2, rig.movements.refreshed.size)

        rig.vm.onStart()
        advanceUntilIdle()

        assertEquals(4, rig.movements.refreshed.size)
        assertEquals(2, rig.categories.refreshes)
    }

    @Test
    fun alRecuperarLaRed_refresca_peroNoEnLaPrimeraEmision() = runTest {
        val rig = create()
        rig.connectivity.flow.emit(true)
        advanceUntilIdle()
        assertEquals(2, rig.movements.refreshed.size)

        rig.connectivity.flow.emit(false)
        rig.connectivity.flow.emit(true)
        advanceUntilIdle()

        assertEquals(4, rig.movements.refreshed.size)
    }

    // --- Estados: carga, error, copia local (CA-16) ---

    @Test
    fun conCopiaLocalYSinRed_seMuestraElMes_sinError() = runTest {
        val rig = create { movements, _ -> movements.onRefresh = { MovementResult.Failure(MovementFailure.Network) } }

        assertTrue(rig.state.content is DashboardContentState.Content)
        assertFalse(rig.content.isRefreshing)
        assertEquals(money(1_200_000), rig.content.totals.income)
    }

    @Test
    fun laCopiaDeMasDe24Horas_seMarcaComoAntigua() = runTest {
        val rig = create { movements, _ ->
            movements.flowOf(nov).value = MovementsData(november, isStale = true)
        }

        assertTrue(rig.content.isStale)
    }

    @Test
    fun sinCopiaMientrasSeDescarga_esCarga_yConLosDatosSeMuestraElMesYQueSeActualiza() = runTest {
        val gate = CompletableDeferred<Unit>()
        val rig = create(withData = false) { movements, _ -> movements.gate = gate }

        assertEquals(DashboardContentState.Loading, rig.state.content)

        rig.movements.flowOf(nov).value = MovementsData(november)
        advanceUntilIdle()
        assertTrue(rig.content.isRefreshing)

        gate.complete(Unit)
        advanceUntilIdle()
        assertFalse(rig.content.isRefreshing)
    }

    @Test
    fun sinCopiaYConElRefrescoFallido_esError_yReintentarLoRecupera() = runTest {
        val rig = create(withData = false) { movements, _ ->
            movements.onRefresh = { MovementResult.Failure(MovementFailure.Network) }
        }
        assertEquals(DashboardContentState.Error, rig.state.content)

        rig.movements.onRefresh = { MovementResult.Success(Unit) }
        rig.movements.flowOf(nov).value = MovementsData(november)
        rig.vm.retry()
        advanceUntilIdle()

        assertTrue(rig.state.content is DashboardContentState.Content)
        assertEquals(4, rig.movements.refreshed.size)
    }

    @Test
    fun conMonedasMezcladas_esError_noSeSuma() = runTest {
        val rig = create { movements, _ ->
            movements.flowOf(nov).value = MovementsData(listOf(super1, mov("m-usd", MovementType.EXPENSE, 100, 4, currency = "USD")))
        }

        assertEquals(DashboardContentState.Error, rig.state.content)
    }

    // --- Mes vacío (RF-08, CA-15) ---

    @Test
    fun mesSinMovimientos_muestraTodoEnCero_ylasCategoriasActivasEnCero() = runTest {
        val rig = create { movements, _ -> movements.flowOf(nov).value = MovementsData(emptyList()) }

        assertTrue(rig.content.isMonthEmpty)
        assertEquals(money(0), rig.content.totals.expenses)
        assertEquals(money(0), rig.content.totals.income)
        assertEquals(money(0), rig.content.totals.available)
        assertNull(rig.content.percentOfIncome)
        assertEquals(listOf("c-food", "c-transport"), rig.content.breakdown?.entries?.map { it.category.id })
        assertTrue(rig.content.breakdown?.segments?.isEmpty() == true)
    }

    @Test
    fun mesSinMovimientosYSinMonedaDeLaFamilia_esError() = runTest {
        val rig = create(currency = null) { movements, _ -> movements.flowOf(nov).value = MovementsData(emptyList()) }

        assertEquals(DashboardContentState.Error, rig.state.content)
    }

    // --- Comparación con el mes anterior (RF-06, CA-07) ---

    @Test
    fun laComparacionUsaElMismoTramoDeDiasEnElMesEnCurso() = runTest {
        val rig = create()

        // Noviembre del 1 al 16: 50.900; octubre del 1 al 16: 100.000 (el del día 20 no cuenta).
        val comparison = rig.content.comparison
        assertEquals(money(50_900), comparison?.current)
        assertEquals(money(100_000), comparison?.previous)
        assertEquals(BigDecimal("-49.1"), comparison?.variationPercent)
        assertEquals(ChangeDirection.DOWN, comparison?.direction)
        assertEquals(ChangeOutcome.FAVORABLE, comparison?.outcome)
    }

    @Test
    fun enIngresos_sinIngresosElMesAnterior_noHayComparacion() = runTest {
        val rig = create()

        rig.vm.selectType(MovementType.INCOME)
        advanceUntilIdle()

        assertNull(rig.content.comparison)
        assertNotNull(rig.content.breakdown)
    }

    @Test
    fun sinDatosDelMesAnterior_noHayComparacion_yElRestoSeVe() = runTest {
        val rig = create { movements, _ -> movements.flowOf(oct).value = MovementsData() }

        assertNull(rig.content.comparison)
        assertEquals(money(50_900), rig.content.totals.expenses)
        assertNotNull(rig.content.breakdown)
    }

    @Test
    fun siSoloFallaElMesAnterior_noHayComparacion_peroElDashboardSeMuestra() = runTest {
        val rig = create(withData = false) { movements, _ ->
            movements.flowOf(nov).value = MovementsData(november)
            movements.onRefresh = { month ->
                if (month == oct) MovementResult.Failure(MovementFailure.Network) else MovementResult.Success(Unit)
            }
        }

        assertTrue(rig.state.content is DashboardContentState.Content)
        assertNull(rig.content.comparison)
        assertEquals(money(50_900), rig.content.totals.expenses)
    }

    @Test
    fun enUnMesPasado_laComparacionUsaLosMesesCompletos() = runTest {
        val rig = create { movements, _ ->
            movements.flowOf(sep).value = MovementsData(listOf(mov("s-1", MovementType.EXPENSE, 300_000, 29, month = sep)))
        }

        rig.vm.previousMonth()
        advanceUntilIdle()

        // Octubre completo (150.000) frente a septiembre completo (300.000): -50,0 %.
        assertEquals(money(150_000), rig.content.comparison?.current)
        assertEquals(money(300_000), rig.content.comparison?.previous)
        assertEquals(BigDecimal("-50.0"), rig.content.comparison?.variationPercent)
    }

    // --- Cambios en los movimientos (RF-13, CA-12) ---

    @Test
    fun registrarEditarOEliminarUnMovimiento_seReflejaSinRecargar() = runTest {
        val rig = create()
        assertEquals(money(50_900), rig.content.totals.expenses)

        val nuevo = mov("m-nuevo", MovementType.EXPENSE, 20_000, day = 16, categoryId = "c-transport")
        rig.movements.flowOf(nov).value = MovementsData(november + nuevo)
        advanceUntilIdle()

        assertEquals(money(70_900), rig.content.totals.expenses)
        assertEquals(money(20_000), rig.content.breakdown?.entries?.first { it.category.id == "c-transport" }?.amount)

        rig.movements.flowOf(nov).value = MovementsData(november - taxi)
        advanceUntilIdle()
        assertEquals(money(45_900), rig.content.totals.expenses)
        assertEquals(money(1_154_100), rig.content.totals.available)
    }

    // --- Categorías (RF-07, RF-14) ---

    @Test
    fun sinDatosDeCategorias_losTotalesSeVen_yElRepartoEsperaALasCategorias() = runTest {
        val rig = create { _, categories -> categories.data.value = CategoriesData() }

        assertNull(rig.content.breakdown)
        assertEquals(money(50_900), rig.content.totals.expenses)

        rig.categories.data.value = CategoriesData(listOf(food, transport))
        advanceUntilIdle()
        assertNotNull(rig.content.breakdown)
        assertEquals(2, rig.content.breakdown?.count)
    }

    @Test
    fun siFallaElRefrescoDeCategorias_losTotalesSeVenIgual() = runTest {
        val rig = create { _, categories ->
            categories.data.value = CategoriesData()
            categories.refreshFails = true
        }

        assertTrue(rig.state.content is DashboardContentState.Content)
        assertNull(rig.content.breakdown)
    }

    @Test
    fun laCategoriaArchivadaConMovimientosAparece_yLaDeOtroTipoNo() = runTest {
        val rig = create { movements, _ ->
            movements.flowOf(nov).value = MovementsData(november + mov("m-old", MovementType.EXPENSE, 700, 5, categoryId = "c-old"))
        }

        assertEquals(listOf("c-food", "c-transport", "c-old"), rig.content.breakdown?.entries?.map { it.category.id })
        assertEquals(3, rig.content.breakdown?.count)
    }

    // --- Rol y estado (CA-17) ---

    @Test
    fun unaRespuestaNoPermitida_pideReevaluarLaFamilia() = runTest {
        val rig = create(withData = false) { movements, _ ->
            movements.onRefresh = { MovementResult.Failure(MovementFailure.NotAllowed) }
        }

        assertTrue(rig.insufficientRole() >= 1)
    }

    @Test
    fun otrosFallosNoPidenReevaluarLaFamilia() = runTest {
        val rig = create(withData = false) { movements, _ ->
            movements.onRefresh = { MovementResult.Failure(MovementFailure.Network) }
        }

        assertEquals(0, rig.insufficientRole())
    }

    @Test
    fun mesYTipoViveEnElViewModel_nodependenDeQueHayaPantallaObservando() = runTest {
        val movements = FakeMovements().apply {
            flowOf(nov).value = MovementsData(november)
            flowOf(oct).value = MovementsData(october)
        }
        val categories = FakeCategories().apply { data.value = CategoriesData(listOf(food, salary)) }
        val vm = DashboardViewModel(movements, categories, "f-1", "CLP", dates, FakeConnectivity())

        vm.previousMonth()
        vm.selectType(MovementType.INCOME)
        advanceUntilIdle()

        val state = vm.uiState.first { it.content is DashboardContentState.Content && it.month == oct }
        assertEquals(oct, state.month)
        assertEquals(MovementType.INCOME, state.activeType)
    }
}
