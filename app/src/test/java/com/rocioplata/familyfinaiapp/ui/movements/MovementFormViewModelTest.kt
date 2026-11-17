package com.rocioplata.familyfinaiapp.ui.movements

import com.rocioplata.familyfinaiapp.domain.categories.CategoriesData
import com.rocioplata.familyfinaiapp.domain.categories.Category
import com.rocioplata.familyfinaiapp.domain.categories.CategoryStatus
import com.rocioplata.familyfinaiapp.domain.categories.CategoryType
import com.rocioplata.familyfinaiapp.domain.categories.Tag
import com.rocioplata.familyfinaiapp.domain.categories.TagStatus
import com.rocioplata.familyfinaiapp.domain.money.Money
import com.rocioplata.familyfinaiapp.domain.movements.AmountError
import com.rocioplata.familyfinaiapp.domain.movements.CategoryChange
import com.rocioplata.familyfinaiapp.domain.movements.Movement
import com.rocioplata.familyfinaiapp.domain.movements.MovementChanges
import com.rocioplata.familyfinaiapp.domain.movements.NoteUpdate
import com.rocioplata.familyfinaiapp.domain.movements.DateError
import com.rocioplata.familyfinaiapp.domain.movements.MovementDates
import com.rocioplata.familyfinaiapp.domain.movements.MovementFailure
import com.rocioplata.familyfinaiapp.domain.movements.MovementType
import com.rocioplata.familyfinaiapp.domain.movements.NewMovement
import com.rocioplata.familyfinaiapp.domain.movements.TitleError
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
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class MovementFormViewModelTest {
    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val dates = MovementDates(Clock.fixed(Instant.parse("2026-11-16T15:00:00Z"), ZoneId.of("UTC")))
    private val today = LocalDate.of(2026, 11, 16)

    private fun tag(id: String, order: Int, status: TagStatus = TagStatus.ACTIVE) = Tag(id, "Tag $id", status, order)
    private fun category(
        id: String,
        type: CategoryType = CategoryType.EXPENSE,
        status: CategoryStatus = CategoryStatus.ACTIVE,
        tags: List<Tag> = emptyList(),
    ) = Category(id, type, "Cat $id", "restaurant", status, tags)

    private val food = category("c-food", tags = listOf(tag("t-2", 1), tag("t-1", 0), tag("t-old", 2, TagStatus.DEPRECATED)))
    private val transport = category("c-transport", tags = listOf(tag("t-bus", 0)))
    private val oldCategory = category("c-old", status = CategoryStatus.DEPRECATED)
    private val salary = category("c-salary", CategoryType.INCOME)
    private val card = PaymentMethod("m-card", "Tarjeta", PaymentMethodStatus.ACTIVE)
    private val cash = PaymentMethod("m-cash", "Efectivo", PaymentMethodStatus.ACTIVE)
    private val oldMethod = PaymentMethod("m-old", "Antigua", PaymentMethodStatus.DEPRECATED)

    private class Rig(
        val movements: FakeMovements,
        val categories: FakeCategories,
        val paymentMethods: FakePaymentMethods,
        val vm: MovementFormViewModel,
        val closed: () -> Int,
        val insufficientRole: () -> Int,
        val currencyMismatch: () -> Int,
    ) {
        val state get() = vm.uiState.value
    }

    private fun TestScope.create(
        currency: String? = "CLP",
        loadOptions: Boolean = true,
        configure: (FakeMovements, FakeCategories, FakePaymentMethods) -> Unit = { _, _, _ -> },
    ): Rig {
        val movements = FakeMovements()
        val categories = FakeCategories()
        val paymentMethods = FakePaymentMethods()
        if (loadOptions) {
            categories.data.value = CategoriesData(listOf(food, transport, oldCategory, salary))
            paymentMethods.data.value = PaymentMethodsData(listOf(card, cash, oldMethod))
        }
        configure(movements, categories, paymentMethods)
        var closed = 0
        var insufficient = 0
        var mismatch = 0
        val vm = MovementFormViewModel(
            movements, categories, paymentMethods, "f-1", currency, dates,
            onClose = { closed++ },
            onInsufficientRole = { insufficient++ },
            onCurrencyMismatch = { mismatch++ },
        )
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        return Rig(movements, categories, paymentMethods, vm, { closed }, { insufficient }, { mismatch })
    }

    /** Un formulario completo y válido de un gasto en CLP. */
    private fun TestScope.createFilled(
        currency: String? = "CLP",
        configure: (FakeMovements, FakeCategories, FakePaymentMethods) -> Unit = { _, _, _ -> },
    ): Rig = create(currency, configure = configure).also {
        it.vm.onAmountChange("45900")
        it.vm.onTitleChange("  Supermercado  ")
        it.vm.onCategorySelect("c-food")
        it.vm.onTagSelect("t-1")
        it.vm.onPaymentMethodSelect("m-card")
        it.vm.onNoteChange("   ")
        advanceUntilIdle()
    }

    // --- Valores iniciales y opciones (CA-07, CA-09) ---

    @Test
    fun valoresIniciales() = runTest {
        val rig = create()
        val s = rig.state

        assertEquals(MovementType.EXPENSE, s.type)
        assertEquals("", s.amountText)
        assertEquals("CLP", s.currency)
        assertEquals("", s.title)
        assertNull(s.categoryId)
        assertNull(s.tagId)
        assertNull(s.paymentMethodId)
        assertEquals(today, s.date)
        assertEquals("", s.note)
        assertTrue(s.errors.isEmpty)
        assertEquals(FormOptionsState.Ready, s.options)
        assertFalse(s.isSaving)
        assertFalse(s.confirmingDiscard)
        assertNull(s.notice)
    }

    @Test
    fun alAbrir_refrescaCategoriasYMediosDePago() = runTest {
        val rig = create()

        assertEquals(1, rig.categories.refreshes)
        assertEquals(1, rig.paymentMethods.refreshes)
    }

    @Test
    fun laCuadriculaSoloOfreceCategoriasActivasDelTipo() = runTest {
        val rig = create()
        assertEquals(listOf("c-food", "c-transport"), rig.state.categories.map { it.id })

        rig.vm.onTypeChange(MovementType.INCOME)
        advanceUntilIdle()
        assertEquals(listOf("c-salary"), rig.state.categories.map { it.id })
    }

    @Test
    fun cambiarElTipo_quitaCategoriaYEtiqueta_peroNoLoDemas() = runTest {
        val rig = createFilled()

        rig.vm.onTypeChange(MovementType.INCOME)
        advanceUntilIdle()

        assertNull(rig.state.categoryId)
        assertNull(rig.state.tagId)
        assertEquals("45900", rig.state.amountText)
        assertEquals("  Supermercado  ".take(100), rig.state.title)
        assertEquals("m-card", rig.state.paymentMethodId)
    }

    @Test
    fun elMismoTipo_noQuitaNada() = runTest {
        val rig = createFilled()

        rig.vm.onTypeChange(MovementType.EXPENSE)
        advanceUntilIdle()

        assertEquals("c-food", rig.state.categoryId)
        assertEquals("t-1", rig.state.tagId)
    }

    @Test
    fun alElegirCategoria_aparecenSusEtiquetasActivasEnOrden() = runTest {
        val rig = create()

        rig.vm.onCategorySelect("c-food")
        advanceUntilIdle()

        assertEquals(listOf("t-1", "t-2"), rig.state.tags.map { it.id })
    }

    @Test
    fun unaEtiquetaComoMaximo_yTocarLaElegidaLaQuita() = runTest {
        val rig = create()
        rig.vm.onCategorySelect("c-food")

        rig.vm.onTagSelect("t-1")
        rig.vm.onTagSelect("t-2")
        advanceUntilIdle()
        assertEquals("t-2", rig.state.tagId)

        rig.vm.onTagSelect("t-2")
        advanceUntilIdle()
        assertNull(rig.state.tagId)

        rig.vm.onTagSelect("t-1")
        rig.vm.onTagSelect(null)
        advanceUntilIdle()
        assertNull(rig.state.tagId)
    }

    @Test
    fun cambiarDeCategoria_quitaLaEtiqueta_peroElegirLaMismaNo() = runTest {
        val rig = create()
        rig.vm.onCategorySelect("c-food")
        rig.vm.onTagSelect("t-1")

        rig.vm.onCategorySelect("c-food")
        advanceUntilIdle()
        assertEquals("t-1", rig.state.tagId)

        rig.vm.onCategorySelect("c-transport")
        advanceUntilIdle()
        assertNull(rig.state.tagId)
        assertEquals(listOf("t-bus"), rig.state.tags.map { it.id })
    }

    @Test
    fun unaEtiquetaQueNoEsDeLaCategoria_noSeAcepta() = runTest {
        val rig = create()
        rig.vm.onCategorySelect("c-transport")

        rig.vm.onTagSelect("t-1")
        advanceUntilIdle()

        assertNull(rig.state.tagId)
    }

    @Test
    fun unaCategoriaArchivadaMientrasSeEscribe_seQuitaDeLaSeleccion() = runTest {
        val rig = createFilled()

        rig.categories.data.value = CategoriesData(listOf(food.copy(status = CategoryStatus.DEPRECATED), transport, salary))
        advanceUntilIdle()

        assertNull(rig.state.categoryId)
        assertNull(rig.state.tagId)
        assertEquals(listOf("c-transport"), rig.state.categories.map { it.id })
    }

    // --- Monto, concepto y observaciones (CA-06) ---

    @Test
    fun elMontoSoloAdmiteLoQueLaMonedaPermite() = runTest {
        val clp = create(currency = "CLP")
        clp.vm.onAmountChange("45.900,5abc")
        advanceUntilIdle()
        assertEquals("459005", clp.state.amountText)

        val usd = create(currency = "USD")
        usd.vm.onAmountChange("12,345")
        advanceUntilIdle()
        assertEquals("12,34", usd.state.amountText)
    }

    @Test
    fun laParteEnteraTieneComoMaximo12Digitos() = runTest {
        val rig = create()

        rig.vm.onAmountChange("1".repeat(20))
        advanceUntilIdle()

        assertEquals(12, rig.state.amountText.length)
    }

    @Test
    fun elConceptoTieneComoMaximo100Caracteres_yLasObservaciones500() = runTest {
        val rig = create()

        rig.vm.onTitleChange("a".repeat(150))
        rig.vm.onNoteChange("b".repeat(700))
        advanceUntilIdle()

        assertEquals(100, rig.state.title.length)
        assertEquals(500, rig.state.note.length)
    }

    @Test
    fun guardarConTodoVacio_muestraUnAvisoPorCampo_ySinLlamarAlBackend() = runTest {
        val rig = create()

        rig.vm.save()
        advanceUntilIdle()

        val errors = rig.state.errors
        assertEquals(AmountError.REQUIRED, errors.amount)
        assertEquals(TitleError.REQUIRED, errors.title)
        assertTrue(errors.category)
        assertTrue(errors.paymentMethod)
        assertTrue(rig.movements.created.isEmpty())
        assertFalse(rig.state.isSaving)
    }

    @Test
    fun montoEnCero_yConceptoSoloEspacios_sonInvalidos() = runTest {
        val rig = createFilled()
        rig.vm.onAmountChange("0")
        rig.vm.onTitleChange("    ")

        rig.vm.save()
        advanceUntilIdle()

        assertEquals(AmountError.MUST_BE_POSITIVE, rig.state.errors.amount)
        assertEquals(TitleError.REQUIRED, rig.state.errors.title)
        assertFalse(rig.state.errors.category)
        assertFalse(rig.state.errors.paymentMethod)
        assertTrue(rig.movements.created.isEmpty())
    }

    @Test
    fun sinCategoria_oSinMedioDePago_cadaUnoTieneSuAviso() = runTest {
        val noCategory = createFilled()
        noCategory.vm.onTypeChange(MovementType.INCOME)
        noCategory.vm.save()
        advanceUntilIdle()
        assertTrue(noCategory.state.errors.category)
        assertFalse(noCategory.state.errors.paymentMethod)

        val noMethod = create()
        noMethod.vm.onAmountChange("100")
        noMethod.vm.onTitleChange("x")
        noMethod.vm.onCategorySelect("c-food")
        noMethod.vm.save()
        advanceUntilIdle()
        assertTrue(noMethod.state.errors.paymentMethod)
        assertFalse(noMethod.state.errors.category)
    }

    @Test
    fun guardarEnseguidaDeCambiarElTipo_validaConElEstadoActual() = runTest {
        val rig = createFilled()

        // Sin dejar pasar el tiempo entre el cambio y el guardado: no debe usar la categoría anterior.
        rig.vm.onTypeChange(MovementType.INCOME)
        rig.vm.save()
        advanceUntilIdle()

        assertTrue(rig.movements.created.isEmpty())
        assertTrue(rig.state.errors.category)
    }

    @Test
    fun editarUnCampo_quitaSuAviso() = runTest {
        val rig = create()
        rig.vm.save()
        advanceUntilIdle()

        rig.vm.onAmountChange("5")
        rig.vm.onTitleChange("a")
        rig.vm.onCategorySelect("c-food")
        rig.vm.onPaymentMethodSelect("m-card")
        advanceUntilIdle()

        assertTrue(rig.state.errors.isEmpty)
    }

    // --- Medio de pago (CA-08) ---

    @Test
    fun elUltimoMedioUsado_seProponeSiSigueActivo() = runTest {
        val rig = create { movements, _, _ -> movements.lastUsedPaymentMethodId = "m-cash" }

        assertEquals("m-cash", rig.state.paymentMethodId)
    }

    @Test
    fun sinUltimoMedio_oArchivado_noSePropone() = runTest {
        assertNull(create().state.paymentMethodId)
        assertNull(create { movements, _, _ -> movements.lastUsedPaymentMethodId = "m-old" }.state.paymentMethodId)
        assertNull(create { movements, _, _ -> movements.lastUsedPaymentMethodId = "m-gone" }.state.paymentMethodId)
    }

    @Test
    fun laEleccionDelUsuarioPrevaleceSobreLaPropuesta_yUnMedioArchivadoNoSePuedeElegir() = runTest {
        val rig = create { movements, _, _ -> movements.lastUsedPaymentMethodId = "m-cash" }

        rig.vm.onPaymentMethodSelect("m-card")
        advanceUntilIdle()
        assertEquals("m-card", rig.state.paymentMethodId)

        // Un medio archivado se ignora: se conserva la elección anterior.
        rig.vm.onPaymentMethodSelect("m-old")
        advanceUntilIdle()
        assertEquals("m-card", rig.state.paymentMethodId)
        assertEquals(listOf("m-card", "m-cash"), rig.state.paymentMethods.map { it.id })
    }

    // --- Fecha (CA-09) ---

    @Test
    fun laFechaViene_conHoy_yLosAccesosRapidosLaCambian() = runTest {
        val rig = create()
        assertEquals(today, rig.state.date)

        rig.vm.onYesterday()
        advanceUntilIdle()
        assertEquals(today.minusDays(1), rig.state.date)

        rig.vm.onToday()
        advanceUntilIdle()
        assertEquals(today, rig.state.date)
    }

    @Test
    fun otraFecha_admiteCualquierDiaPasado() = runTest {
        val rig = create()

        rig.vm.onDateSelect(LocalDate.of(2026, 1, 3))
        advanceUntilIdle()

        assertEquals(LocalDate.of(2026, 1, 3), rig.state.date)
    }

    @Test
    fun unDiaFuturo_seRechazaConAvisoYSeConservaLaFechaAnterior() = runTest {
        val rig = create()
        rig.vm.onYesterday()

        rig.vm.onDateSelect(today.plusDays(1))
        advanceUntilIdle()

        assertEquals(today.minusDays(1), rig.state.date)
        assertEquals(DateError.FUTURE, rig.state.errors.date)

        rig.vm.onToday()
        advanceUntilIdle()
        assertNull(rig.state.errors.date)
    }

    // --- Guardar ---

    @Test
    fun altaCorrecta_enviaLosDatosNormalizados_yCierra() = runTest {
        val rig = createFilled()
        rig.vm.onYesterday()

        rig.vm.save()
        advanceUntilIdle()

        assertEquals(
            listOf(
                NewMovement(
                    amount = Money(45_900, "CLP"), categoryId = "c-food", tagId = "t-1", paymentMethodId = "m-card",
                    title = "Supermercado", note = null, date = today.minusDays(1),
                ),
            ),
            rig.movements.created,
        )
        assertEquals(1, rig.closed())
        assertFalse(rig.state.isSaving)
    }

    @Test
    fun altaConDecimales_conviertePorLaMoneda() = runTest {
        val rig = createFilled(currency = "USD")
        rig.vm.onAmountChange("12,5")
        rig.vm.onNoteChange("  con nota ")

        rig.vm.save()
        advanceUntilIdle()

        assertEquals(Money(1_250, "USD"), rig.movements.created.single().amount)
        assertEquals("con nota", rig.movements.created.single().note)
    }

    @Test
    fun altaSinEtiqueta_enviaNulo() = runTest {
        val rig = createFilled()
        rig.vm.onTagSelect(null)
        rig.vm.onTagSelect("t-1")
        rig.vm.onTagSelect("t-1")
        advanceUntilIdle()

        rig.vm.save()
        advanceUntilIdle()

        assertNull(rig.movements.created.single().tagId)
    }

    @Test
    fun dosPulsacionesDeGuardar_haceUnaSolaLlamada_yElFormularioNoCambiaMientras() = runTest {
        val gate = CompletableDeferred<Unit>()
        val rig = createFilled { movements, _, _ -> movements.createGate = gate }

        rig.vm.save()
        rig.vm.save()
        advanceUntilIdle()
        assertTrue(rig.state.isSaving)
        rig.vm.onTitleChange("otro")
        rig.vm.onTypeChange(MovementType.INCOME)
        rig.vm.requestClose()
        advanceUntilIdle()
        assertEquals("  Supermercado  ", rig.state.title)
        assertEquals(MovementType.EXPENSE, rig.state.type)
        assertFalse(rig.state.confirmingDiscard)

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, rig.movements.created.size)
        assertEquals(1, rig.closed())
    }

    // --- Fallos (RF-14, CA-17, CA-22): lo escrito se conserva ---

    private fun TestScope.failed(failure: MovementFailure): Rig =
        createFilled { movements, _, _ -> movements.createFailure = failure }.also {
            it.vm.save()
            advanceUntilIdle()
        }

    private fun assertWrittenKept(rig: Rig) {
        assertEquals("45900", rig.state.amountText)
        assertEquals("  Supermercado  ", rig.state.title)
        assertEquals(today, rig.state.date)
    }

    @Test
    fun sinConexion_avisaYConservaTodo() = runTest {
        val rig = failed(MovementFailure.Offline)

        assertEquals(MovementFailure.Offline, rig.state.notice)
        assertFalse(rig.state.isSaving)
        assertEquals(0, rig.closed())
        assertWrittenKept(rig)
        assertEquals("c-food", rig.state.categoryId)
        assertEquals("t-1", rig.state.tagId)
        assertEquals("m-card", rig.state.paymentMethodId)
    }

    @Test
    fun errorDeRedYOtrosFallos_avisanYConservanTodo() = runTest {
        listOf(
            MovementFailure.Network, MovementFailure.InvalidData, MovementFailure.Unknown,
            MovementFailure.Unauthorized, MovementFailure.NotFound,
        ).forEach { failure ->
            val rig = failed(failure)
            assertEquals(failure.toString(), failure, rig.state.notice)
            assertFalse(failure.toString(), rig.state.isSaving)
            assertEquals(failure.toString(), 0, rig.closed())
            assertWrittenKept(rig)
        }
    }

    @Test
    fun categoriaNoDisponible_laQuita_conservaElRestoYRefrescaLasCategorias() = runTest {
        val rig = failed(MovementFailure.CategoryUnavailable)

        assertEquals(MovementFailure.CategoryUnavailable, rig.state.notice)
        assertNull(rig.state.categoryId)
        assertNull(rig.state.tagId)
        assertWrittenKept(rig)
        assertEquals("m-card", rig.state.paymentMethodId)
        assertEquals(2, rig.categories.refreshes)
        assertEquals(1, rig.paymentMethods.refreshes)
    }

    @Test
    fun etiquetaNoDisponible_quitaSoloLaEtiquetaYRefrescaLasCategorias() = runTest {
        val rig = failed(MovementFailure.TagUnavailable)

        assertNull(rig.state.tagId)
        assertEquals("c-food", rig.state.categoryId)
        assertEquals(2, rig.categories.refreshes)
        assertWrittenKept(rig)
    }

    @Test
    fun medioDePagoNoDisponible_loQuitaYRefrescaLosMedios_sinProponerElUltimoUsado() = runTest {
        val rig = createFilled { movements, _, _ ->
            movements.createFailure = MovementFailure.PaymentMethodUnavailable
            movements.lastUsedPaymentMethodId = "m-card"
        }
        rig.vm.save()
        advanceUntilIdle()

        assertEquals(MovementFailure.PaymentMethodUnavailable, rig.state.notice)
        assertNull(rig.state.paymentMethodId)
        assertEquals(2, rig.paymentMethods.refreshes)
        assertWrittenKept(rig)
        assertEquals("c-food", rig.state.categoryId)
    }

    @Test
    fun alRefrescarseLasOpciones_elFormularioSeActualiza() = runTest {
        val rig = failed(MovementFailure.CategoryUnavailable)

        rig.categories.data.value = CategoriesData(listOf(transport, salary))
        advanceUntilIdle()

        assertEquals(listOf("c-transport"), rig.state.categories.map { it.id })
    }

    @Test
    fun tipoIncompatible_avisaYRefrescaLasCategorias() = runTest {
        val rig = failed(MovementFailure.TypeMismatch)

        assertEquals(MovementFailure.TypeMismatch, rig.state.notice)
        assertEquals(2, rig.categories.refreshes)
        assertWrittenKept(rig)
    }

    @Test
    fun monedaCambiada_avisaYPideConsultarLaCabecera() = runTest {
        val rig = failed(MovementFailure.CurrencyMismatch)

        assertEquals(MovementFailure.CurrencyMismatch, rig.state.notice)
        assertEquals(1, rig.currencyMismatch())
        assertWrittenKept(rig)
    }

    @Test
    fun sinPermiso_avisaYPideReevaluarElRol() = runTest {
        val rig = failed(MovementFailure.NotAllowed)

        assertEquals(MovementFailure.NotAllowed, rig.state.notice)
        assertEquals(1, rig.insufficientRole())
        assertWrittenKept(rig)
    }

    @Test
    fun fechaFuturaDelBackend_muestraElAvisoJuntoALaFecha() = runTest {
        val rig = failed(MovementFailure.FutureDate)

        assertEquals(DateError.FUTURE, rig.state.errors.date)
        assertEquals(MovementFailure.FutureDate, rig.state.notice)
    }

    @Test
    fun consumirElAviso_loQuita_yDespuesDeUnFalloSePuedeReintentar() = runTest {
        val rig = failed(MovementFailure.Network)

        rig.vm.consumeNotice()
        advanceUntilIdle()
        assertNull(rig.state.notice)

        rig.movements.createFailure = null
        rig.vm.save()
        advanceUntilIdle()

        assertEquals(2, rig.movements.created.size)
        assertEquals(1, rig.closed())
    }

    // --- Descarte (CA-19) ---

    @Test
    fun sinCambios_cierraDirectamente() = runTest {
        val rig = create()

        rig.vm.requestClose()
        advanceUntilIdle()

        assertEquals(1, rig.closed())
        assertFalse(rig.state.confirmingDiscard)
    }

    @Test
    fun elMedioPropuesto_noCuentaComoCambio() = runTest {
        val rig = create { movements, _, _ -> movements.lastUsedPaymentMethodId = "m-cash" }

        rig.vm.requestClose()
        advanceUntilIdle()

        assertEquals(1, rig.closed())
    }

    @Test
    fun cualquierCambio_pideConfirmacion_yConfirmarCierra() = runTest {
        val changes: List<(MovementFormViewModel) -> Unit> = listOf(
            { it.onAmountChange("5") },
            { it.onTitleChange("x") },
            { it.onNoteChange("x") },
            { it.onTypeChange(MovementType.INCOME) },
            { it.onCategorySelect("c-food") },
            { it.onYesterday() },
            { it.onPaymentMethodSelect("m-card") },
        )
        changes.forEachIndexed { index, change ->
            val rig = create()
            change(rig.vm)
            rig.vm.requestClose()
            advanceUntilIdle()
            assertTrue("cambio $index", rig.state.confirmingDiscard)
            assertEquals("cambio $index", 0, rig.closed())

            rig.vm.confirmDiscard()
            advanceUntilIdle()
            assertEquals("cambio $index", 1, rig.closed())
        }
    }

    @Test
    fun cancelarElDescarte_conservaLoEscrito() = runTest {
        val rig = createFilled()
        rig.vm.requestClose()
        advanceUntilIdle()

        rig.vm.dismissDiscard()
        advanceUntilIdle()

        assertFalse(rig.state.confirmingDiscard)
        assertEquals(0, rig.closed())
        assertEquals("45900", rig.state.amountText)
    }

    @Test
    fun conLaConfirmacionAbierta_noSePuedeGuardar() = runTest {
        val rig = createFilled()
        rig.vm.requestClose()
        advanceUntilIdle()

        rig.vm.save()
        advanceUntilIdle()

        assertTrue(rig.movements.created.isEmpty())
    }

    @Test
    fun confirmarSinHaberPedidoCerrar_noHaceNada() = runTest {
        val rig = createFilled()

        rig.vm.confirmDiscard()
        advanceUntilIdle()

        assertEquals(0, rig.closed())
    }

    // --- Sin categorías, sin medios, sin moneda y carga de opciones (CA-23, CA-25) ---

    @Test
    fun familiaSinCategoriasDelTipo_loIndica() = runTest {
        val rig = create { _, categories, _ -> categories.data.value = CategoriesData(listOf(food, oldCategory)) }
        assertFalse(rig.state.noCategories)

        rig.vm.onTypeChange(MovementType.INCOME)
        advanceUntilIdle()

        assertTrue(rig.state.noCategories)
        assertTrue(rig.state.categories.isEmpty())
    }

    @Test
    fun familiaSinMediosActivos_loIndica() = runTest {
        val rig = create { _, _, methods -> methods.data.value = PaymentMethodsData(listOf(oldMethod)) }

        assertTrue(rig.state.noPaymentMethods)
        assertTrue(rig.state.paymentMethods.isEmpty())
    }

    @Test
    fun sinMoneda_noSePuedeGuardar() = runTest {
        listOf(null, "XXXX").forEach { currency ->
            val rig = createFilled(currency = currency)

            assertTrue(currency.toString(), rig.state.currencyMissing)
            rig.vm.save()
            advanceUntilIdle()

            assertTrue(currency.toString(), rig.movements.created.isEmpty())
            assertFalse(currency.toString(), rig.state.isSaving)
        }
    }

    @Test
    fun mientrasLlegan_lasOpciones_estanCargando_yNoSeDiceQueFaltan() = runTest {
        val rig = create(loadOptions = false) { _, c, m ->
            c.data.value = CategoriesData()
            m.data.value = PaymentMethodsData()
        }

        assertEquals(FormOptionsState.Loading, rig.state.options)
        assertFalse(rig.state.noCategories)
        assertFalse(rig.state.noPaymentMethods)
    }

    @Test
    fun sinOpcionesYConFalloDeCarga_esErrorYReintentarLasPideDeNuevo() = runTest {
        val rig = create(loadOptions = false) { _, c, m ->
            c.refreshFails = true
            m.refreshFails = true
        }
        assertEquals(FormOptionsState.Error, rig.state.options)

        rig.categories.refreshFails = false
        rig.paymentMethods.refreshFails = false
        rig.vm.retryOptions()
        rig.categories.data.value = CategoriesData(listOf(food))
        rig.paymentMethods.data.value = PaymentMethodsData(listOf(card))
        advanceUntilIdle()

        assertEquals(FormOptionsState.Ready, rig.state.options)
        assertEquals(2, rig.categories.refreshes)
        assertEquals(2, rig.paymentMethods.refreshes)
    }

    @Test
    fun conCopiaDeLasOpciones_unFalloDelRefrescoNoLasOculta() = runTest {
        val rig = create { _, c, m ->
            c.refreshFails = true
            m.refreshFails = true
        }

        assertEquals(FormOptionsState.Ready, rig.state.options)
    }

    // --- Edición (T-20: CA-11, CA-12, CA-13, CA-19, CA-22) ---

    private val original = Movement(
        id = "m-1", type = MovementType.EXPENSE, amount = Money(45_900, "CLP"), categoryId = "c-food",
        tagId = "t-1", paymentMethodId = "m-card", title = "Supermercado", note = "Compra semanal",
        date = LocalDate.of(2026, 11, 12), recordedBy = "u-1", createdAt = Instant.EPOCH,
    )

    private fun TestScope.createEdit(
        movement: Movement? = original,
        emit: Boolean = true,
        currency: String? = "CLP",
        onMoved: (() -> Unit)? = null,
        configure: (FakeMovements, FakeCategories, FakePaymentMethods) -> Unit = { _, _, _ -> },
    ): Rig {
        val movements = FakeMovements()
        val categories = FakeCategories().apply { data.value = CategoriesData(listOf(food, transport, oldCategory, salary)) }
        val paymentMethods = FakePaymentMethods().apply { data.value = PaymentMethodsData(listOf(card, cash, oldMethod)) }
        configure(movements, categories, paymentMethods)
        if (emit) movements.detail.tryEmit(movement)
        var closed = 0
        var insufficient = 0
        var mismatch = 0
        val vm = MovementFormViewModel(
            movements, categories, paymentMethods, "f-1", currency, dates,
            onClose = { closed++ }, onInsufficientRole = { insufficient++ }, onCurrencyMismatch = { mismatch++ },
            movementId = "m-1",
            onMovedToAnotherMonth = onMoved ?: { closed++; Unit },
        )
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        return Rig(movements, categories, paymentMethods, vm, { closed }, { insufficient }, { mismatch })
    }

    @Test
    fun edicion_cargaLosDatosDelMovimiento() = runTest {
        val rig = createEdit()
        val s = rig.state

        assertTrue(s.isEditing)
        assertFalse(s.isLoadingMovement)
        assertEquals(MovementType.EXPENSE, s.type)
        assertEquals("45900", s.amountText)
        assertEquals("Supermercado", s.title)
        assertEquals("c-food", s.categoryId)
        assertEquals("t-1", s.tagId)
        assertEquals("m-card", s.paymentMethodId)
        assertEquals(LocalDate.of(2026, 11, 12), s.date)
        assertEquals("Compra semanal", s.note)
        assertEquals(FormOptionsState.Ready, s.options)
        assertFalse(s.savedPartially)
    }

    @Test
    fun edicion_conMontoConDecimales_loMuestraEnSuFormatoEditable() = runTest {
        val rig = createEdit(original.copy(amount = Money(1_250, "USD")), currency = "USD")

        assertEquals("12,50", rig.state.amountText)
    }

    @Test
    fun edicion_sinObservaciones_dejaElCampoVacio() = runTest {
        assertEquals("", createEdit(original.copy(note = null)).state.note)
    }

    @Test
    fun edicion_mientrasSeLeeElMovimiento_estaCargando_yNoSePuedeEditar() = runTest {
        val rig = createEdit(emit = false)

        assertTrue(rig.state.isLoadingMovement)
        assertEquals(FormOptionsState.Loading, rig.state.options)
        rig.vm.onTitleChange("x")
        rig.vm.save()
        advanceUntilIdle()
        assertEquals("", rig.state.title)
        assertTrue(rig.movements.edited.isEmpty())

        rig.movements.detail.tryEmit(original)
        advanceUntilIdle()
        assertFalse(rig.state.isLoadingMovement)
        assertEquals("Supermercado", rig.state.title)
    }

    @Test
    fun edicion_siElMovimientoNoEstaEnMemoria_cierra() = runTest {
        val rig = createEdit(movement = null)

        assertEquals(1, rig.closed())
    }

    @Test
    fun edicion_noRefrescaElUltimoMedioNiLoPropone() = runTest {
        val rig = createEdit(original.copy(paymentMethodId = null)) { movements, _, _ ->
            movements.lastUsedPaymentMethodId = "m-cash"
        }

        assertNull(rig.state.paymentMethodId)
    }

    @Test
    fun edicion_elTipoNoSePuedeCambiar() = runTest {
        val rig = createEdit()

        rig.vm.onTypeChange(MovementType.INCOME)
        advanceUntilIdle()

        assertEquals(MovementType.EXPENSE, rig.state.type)
        assertEquals("c-food", rig.state.categoryId)
    }

    @Test
    fun edicion_unIngresoSoloOfreceCategoriasDeIngreso() = runTest {
        val income = original.copy(type = MovementType.INCOME, categoryId = "c-salary", tagId = null)
        val rig = createEdit(income)

        assertEquals(listOf("c-salary"), rig.state.categories.map { it.id })
    }

    @Test
    fun edicion_sinCambios_cierraSinLlamar() = runTest {
        val rig = createEdit()

        rig.vm.save()
        advanceUntilIdle()

        assertTrue(rig.movements.edited.isEmpty())
        assertEquals(1, rig.closed())
    }

    @Test
    fun edicion_cadaCampoPorSeparado_enviaSoloEseCampo() = runTest {
        data class Case(val name: String, val change: (MovementFormViewModel) -> Unit, val expected: MovementChanges)
        val cases = listOf(
            Case("monto", { it.onAmountChange("50000") }, MovementChanges(amount = Money(50_000, "CLP"))),
            Case("concepto", { it.onTitleChange("  Super Jumbo ") }, MovementChanges(title = "Super Jumbo")),
            Case("observaciones", { it.onNoteChange("Otra nota") }, MovementChanges(note = NoteUpdate.Set("Otra nota"))),
            Case("fecha", { it.onYesterday() }, MovementChanges(date = today.minusDays(1))),
            Case("medio", { it.onPaymentMethodSelect("m-cash") }, MovementChanges(paymentMethodId = "m-cash")),
            Case("etiqueta", { it.onTagSelect("t-2") }, MovementChanges(category = CategoryChange("c-food", "t-2"))),
            Case("categoria", { it.onCategorySelect("c-transport") }, MovementChanges(category = CategoryChange("c-transport", null))),
        )
        cases.forEach { case ->
            val rig = createEdit()
            case.change(rig.vm)
            rig.vm.save()
            advanceUntilIdle()

            assertEquals(case.name, listOf(case.expected), rig.movements.edited.map { it.changes })
            assertEquals(case.name, 1, rig.closed())
        }
    }

    @Test
    fun edicion_borrarLasObservaciones_envianRemove() = runTest {
        val rig = createEdit()

        rig.vm.onNoteChange("   ")
        rig.vm.save()
        advanceUntilIdle()

        assertEquals(NoteUpdate.Remove, rig.movements.edited.single().changes.note)
    }

    @Test
    fun edicion_laFechaSoloSeEnviaSiCambioElDia() = runTest {
        val rig = createEdit()

        rig.vm.onYesterday()
        rig.vm.onDateSelect(original.date)
        rig.vm.onTitleChange("Otro")
        rig.vm.save()
        advanceUntilIdle()

        assertNull(rig.movements.edited.single().changes.date)
        assertEquals("Otro", rig.movements.edited.single().changes.title)
    }

    @Test
    fun edicion_datosYCategoriaAlMismoTiempo_seEnvianJuntos() = runTest {
        val rig = createEdit()

        rig.vm.onTitleChange("Nuevo")
        rig.vm.onCategorySelect("c-transport")
        rig.vm.onTagSelect("t-bus")
        rig.vm.save()
        advanceUntilIdle()

        assertEquals(
            MovementChanges(title = "Nuevo", category = CategoryChange("c-transport", "t-bus")),
            rig.movements.edited.single().changes,
        )
        assertEquals("m-1", rig.movements.edited.single().movementId)
    }

    @Test
    fun edicion_quitarLaEtiqueta_enviaLaMismaCategoriaSinEtiqueta() = runTest {
        val rig = createEdit()

        rig.vm.onTagSelect("t-1")
        rig.vm.save()
        advanceUntilIdle()

        assertEquals(CategoryChange("c-food", null), rig.movements.edited.single().changes.category)
    }

    @Test
    fun edicion_medioArchivado_seConservaYSeGuardaElRestoSinCambiarlo() = runTest {
        val rig = createEdit(original.copy(paymentMethodId = "m-old"))
        assertEquals("m-old", rig.state.paymentMethodId)
        assertEquals(listOf("m-card", "m-cash", "m-old"), rig.state.paymentMethods.map { it.id })

        rig.vm.onTitleChange("Otro")
        rig.vm.save()
        advanceUntilIdle()

        assertNull(rig.movements.edited.single().changes.paymentMethodId)
        assertEquals(1, rig.closed())
    }

    @Test
    fun edicion_medioArchivado_alCambiarloSoloSeOfrecenActivos() = runTest {
        val rig = createEdit(original.copy(paymentMethodId = "m-old"))

        rig.vm.onPaymentMethodSelect("m-cash")
        advanceUntilIdle()
        assertEquals("m-cash", rig.state.paymentMethodId)

        rig.vm.save()
        advanceUntilIdle()
        assertEquals("m-cash", rig.movements.edited.single().changes.paymentMethodId)
    }

    @Test
    fun edicion_otroMedioArchivadoNoSePuedeElegir() = runTest {
        val rig = createEdit()

        rig.vm.onPaymentMethodSelect("m-old")
        advanceUntilIdle()

        assertEquals("m-card", rig.state.paymentMethodId)
    }

    @Test
    fun edicion_movimientoSinMedioDePago_seGuardaSinExigirlo() = runTest {
        val rig = createEdit(original.copy(paymentMethodId = null))

        rig.vm.onTitleChange("Otro")
        rig.vm.save()
        advanceUntilIdle()

        assertFalse(rig.state.errors.paymentMethod)
        assertNull(rig.movements.edited.single().changes.paymentMethodId)
    }

    @Test
    fun edicion_categoriaYEtiquetaArchivadas_seConservanMientrasNoSeCambien() = runTest {
        val archivedTag = Tag("t-gone", "vieja", TagStatus.DEPRECATED, 5)
        val archivedFood = food.copy(status = CategoryStatus.DEPRECATED, tags = food.tags + archivedTag)
        val rig = createEdit(original.copy(tagId = "t-gone")) { _, categories, _ ->
            categories.data.value = CategoriesData(listOf(archivedFood, transport, salary))
        }

        assertEquals("c-food", rig.state.categoryId)
        assertEquals("t-gone", rig.state.tagId)
        assertEquals(listOf("c-food", "c-transport"), rig.state.categories.map { it.id })
        assertEquals(CategoryStatus.DEPRECATED, rig.state.categories.first { it.id == "c-food" }.status)
        assertTrue(rig.state.tags.any { it.id == "t-gone" })

        rig.vm.onTitleChange("Otro")
        rig.vm.save()
        advanceUntilIdle()

        assertNull(rig.movements.edited.single().changes.category)
    }

    @Test
    fun edicion_alCambiarDeCategoria_laEtiquetaArchivadaOriginalYaNoSeOfrece_peroSePuedeVolver() = runTest {
        val archivedTag = Tag("t-gone", "vieja", TagStatus.DEPRECATED, 5)
        val rig = createEdit(original.copy(tagId = "t-gone")) { _, categories, _ ->
            categories.data.value = CategoriesData(listOf(food.copy(tags = food.tags + archivedTag), transport, oldCategory, salary))
        }
        assertTrue(rig.state.tags.any { it.id == "t-gone" })

        rig.vm.onCategorySelect("c-transport")
        advanceUntilIdle()
        assertNull(rig.state.tagId)
        assertFalse(rig.state.tags.any { it.id == "t-gone" })
        rig.vm.onTagSelect("t-gone")
        advanceUntilIdle()
        assertNull(rig.state.tagId)

        // Volver a la categoría original vuelve a ofrecer su etiqueta original, y es lo único archivado que se ofrece.
        rig.vm.onCategorySelect("c-food")
        advanceUntilIdle()
        assertEquals(listOf("t-1", "t-2", "t-gone"), rig.state.tags.map { it.id })
        assertFalse(rig.state.categories.any { it.id == "c-old" })
    }

    @Test
    fun edicion_otraCategoriaArchivadaNoSePuedeElegir() = runTest {
        val rig = createEdit()

        rig.vm.onCategorySelect("c-old")
        advanceUntilIdle()

        assertEquals("c-food", rig.state.categoryId)
    }

    // --- Validación en edición ---

    @Test
    fun edicion_conUnCampoInvalido_noLlamaYMuestraElAviso() = runTest {
        val rig = createEdit()

        rig.vm.onAmountChange("")
        rig.vm.onTitleChange("   ")
        rig.vm.save()
        advanceUntilIdle()

        assertEquals(AmountError.REQUIRED, rig.state.errors.amount)
        assertEquals(TitleError.REQUIRED, rig.state.errors.title)
        assertTrue(rig.movements.edited.isEmpty())
        assertEquals(0, rig.closed())
    }

    @Test
    fun edicion_montoEnCero_esInvalido() = runTest {
        val rig = createEdit()

        rig.vm.onAmountChange("0")
        rig.vm.save()
        advanceUntilIdle()

        assertEquals(AmountError.MUST_BE_POSITIVE, rig.state.errors.amount)
    }

    // --- Resultado parcial (CA-13) ---

    @Test
    fun edicion_resultadoParcial_informa_siguesAbierto_yElReintentoSoloEnviaLoPendiente() = runTest {
        val rig = createEdit { movements, _, _ -> movements.editPartialFailure = MovementFailure.Network }
        rig.vm.onTitleChange("Nuevo")
        rig.vm.onCategorySelect("c-transport")

        rig.vm.save()
        advanceUntilIdle()

        assertTrue(rig.state.savedPartially)
        assertEquals(MovementFailure.Network, rig.state.notice)
        assertFalse(rig.state.isSaving)
        assertEquals(0, rig.closed())
        assertEquals("Nuevo", rig.state.title)
        assertEquals("c-transport", rig.state.categoryId)

        rig.movements.editPartialFailure = null
        rig.vm.save()
        advanceUntilIdle()

        assertEquals(2, rig.movements.edited.size)
        assertEquals(MovementChanges(category = CategoryChange("c-transport", null)), rig.movements.edited.last().changes)
        assertEquals(1, rig.closed())
    }

    @Test
    fun edicion_alReintentarTrasUnParcial_elAvisoDeParcialSeQuita() = runTest {
        val rig = createEdit { movements, _, _ -> movements.editPartialFailure = MovementFailure.Network }
        rig.vm.onTitleChange("Nuevo")
        rig.vm.onCategorySelect("c-transport")
        rig.vm.save()
        advanceUntilIdle()

        val gate = CompletableDeferred<Unit>()
        rig.movements.editGate = gate
        rig.vm.save()
        advanceUntilIdle()

        assertFalse(rig.state.savedPartially)
        assertTrue(rig.state.isSaving)
        gate.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun edicion_parcialConCategoriaNoDisponible_vuelveALaCategoriaOriginal_yConservaLosDatosGuardados() = runTest {
        val rig = createEdit { movements, _, _ -> movements.editPartialFailure = MovementFailure.CategoryUnavailable }
        rig.vm.onTitleChange("Nuevo")
        rig.vm.onCategorySelect("c-transport")

        rig.vm.save()
        advanceUntilIdle()

        assertTrue(rig.state.savedPartially)
        assertEquals("c-food", rig.state.categoryId)
        assertEquals("t-1", rig.state.tagId)
        assertEquals("Nuevo", rig.state.title)
        assertEquals(2, rig.categories.refreshes)
    }

    // --- Fallos (CA-22) ---

    private fun TestScope.editFailed(failure: MovementFailure, change: (MovementFormViewModel) -> Unit): Rig =
        createEdit { movements, _, _ -> movements.editFailure = failure }.also {
            change(it.vm)
            it.vm.save()
            advanceUntilIdle()
        }

    @Test
    fun edicion_sinConexion_avisaYConservaLoEscrito() = runTest {
        val rig = editFailed(MovementFailure.Offline) { it.onTitleChange("Nuevo") }

        assertEquals(MovementFailure.Offline, rig.state.notice)
        assertEquals("Nuevo", rig.state.title)
        assertFalse(rig.state.isSaving)
        assertFalse(rig.state.savedPartially)
        assertEquals(0, rig.closed())
    }

    @Test
    fun edicion_categoriaNoDisponible_vuelveALaOriginalYRefrescaLasCategorias() = runTest {
        val rig = editFailed(MovementFailure.CategoryUnavailable) {
            it.onTitleChange("Nuevo")
            it.onCategorySelect("c-transport")
        }

        assertEquals("c-food", rig.state.categoryId)
        assertEquals("t-1", rig.state.tagId)
        assertEquals("Nuevo", rig.state.title)
        assertEquals(2, rig.categories.refreshes)
    }

    @Test
    fun edicion_etiquetaNoDisponible_vuelveALaOriginalSiEsLaMismaCategoria() = runTest {
        val rig = editFailed(MovementFailure.TagUnavailable) { it.onTagSelect("t-2") }

        assertEquals("t-1", rig.state.tagId)
        assertEquals("c-food", rig.state.categoryId)
        assertEquals(2, rig.categories.refreshes)
    }

    @Test
    fun edicion_etiquetaNoDisponibleEnOtraCategoria_quedaSinEtiqueta() = runTest {
        val rig = editFailed(MovementFailure.TagUnavailable) {
            it.onCategorySelect("c-transport")
            it.onTagSelect("t-bus")
        }

        assertEquals("c-transport", rig.state.categoryId)
        assertNull(rig.state.tagId)
    }

    @Test
    fun edicion_medioNoDisponible_vuelveAlOriginalYRefrescaLosMedios() = runTest {
        val rig = editFailed(MovementFailure.PaymentMethodUnavailable) { it.onPaymentMethodSelect("m-cash") }

        assertEquals("m-card", rig.state.paymentMethodId)
        assertEquals(2, rig.paymentMethods.refreshes)
    }

    @Test
    fun edicion_sinPermiso_avisaYPideReevaluarElRol() = runTest {
        val rig = editFailed(MovementFailure.NotAllowed) { it.onTitleChange("Nuevo") }

        assertEquals(1, rig.insufficientRole())
        assertEquals(MovementFailure.NotAllowed, rig.state.notice)
    }

    @Test
    fun edicion_monedaCambiada_pideConsultarLaCabecera() = runTest {
        val rig = editFailed(MovementFailure.CurrencyMismatch) { it.onTitleChange("Nuevo") }

        assertEquals(1, rig.currencyMismatch())
    }

    @Test
    fun edicion_elMovimientoYaNoExiste_cierraYRefrescaSuMes() = runTest {
        val rig = editFailed(MovementFailure.NotFound) { it.onTitleChange("Nuevo") }

        assertEquals(1, rig.closed())
        assertEquals(YearMonth.of(2026, 11), rig.movements.refreshed.last())
        assertFalse(rig.state.isSaving)
    }

    @Test
    fun edicion_dosPulsacionesDeGuardar_hacenUnaSolaLlamada() = runTest {
        val gate = CompletableDeferred<Unit>()
        val rig = createEdit { movements, _, _ -> movements.editGate = gate }
        rig.vm.onTitleChange("Nuevo")

        rig.vm.save()
        rig.vm.save()
        advanceUntilIdle()
        rig.vm.onTitleChange("Otro")
        advanceUntilIdle()
        assertEquals("Nuevo", rig.state.title)

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, rig.movements.edited.size)
        assertEquals(1, rig.closed())
    }

    // --- Descarte en edición (CA-19) ---

    @Test
    fun edicion_sinCambios_cierraDirectamente() = runTest {
        val rig = createEdit()

        rig.vm.requestClose()
        advanceUntilIdle()

        assertEquals(1, rig.closed())
        assertFalse(rig.state.confirmingDiscard)
    }

    @Test
    fun edicion_cualquierCambio_pideConfirmacion() = runTest {
        val changes: List<(MovementFormViewModel) -> Unit> = listOf(
            { it.onAmountChange("1") },
            { it.onTitleChange("x") },
            { it.onNoteChange("x") },
            { it.onYesterday() },
            { it.onPaymentMethodSelect("m-cash") },
            { it.onCategorySelect("c-transport") },
            { it.onTagSelect("t-2") },
        )
        changes.forEachIndexed { index, change ->
            val rig = createEdit()
            change(rig.vm)
            rig.vm.requestClose()
            advanceUntilIdle()
            assertTrue("cambio $index", rig.state.confirmingDiscard)
            assertEquals("cambio $index", 0, rig.closed())
            rig.vm.confirmDiscard()
            advanceUntilIdle()
            assertEquals("cambio $index", 1, rig.closed())
        }
    }

    @Test
    fun edicion_volverAlValorOriginal_noCuentaComoCambio() = runTest {
        val rig = createEdit()

        rig.vm.onTitleChange("Otro")
        rig.vm.onTitleChange("Supermercado")
        rig.vm.onAmountChange("1")
        rig.vm.onAmountChange("45900")
        rig.vm.requestClose()
        advanceUntilIdle()

        assertEquals(1, rig.closed())
    }

    @Test
    fun edicion_tomandoElOriginalDespuesDeUnParcial_loGuardadoNoCuentaComoCambio() = runTest {
        val rig = createEdit { movements, _, _ -> movements.editPartialFailure = MovementFailure.Network }
        rig.vm.onTitleChange("Nuevo")
        rig.vm.onCategorySelect("c-transport")
        rig.vm.save()
        advanceUntilIdle()
        // Solo queda pendiente la categoría: volver a la original deja el formulario igual a lo guardado.
        rig.vm.onCategorySelect("c-food")
        rig.vm.onTagSelect("t-1")
        rig.vm.requestClose()
        advanceUntilIdle()

        assertEquals(1, rig.closed())
    }

    @Test
    fun edicion_sinMonedaDeLaFamilia_usaLaDelMovimiento() = runTest {
        val rig = createEdit(currency = null)

        assertEquals("CLP", rig.state.currency)
        assertFalse(rig.state.currencyMissing)
        rig.vm.onAmountChange("50000")
        rig.vm.save()
        advanceUntilIdle()
        assertEquals(Money(50_000, "CLP"), rig.movements.edited.single().changes.amount)
    }

    // --- Edición que mueve el movimiento a otro mes (hallazgo de T-23) ---

    @Test
    fun edicion_alMoverLaFechaAOtroMes_vuelveAlListadoYNoAlDetalle() = runTest {
        var moved = 0
        val rig = createEdit(onMoved = { moved++; Unit })

        rig.vm.onDateSelect(LocalDate.of(2026, 10, 15))
        rig.vm.save()
        advanceUntilIdle()

        assertEquals(1, moved)
        assertEquals(0, rig.closed())
        assertEquals(LocalDate.of(2026, 10, 15), rig.movements.edited.single().changes.date)
    }

    @Test
    fun edicion_alCambiarLaFechaDentroDelMismoMes_vuelveAlDetalle() = runTest {
        var moved = 0
        val rig = createEdit(onMoved = { moved++; Unit })

        rig.vm.onDateSelect(LocalDate.of(2026, 11, 3))
        rig.vm.save()
        advanceUntilIdle()

        assertEquals(0, moved)
        assertEquals(1, rig.closed())
    }

    @Test
    fun edicion_sinCambiarLaFecha_vuelveAlDetalle() = runTest {
        var moved = 0
        val rig = createEdit(onMoved = { moved++; Unit })

        rig.vm.onTitleChange("Otro")
        rig.vm.save()
        advanceUntilIdle()

        assertEquals(0, moved)
        assertEquals(1, rig.closed())
    }

    @Test
    fun edicion_parcialConLaFechaEnOtroMes_alTerminarVuelveAlListado() = runTest {
        var moved = 0
        val rig = createEdit(onMoved = { moved++; Unit }) { movements, _, _ -> movements.editPartialFailure = MovementFailure.Network }
        rig.vm.onDateSelect(LocalDate.of(2026, 10, 15))
        rig.vm.onCategorySelect("c-transport")
        rig.vm.save()
        advanceUntilIdle()
        assertEquals(0, moved)

        rig.movements.editPartialFailure = null
        rig.vm.save()
        advanceUntilIdle()

        assertEquals(1, moved)
        assertEquals(0, rig.closed())
    }

    @Test
    fun edicion_cerrarSinGuardar_nuncaVuelveAlListado() = runTest {
        var moved = 0
        val rig = createEdit(onMoved = { moved++; Unit })
        rig.vm.onDateSelect(LocalDate.of(2026, 10, 15))

        rig.vm.requestClose()
        rig.vm.confirmDiscard()
        advanceUntilIdle()

        assertEquals(0, moved)
        assertEquals(1, rig.closed())
    }
}
