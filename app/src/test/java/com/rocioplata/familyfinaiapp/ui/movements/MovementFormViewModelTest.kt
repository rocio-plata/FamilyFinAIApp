package com.rocioplata.familyfinaiapp.ui.movements

import com.rocioplata.familyfinaiapp.domain.categories.CategoriesData
import com.rocioplata.familyfinaiapp.domain.categories.Category
import com.rocioplata.familyfinaiapp.domain.categories.CategoryStatus
import com.rocioplata.familyfinaiapp.domain.categories.CategoryType
import com.rocioplata.familyfinaiapp.domain.categories.Tag
import com.rocioplata.familyfinaiapp.domain.categories.TagStatus
import com.rocioplata.familyfinaiapp.domain.money.Money
import com.rocioplata.familyfinaiapp.domain.movements.AmountError
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

        rig.vm.onPaymentMethodSelect("m-old")
        advanceUntilIdle()
        assertEquals("m-cash", rig.state.paymentMethodId)
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
}
