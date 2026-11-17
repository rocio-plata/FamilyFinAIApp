package com.rocioplata.familyfinaiapp.ui.movements

import com.rocioplata.familyfinaiapp.domain.categories.CategoriesData
import com.rocioplata.familyfinaiapp.domain.categories.Category
import com.rocioplata.familyfinaiapp.domain.categories.CategoryStatus
import com.rocioplata.familyfinaiapp.domain.categories.CategoryType
import com.rocioplata.familyfinaiapp.domain.categories.Tag
import com.rocioplata.familyfinaiapp.domain.categories.TagStatus
import com.rocioplata.familyfinaiapp.domain.header.FamilyRole
import com.rocioplata.familyfinaiapp.domain.members.FamilyMember
import com.rocioplata.familyfinaiapp.domain.members.FamilyMembersData
import com.rocioplata.familyfinaiapp.domain.money.Money
import com.rocioplata.familyfinaiapp.domain.movements.Movement
import com.rocioplata.familyfinaiapp.domain.movements.MovementDates
import com.rocioplata.familyfinaiapp.domain.movements.MovementFailure
import com.rocioplata.familyfinaiapp.domain.movements.MovementResult
import com.rocioplata.familyfinaiapp.domain.movements.MovementType
import com.rocioplata.familyfinaiapp.domain.movements.MovementsData
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethod
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethodStatus
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethodsData
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class MovementsViewModelTest {
    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val dates = MovementDates(Clock.fixed(Instant.parse("2026-11-16T15:00:00Z"), ZoneId.of("UTC")))
    private val nov = YearMonth.of(2026, 11)
    private val oct = YearMonth.of(2026, 10)

    private val food = Category(
        id = "c-food", type = CategoryType.EXPENSE, name = "Comida", icon = "restaurant",
        status = CategoryStatus.ACTIVE,
        tags = listOf(Tag("t-super", "Supermercado", TagStatus.ACTIVE, 0)),
    )
    private val salary = Category(
        id = "c-salary", type = CategoryType.INCOME, name = "Sueldo", icon = null,
        status = CategoryStatus.DEPRECATED, tags = emptyList(),
    )
    private val card = PaymentMethod("m-card", "Tarjeta", PaymentMethodStatus.ACTIVE)
    private val rocio = FamilyMember("u-1", "Rocío Plata", FamilyRole.OWNER)
    private val luis = FamilyMember("u-2", "Luis", FamilyRole.MEMBER)

    private fun clp(amount: Long) = Money(amount, "CLP")

    private fun mov(
        id: String,
        type: MovementType,
        amount: Long,
        day: Int,
        created: Long = 0,
        month: YearMonth = nov,
        categoryId: String = if (type == MovementType.EXPENSE) "c-food" else "c-salary",
        tagId: String? = null,
        paymentMethodId: String? = "m-card",
        recordedBy: String = "u-1",
        currency: String = "CLP",
    ) = Movement(
        id = id, type = type, amount = Money(amount, currency), categoryId = categoryId, tagId = tagId,
        paymentMethodId = paymentMethodId, title = "Mov $id", note = null, date = month.atDay(day),
        recordedBy = recordedBy, createdAt = Instant.ofEpochSecond(created),
    )

    // Del más reciente al más antiguo, como los entrega el repositorio.
    private val sueldo = mov("m-sueldo", MovementType.INCOME, 1_200_000, day = 15)
    private val super1 = mov("m-super", MovementType.EXPENSE, 45_900, day = 15, created = 100, tagId = "t-super")
    private val cine = mov("m-cine", MovementType.EXPENSE, 10_000, day = 15, created = 50, recordedBy = "u-2")
    private val taxi = mov("m-taxi", MovementType.EXPENSE, 5_000, day = 3)
    private val month = listOf(super1, cine, sueldo, taxi) // día 15 con createdAt desc, luego día 3

    private class Rig(
        val movements: FakeMovements,
        val categories: FakeCategories,
        val paymentMethods: FakePaymentMethods,
        val members: FakeMembers,
        val connectivity: FakeConnectivity,
        val vm: MovementsViewModel,
        val insufficientRole: () -> Int,
    ) {
        val state get() = vm.uiState.value
        val content get() = state.list as MovementsListState.Content
    }

    private fun TestScope.create(
        currency: String? = "CLP",
        currentUserId: String? = "u-1",
        configure: (FakeMovements, FakeCategories, FakePaymentMethods, FakeMembers) -> Unit = { _, _, _, _ -> },
    ): Rig {
        val movements = FakeMovements()
        val categories = FakeCategories().apply { data.value = CategoriesData(listOf(food, salary)) }
        val paymentMethods = FakePaymentMethods().apply { data.value = PaymentMethodsData(listOf(card)) }
        val members = FakeMembers().apply { data.value = FamilyMembersData(listOf(rocio, luis)) }
        configure(movements, categories, paymentMethods, members)
        val connectivity = FakeConnectivity()
        var insufficient = 0
        val vm = MovementsViewModel(
            movements, categories, paymentMethods, members, "f-1", currency, currentUserId, dates, connectivity,
            onInsufficientRole = { insufficient++ },
        )
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        return Rig(movements, categories, paymentMethods, members, connectivity, vm) { insufficient }
    }

    private fun TestScope.createWithMonth(rows: List<Movement> = month) = create { movements, _, _, _ ->
        movements.flowOf(nov).value = MovementsData(rows)
    }

    // --- Mes y navegación (CA-01) ---

    @Test
    fun alAbrir_muestraElMesEnCursoSinPoderAvanzar_yConTodosLosTipos() = runTest {
        val rig = createWithMonth()

        assertEquals(nov, rig.state.month)
        assertFalse(rig.state.canGoNext)
        assertEquals(MovementFilter.ALL, rig.state.filter)
    }

    @Test
    fun mesAnteriorYSiguiente_navegan_ySiguienteNoPasaDelActual() = runTest {
        val rig = createWithMonth()

        rig.vm.nextMonth()
        advanceUntilIdle()
        assertEquals(nov, rig.state.month)

        rig.vm.previousMonth()
        advanceUntilIdle()
        assertEquals(oct, rig.state.month)
        assertTrue(rig.state.canGoNext)

        rig.vm.previousMonth()
        advanceUntilIdle()
        assertEquals(YearMonth.of(2026, 9), rig.state.month)

        rig.vm.nextMonth()
        rig.vm.nextMonth()
        rig.vm.nextMonth()
        advanceUntilIdle()
        assertEquals(nov, rig.state.month)
        assertFalse(rig.state.canGoNext)
    }

    @Test
    fun elMesElegidoMuestraLosMovimientosDeEseMes() = runTest {
        val rig = createWithMonth()
        rig.movements.flowOf(oct).value = MovementsData(listOf(mov("m-oct", MovementType.EXPENSE, 700, 9, month = oct)))

        rig.vm.previousMonth()
        advanceUntilIdle()

        assertEquals(listOf("m-oct"), rig.content.groups.flatMap { it.rows }.map { it.id })
    }

    // --- Filtro (CA-03) ---

    @Test
    fun filtro_muestraSoloElTipoElegido_yConTodosVeAmbos() = runTest {
        val rig = createWithMonth()

        rig.vm.selectFilter(MovementFilter.EXPENSES)
        advanceUntilIdle()
        assertEquals(listOf("m-super", "m-cine", "m-taxi"), rig.content.groups.flatMap { it.rows }.map { it.id })

        rig.vm.selectFilter(MovementFilter.INCOMES)
        advanceUntilIdle()
        assertEquals(listOf("m-sueldo"), rig.content.groups.flatMap { it.rows }.map { it.id })

        rig.vm.selectFilter(MovementFilter.ALL)
        advanceUntilIdle()
        assertEquals(4, rig.content.groups.flatMap { it.rows }.size)
    }

    @Test
    fun elFiltro_seMantieneAlCambiarDeMes() = runTest {
        val rig = createWithMonth()
        rig.movements.flowOf(oct).value = MovementsData(
            listOf(
                mov("m-oct-g", MovementType.EXPENSE, 700, 9, month = oct),
                mov("m-oct-i", MovementType.INCOME, 900, 8, month = oct),
            ),
        )
        rig.vm.selectFilter(MovementFilter.INCOMES)

        rig.vm.previousMonth()
        advanceUntilIdle()

        assertEquals(MovementFilter.INCOMES, rig.state.filter)
        assertEquals(listOf("m-oct-i"), rig.content.groups.flatMap { it.rows }.map { it.id })
    }

    // --- Grupos, orden y filas (CA-02, CA-24) ---

    @Test
    fun agrupaPorDiaConservandoElOrdenDelRepositorio() = runTest {
        val rig = createWithMonth()

        assertEquals(listOf(nov.atDay(15), nov.atDay(3)), rig.content.groups.map { it.date })
        assertEquals(listOf("m-super", "m-cine", "m-sueldo"), rig.content.groups[0].rows.map { it.id })
        assertEquals(listOf("m-taxi"), rig.content.groups[1].rows.map { it.id })
    }

    @Test
    fun cadaFilaTraeSuCategoriaIconoEtiquetaMedioYQuienLoRegistro() = runTest {
        val rig = createWithMonth()
        val row = rig.content.groups[0].rows.first { it.id == "m-super" }

        assertEquals("Comida", row.categoryName)
        assertEquals("restaurant", row.categoryIcon)
        assertEquals("Supermercado", row.tagName)
        assertEquals("Tarjeta", row.paymentMethodName)
        assertEquals(clp(45_900), row.amount)
        assertEquals(MovementType.EXPENSE, row.type)
        assertEquals(MemberLabel.You("Rocío Plata", FamilyRole.OWNER), row.recordedBy)
        assertEquals(
            MemberLabel.Named("Luis", FamilyRole.MEMBER),
            rig.content.groups[0].rows.first { it.id == "m-cine" }.recordedBy,
        )
    }

    @Test
    fun unaCategoriaArchivadaSeResuelveConSuNombre() = runTest {
        val rig = createWithMonth()

        assertEquals("Sueldo", rig.content.groups[0].rows.first { it.id == "m-sueldo" }.categoryName)
    }

    @Test
    fun categoriaMedioOMiembroNoEncontrados_quedanNulosOExMiembro() = runTest {
        val orphan = mov(
            "m-x", MovementType.EXPENSE, 100, 2, categoryId = "c-gone", tagId = "t-gone",
            paymentMethodId = "m-gone", recordedBy = "u-9",
        )
        val rig = createWithMonth(listOf(orphan))
        val row = rig.content.groups.single().rows.single()

        assertNull(row.categoryName)
        assertNull(row.categoryIcon)
        assertNull(row.tagName)
        assertNull(row.paymentMethodName)
        assertEquals(MemberLabel.FormerMember, row.recordedBy)
    }

    @Test
    fun sinMedioDePagoEnElBackend_quedaNulo() = runTest {
        val rig = createWithMonth(listOf(mov("m-x", MovementType.EXPENSE, 100, 2, paymentMethodId = null)))

        assertNull(rig.content.groups.single().rows.single().paymentMethodName)
    }

    @Test
    fun sinListaDeMiembros_quienLoRegistroNoEstaDisponible_peroElPropioUsuarioSigueSiendoTu() = runTest {
        val rig = create { movements, _, _, members ->
            movements.flowOf(nov).value = MovementsData(month)
            members.data.value = FamilyMembersData(null)
        }
        val rows = rig.content.groups[0].rows

        assertEquals(MemberLabel.Unavailable, rows.first { it.id == "m-cine" }.recordedBy)
        assertEquals(MemberLabel.You(null, null), rows.first { it.id == "m-super" }.recordedBy)
    }

    // --- Resumen y subtotales (CA-26) ---

    @Test
    fun resumenDelMes_ingresosGastosYBalance() = runTest {
        val rig = createWithMonth()
        val summary = rig.content.summary!!

        assertEquals(clp(1_200_000), summary.income)
        assertEquals(clp(60_900), summary.expenses)
        assertEquals(clp(1_139_100), summary.balance)
    }

    @Test
    fun balanceNegativo() = runTest {
        val rig = createWithMonth(listOf(mov("g", MovementType.EXPENSE, 1_448_350, 4), mov("i", MovementType.INCOME, 1_200_000, 5)))

        assertEquals(clp(-248_350), rig.content.summary!!.balance)
    }

    @Test
    fun subtotalDeCadaDia_esIngresosMenosGastos() = runTest {
        val rig = createWithMonth()

        assertEquals(clp(1_200_000 - 45_900 - 10_000), rig.content.groups[0].subtotal)
        assertEquals(clp(-5_000), rig.content.groups[1].subtotal)
    }

    @Test
    fun conCadaFiltro_elResumenNoCambia_ylosSubtotalesSoloCuentanLoVisible() = runTest {
        val rig = createWithMonth()
        val summary = rig.content.summary

        rig.vm.selectFilter(MovementFilter.EXPENSES)
        advanceUntilIdle()
        assertEquals(summary, rig.content.summary)
        assertEquals(clp(-55_900), rig.content.groups[0].subtotal)
        assertEquals(clp(-5_000), rig.content.groups[1].subtotal)

        rig.vm.selectFilter(MovementFilter.INCOMES)
        advanceUntilIdle()
        assertEquals(summary, rig.content.summary)
        assertEquals(clp(1_200_000), rig.content.groups.single().subtotal)
    }

    @Test
    fun trasRegistrarEditarYEliminar_resumenYSubtotalesSeActualizan() = runTest {
        val rig = createWithMonth()
        val flow = rig.movements.flowOf(nov)

        val nuevo = mov("m-nuevo", MovementType.EXPENSE, 20_000, 3, created = 500)
        flow.value = MovementsData(listOf(super1, cine, sueldo, nuevo, taxi))
        advanceUntilIdle()
        assertEquals(clp(80_900), rig.content.summary!!.expenses)
        assertEquals(clp(-25_000), rig.content.groups[1].subtotal)

        flow.value = MovementsData(listOf(super1, cine, sueldo, nuevo, taxi.copy(amount = clp(8_000))))
        advanceUntilIdle()
        assertEquals(clp(83_900), rig.content.summary!!.expenses)
        assertEquals(clp(-28_000), rig.content.groups[1].subtotal)

        flow.value = MovementsData(listOf(super1, cine, sueldo))
        advanceUntilIdle()
        assertEquals(clp(55_900), rig.content.summary!!.expenses)
        assertEquals(1, rig.content.groups.size)
    }

    // --- Estados (CA-16) ---

    @Test
    fun mientrasCarga_sinDatos_esLoading() = runTest {
        val gate = CompletableDeferred<Unit>()
        val rig = create { movements, _, _, _ -> movements.gate = gate }

        assertEquals(MovementsListState.Loading, rig.state.list)
        gate.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun sinDatosYConFallo_esErrorYReintentarLoRecupera() = runTest {
        val rig = create { movements, _, _, _ ->
            movements.onRefresh = { MovementResult.Failure(MovementFailure.Network) }
        }
        assertEquals(MovementsListState.Error, rig.state.list)
        assertEquals(nov, rig.state.month)

        rig.movements.onRefresh = {
            rig.movements.flowOf(nov).value = MovementsData(month)
            MovementResult.Success(Unit)
        }
        rig.vm.retry()
        advanceUntilIdle()

        assertTrue(rig.state.list is MovementsListState.Content)
    }

    @Test
    fun conFalloPeroConCopia_muestraElContenidoYNoElError() = runTest {
        val rig = create { movements, _, _, _ ->
            movements.flowOf(nov).value = MovementsData(month, isStale = true)
            movements.onRefresh = { MovementResult.Failure(MovementFailure.Offline) }
        }

        assertTrue(rig.content.isStale)
        assertEquals(4, rig.content.groups.flatMap { it.rows }.size)
    }

    @Test
    fun datosRecientes_noEstanMarcadosComoAntiguos() = runTest {
        assertFalse(createWithMonth().content.isStale)
    }

    @Test
    fun mesSinMovimientos_esVacio_conResumenEnCero() = runTest {
        val rig = createWithMonth(emptyList())

        assertTrue(rig.content.isMonthEmpty)
        assertFalse(rig.content.isFilterEmpty)
        assertEquals(clp(0), rig.content.summary!!.balance)
        assertTrue(rig.content.groups.isEmpty())
    }

    @Test
    fun mesVacioSinMonedaDeLaFamilia_noTieneResumen() = runTest {
        val rig = create(currency = null) { movements, _, _, _ -> movements.flowOf(nov).value = MovementsData(emptyList()) }

        assertTrue(rig.content.isMonthEmpty)
        assertNull(rig.content.summary)
    }

    @Test
    fun filtroSinResultados_noEsMesVacio() = runTest {
        val rig = createWithMonth(listOf(taxi))

        rig.vm.selectFilter(MovementFilter.INCOMES)
        advanceUntilIdle()

        assertTrue(rig.content.isFilterEmpty)
        assertFalse(rig.content.isMonthEmpty)
        assertEquals(clp(5_000), rig.content.summary!!.expenses)
    }

    @Test
    fun monedasMezcladas_noSeSuman_yEsError() = runTest {
        val rig = createWithMonth(listOf(taxi, mov("m-usd", MovementType.EXPENSE, 100, 4, currency = "USD")))

        assertEquals(MovementsListState.Error, rig.state.list)
    }

    @Test
    fun alCambiarDeMes_hastaQueLleganLosDatos_esLoading() = runTest {
        val rig = createWithMonth()
        rig.movements.gate = CompletableDeferred()

        rig.vm.previousMonth()
        advanceUntilIdle()

        assertEquals(oct, rig.state.month)
        assertEquals(MovementsListState.Loading, rig.state.list)
        assertTrue(rig.state.canGoNext)
    }

    @Test
    fun refrescando_seIndicaSoloMientrasHayUnaCargaDelMesMostrado() = runTest {
        val rig = createWithMonth()
        assertFalse(rig.content.isRefreshing)

        val gate = CompletableDeferred<Unit>()
        rig.movements.gate = gate
        rig.vm.onStart()
        advanceUntilIdle()
        assertTrue(rig.content.isRefreshing)

        gate.complete(Unit)
        advanceUntilIdle()
        assertFalse(rig.content.isRefreshing)
    }

    // --- Refrescos ---

    @Test
    fun alAbrir_refrescaElMesYLosNombres() = runTest {
        val rig = createWithMonth()

        assertEquals(listOf(nov), rig.movements.refreshed)
        assertEquals(1, rig.categories.refreshes)
        assertEquals(1, rig.paymentMethods.refreshes)
        assertEquals(1, rig.members.refreshes)
    }

    @Test
    fun alCambiarDeMes_refrescaEseMes() = runTest {
        val rig = createWithMonth()

        rig.vm.previousMonth()
        advanceUntilIdle()

        assertEquals(listOf(nov, oct), rig.movements.refreshed)
    }

    @Test
    fun enOnStart_refrescaElMesMostrado() = runTest {
        val rig = createWithMonth()

        rig.vm.onStart()
        advanceUntilIdle()

        assertEquals(listOf(nov, nov), rig.movements.refreshed)
        assertEquals(2, rig.members.refreshes)
    }

    @Test
    fun alRecuperarLaRed_refresca_peroNoEnLaPrimeraEmision() = runTest {
        val rig = createWithMonth()
        rig.connectivity.flow.emit(true)
        advanceUntilIdle()
        assertEquals(1, rig.movements.refreshed.size)

        rig.connectivity.flow.emit(false)
        rig.connectivity.flow.emit(true)
        advanceUntilIdle()

        assertEquals(2, rig.movements.refreshed.size)
    }

    @Test
    fun conUnaCargaEnCurso_otroRefrescoDelMismoMesNoLaDuplica() = runTest {
        val gate = CompletableDeferred<Unit>()
        val rig = create { movements, _, _, _ -> movements.gate = gate }

        rig.vm.onStart()
        rig.vm.retry()
        advanceUntilIdle()
        assertEquals(listOf(nov), rig.movements.refreshed)

        gate.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun siElBackendRechazaPorRol_avisaAlMain() = runTest {
        val rig = create { movements, _, _, _ ->
            movements.onRefresh = { MovementResult.Failure(MovementFailure.NotAllowed) }
        }

        assertEquals(1, rig.insufficientRole())
    }

    @Test
    fun otrosFallos_noAvisanDeRol() = runTest {
        val rig = create { movements, _, _, _ ->
            movements.onRefresh = { MovementResult.Failure(MovementFailure.Network) }
        }

        assertEquals(0, rig.insufficientRole())
    }
}
