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
import com.rocioplata.familyfinaiapp.domain.movements.MovementFailure
import com.rocioplata.familyfinaiapp.domain.movements.MovementType
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
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class MovementDetailViewModelTest {
    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val food = Category(
        "c-food", CategoryType.EXPENSE, "Comida", "restaurant", CategoryStatus.ACTIVE,
        listOf(Tag("t-super", "supermercado", TagStatus.ACTIVE, 0), Tag("t-old", "antigua", TagStatus.DEPRECATED, 1)),
    )
    private val oldCategory = Category("c-old", CategoryType.EXPENSE, "Vieja", null, CategoryStatus.DEPRECATED, emptyList())
    private val card = PaymentMethod("m-card", "Tarjeta", PaymentMethodStatus.ACTIVE)
    private val oldMethod = PaymentMethod("m-old", "Antigua", PaymentMethodStatus.DEPRECATED)
    private val rocio = FamilyMember("u-1", "Rocío Plata", FamilyRole.OWNER)
    private val luis = FamilyMember("u-2", "Luis", FamilyRole.MEMBER)

    private val movement = Movement(
        id = "m-1", type = MovementType.EXPENSE, amount = Money(45_900, "CLP"), categoryId = "c-food",
        tagId = "t-super", paymentMethodId = "m-card", title = "Supermercado", note = "Compra semanal",
        date = LocalDate.of(2026, 11, 12), recordedBy = "u-1", createdAt = Instant.EPOCH,
    )

    private class Rig(
        val movements: FakeMovements,
        val categories: FakeCategories,
        val paymentMethods: FakePaymentMethods,
        val members: FakeMembers,
        val vm: MovementDetailViewModel,
        val closed: () -> Int,
        val insufficientRole: () -> Int,
    ) {
        val state get() = vm.uiState.value
        val content get() = state as MovementDetailUiState.Content
    }

    private fun TestScope.create(
        movement: Movement? = this@MovementDetailViewModelTest.movement,
        emit: Boolean = true,
        currentUserId: String? = "u-1",
        configure: (FakeMovements, FakeCategories, FakePaymentMethods, FakeMembers) -> Unit = { _, _, _, _ -> },
    ): Rig {
        val movements = FakeMovements()
        val categories = FakeCategories().apply { data.value = CategoriesData(listOf(food, oldCategory)) }
        val paymentMethods = FakePaymentMethods().apply { data.value = PaymentMethodsData(listOf(card, oldMethod)) }
        val members = FakeMembers().apply { data.value = FamilyMembersData(listOf(rocio, luis)) }
        configure(movements, categories, paymentMethods, members)
        if (emit) movements.detail.tryEmit(movement)
        var closed = 0
        var insufficient = 0
        val vm = MovementDetailViewModel(
            movements, categories, paymentMethods, members, "f-1", "m-1", currentUserId,
            onClose = { closed++ }, onInsufficientRole = { insufficient++ },
        )
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        return Rig(movements, categories, paymentMethods, members, vm, { closed }, { insufficient })
    }

    // --- Datos resueltos (CA-10, CA-24) ---

    @Test
    fun alAbrir_resuelveTodosLosDatos() = runTest {
        val rig = create()
        val c = rig.content

        assertEquals("m-1", c.id)
        assertEquals(MovementType.EXPENSE, c.type)
        assertEquals(Money(45_900, "CLP"), c.amount)
        assertEquals("Supermercado", c.title)
        assertEquals("Compra semanal", c.note)
        assertEquals(LocalDate.of(2026, 11, 12), c.date)
        assertEquals(ResolvedItem("Comida", false), c.category)
        assertEquals("restaurant", c.categoryIcon)
        assertEquals(ResolvedItem("supermercado", false), c.tag)
        assertEquals(ResolvedItem("Tarjeta", false), c.paymentMethod)
        assertEquals(MemberLabel.You("Rocío Plata", FamilyRole.OWNER), c.recordedBy)
        assertFalse(c.confirmingDelete)
        assertFalse(c.isDeleting)
        assertNull(c.notice)
    }

    @Test
    fun alAbrir_refrescaLosNombres() = runTest {
        val rig = create()

        assertEquals(1, rig.categories.refreshes)
        assertEquals(1, rig.paymentMethods.refreshes)
        assertEquals(1, rig.members.refreshes)
    }

    @Test
    fun sinEtiquetaNiObservacionesNiMedio_quedanNulos() = runTest {
        val rig = create(movement.copy(tagId = null, note = null, paymentMethodId = null))

        assertNull(rig.content.tag)
        assertNull(rig.content.note)
        assertNull(rig.content.paymentMethod)
    }

    @Test
    fun categoriaEtiquetaYMedioArchivados_seIndican() = runTest {
        val rig = create(movement.copy(categoryId = "c-old", tagId = null, paymentMethodId = "m-old"))
        assertEquals(ResolvedItem("Vieja", true), rig.content.category)
        assertEquals(ResolvedItem("Antigua", true), rig.content.paymentMethod)

        val withOldTag = create(movement.copy(tagId = "t-old"))
        assertEquals(ResolvedItem("antigua", true), withOldTag.content.tag)
        assertEquals(ResolvedItem("Comida", false), withOldTag.content.category)
    }

    @Test
    fun elementosNoEncontrados_quedanSinNombreYNoArchivados() = runTest {
        val rig = create(movement.copy(categoryId = "c-gone", tagId = "t-gone", paymentMethodId = "m-gone"))

        assertEquals(ResolvedItem(null, false), rig.content.category)
        assertNull(rig.content.categoryIcon)
        assertEquals(ResolvedItem(null, false), rig.content.tag)
        assertEquals(ResolvedItem(null, false), rig.content.paymentMethod)
    }

    @Test
    fun quienLoRegistro_conCadaCaso() = runTest {
        assertEquals(
            MemberLabel.Named("Luis", FamilyRole.MEMBER),
            create(movement.copy(recordedBy = "u-2")).content.recordedBy,
        )
        assertEquals(MemberLabel.FormerMember, create(movement.copy(recordedBy = "u-9")).content.recordedBy)
        assertEquals(
            MemberLabel.NoName(FamilyRole.MEMBER),
            create(movement.copy(recordedBy = "u-3")) { _, _, _, members ->
                members.data.value = FamilyMembersData(listOf(FamilyMember("u-3", "  ", FamilyRole.MEMBER)))
            }.content.recordedBy,
        )
        assertEquals(
            MemberLabel.Unavailable,
            create(movement.copy(recordedBy = "u-2")) { _, _, _, members ->
                members.data.value = FamilyMembersData(null)
            }.content.recordedBy,
        )
    }

    @Test
    fun elPropioUsuario_esTuAunqueNoHayaListaDeMiembros() = runTest {
        val rig = create { _, _, _, members -> members.data.value = FamilyMembersData(null) }

        assertEquals(MemberLabel.You(null, null), rig.content.recordedBy)
    }

    @Test
    fun alLlegarLosNombres_elDetalleSeActualiza() = runTest {
        val rig = create { _, categories, _, _ -> categories.data.value = CategoriesData(null) }
        assertEquals(ResolvedItem(null, false), rig.content.category)

        rig.categories.data.value = CategoriesData(listOf(food))
        advanceUntilIdle()

        assertEquals(ResolvedItem("Comida", false), rig.content.category)
    }

    @Test
    fun siElMovimientoSeEdita_elDetalleMuestraLosNuevosDatos() = runTest {
        val rig = create()

        rig.movements.detail.tryEmit(movement.copy(title = "Super Jumbo", amount = Money(50_000, "CLP")))
        advanceUntilIdle()

        assertEquals("Super Jumbo", rig.content.title)
        assertEquals(Money(50_000, "CLP"), rig.content.amount)
    }

    @Test
    fun antesDeLeerElMovimiento_esLoading() = runTest {
        val rig = create(emit = false)

        assertEquals(MovementDetailUiState.Loading, rig.state)
        assertEquals(0, rig.closed())
    }

    // --- Eliminar (CA-14, CA-17, CA-18) ---

    @Test
    fun pedirEliminar_muestraLaConfirmacion_sinLlamarAlBackend() = runTest {
        val rig = create()

        rig.vm.requestDelete()
        advanceUntilIdle()

        assertTrue(rig.content.confirmingDelete)
        assertTrue(rig.movements.deleted.isEmpty())
    }

    @Test
    fun cancelar_noLlamaYNoCambiaNada() = runTest {
        val rig = create()
        rig.vm.requestDelete()

        rig.vm.cancelDelete()
        advanceUntilIdle()

        assertFalse(rig.content.confirmingDelete)
        assertTrue(rig.movements.deleted.isEmpty())
        assertEquals(0, rig.closed())
        assertEquals("Supermercado", rig.content.title)
    }

    @Test
    fun confirmar_llamaUnaVez_yCierraElDetalle() = runTest {
        val rig = create()
        rig.vm.requestDelete()

        rig.vm.confirmDelete()
        advanceUntilIdle()

        assertEquals(listOf("m-1"), rig.movements.deleted)
        assertEquals(1, rig.closed())
        assertEquals(MovementDetailUiState.Loading, rig.state)
    }

    @Test
    fun confirmarSinHaberPedido_noHaceNada() = runTest {
        val rig = create()

        rig.vm.confirmDelete()
        advanceUntilIdle()

        assertTrue(rig.movements.deleted.isEmpty())
    }

    @Test
    fun dosConfirmaciones_hacenUnaSolaLlamada_yMientrasTantoNoSePuedeCancelarNiCerrar() = runTest {
        val gate = CompletableDeferred<Unit>()
        val rig = create { movements, _, _, _ -> movements.deleteGate = gate }
        rig.vm.requestDelete()

        rig.vm.confirmDelete()
        rig.vm.confirmDelete()
        advanceUntilIdle()
        assertTrue(rig.content.isDeleting)
        rig.vm.cancelDelete()
        rig.vm.requestClose()
        rig.vm.requestDelete()
        advanceUntilIdle()
        assertTrue(rig.content.isDeleting)
        assertEquals(0, rig.closed())

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, rig.movements.deleted.size)
        assertEquals(1, rig.closed())
    }

    @Test
    fun alEliminar_elDetalleSeCierraUnaSolaVez_aunqueElMovimientoDesaparezca() = runTest {
        val rig = create()
        rig.vm.requestDelete()
        rig.vm.confirmDelete()
        advanceUntilIdle()

        rig.vm.requestClose()
        advanceUntilIdle()

        assertEquals(1, rig.closed())
    }

    private fun TestScope.deleteFailing(failure: MovementFailure): Rig =
        create { movements, _, _, _ -> movements.deleteFailure = failure }.also {
            it.vm.requestDelete()
            it.vm.confirmDelete()
            advanceUntilIdle()
        }

    @Test
    fun sinConexionYDemasFallos_avisanSinCambiosYSePuedeReintentar() = runTest {
        listOf(
            MovementFailure.Offline, MovementFailure.Network, MovementFailure.Unknown,
            MovementFailure.Unauthorized, MovementFailure.InvalidData,
        ).forEach { failure ->
            val rig = deleteFailing(failure)

            assertEquals(failure.toString(), failure, rig.content.notice)
            assertFalse(failure.toString(), rig.content.isDeleting)
            assertFalse(failure.toString(), rig.content.confirmingDelete)
            assertEquals(failure.toString(), "Supermercado", rig.content.title)
            assertEquals(failure.toString(), 0, rig.closed())

            rig.movements.deleteFailure = null
            rig.vm.requestDelete()
            rig.vm.confirmDelete()
            advanceUntilIdle()
            assertEquals(failure.toString(), 2, rig.movements.deleted.size)
            assertEquals(failure.toString(), 1, rig.closed())
        }
    }

    @Test
    fun sinPermiso_avisaYPideReevaluarElRol() = runTest {
        val rig = deleteFailing(MovementFailure.NotAllowed)

        assertEquals(MovementFailure.NotAllowed, rig.content.notice)
        assertEquals(1, rig.insufficientRole())
        assertEquals(0, rig.closed())
    }

    @Test
    fun consumirElAviso_loQuita() = runTest {
        val rig = deleteFailing(MovementFailure.Offline)

        rig.vm.consumeNotice()
        advanceUntilIdle()

        assertNull(rig.content.notice)
    }

    @Test
    fun noEncontrado_informaYRefrescaElMesDelMovimiento_sinCerrarSolo() = runTest {
        val rig = deleteFailing(MovementFailure.NotFound)

        assertEquals(MovementDetailUiState.Gone, rig.state)
        assertEquals(YearMonth.of(2026, 11), rig.movements.refreshed.last())
        assertEquals(0, rig.closed())

        rig.vm.requestClose()
        advanceUntilIdle()
        assertEquals(1, rig.closed())
    }

    // --- El movimiento desaparece ---

    @Test
    fun siElMovimientoDesaparece_seInformaYSeVuelveAlListado() = runTest {
        val rig = create()

        rig.movements.detail.tryEmit(null)
        advanceUntilIdle()
        assertEquals(MovementDetailUiState.Gone, rig.state)
        assertEquals(0, rig.closed())

        rig.vm.requestClose()
        advanceUntilIdle()
        assertEquals(1, rig.closed())
    }

    @Test
    fun siNoEstabaEnMemoria_esGone() = runTest {
        val rig = create(movement = null)

        assertEquals(MovementDetailUiState.Gone, rig.state)
    }

    @Test
    fun cerrarSinMasCierraUnaVez() = runTest {
        val rig = create()

        rig.vm.requestClose()
        rig.vm.requestClose()
        advanceUntilIdle()

        assertEquals(1, rig.closed())
    }

    @Test
    fun noSePuedePedirEliminarUnMovimientoQueYaNoExiste() = runTest {
        val rig = create(movement = null)

        rig.vm.requestDelete()
        rig.vm.confirmDelete()
        advanceUntilIdle()

        assertTrue(rig.movements.deleted.isEmpty())
    }
}
