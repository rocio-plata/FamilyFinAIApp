package com.rocioplata.familyfinaiapp.ui.paymentmethods

import com.rocioplata.familyfinaiapp.data.network.ConnectivityObserver
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethod
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethodFailure
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethodResult
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethodStatus
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethodsData
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethodsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PaymentMethodsViewModelTest {
    private class FakeRepository : PaymentMethodsRepository {
        val data = MutableStateFlow(PaymentMethodsData())
        val refreshed = mutableListOf<String>()
        val requestedFamilies = mutableListOf<String>()
        var gate: CompletableDeferred<Unit>? = null
        var onRefresh: (FakeRepository) -> PaymentMethodResult<Unit> = { PaymentMethodResult.Success(Unit) }

        override fun paymentMethods(familyId: String): Flow<PaymentMethodsData> {
            requestedFamilies += familyId
            return data
        }

        override suspend fun refresh(familyId: String): PaymentMethodResult<Unit> {
            refreshed += familyId
            gate?.await()
            return onRefresh(this)
        }

        override suspend fun clear() = Unit

        val created = mutableListOf<String>()
        val renamed = mutableListOf<Pair<String, String>>()
        var saveGate: CompletableDeferred<Unit>? = null
        var saveFailure: PaymentMethodFailure? = null

        override suspend fun createPaymentMethod(familyId: String, name: String): PaymentMethodResult<PaymentMethod> {
            created += name
            saveGate?.await()
            return saveFailure?.let { PaymentMethodResult.Failure(it) }
                ?: PaymentMethodResult.Success(PaymentMethod("m-new", name, PaymentMethodStatus.ACTIVE))
        }

        override suspend fun renamePaymentMethod(
            familyId: String,
            paymentMethodId: String,
            name: String,
        ): PaymentMethodResult<Unit> {
            renamed += paymentMethodId to name
            saveGate?.await()
            return saveFailure?.let { PaymentMethodResult.Failure(it) } ?: PaymentMethodResult.Success(Unit)
        }


        val deprecated = mutableListOf<String>()
        val deleted = mutableListOf<String>()
        var actionGate: CompletableDeferred<Unit>? = null

        /** Fallo por id de medio; sin entrada, la operación tiene éxito. */
        val actionFailures = mutableMapOf<String, PaymentMethodFailure>()

        override suspend fun deprecatePaymentMethod(familyId: String, paymentMethodId: String): PaymentMethodResult<Unit> {
            deprecated += paymentMethodId
            return action(paymentMethodId)
        }

        override suspend fun deletePaymentMethod(familyId: String, paymentMethodId: String): PaymentMethodResult<Unit> {
            deleted += paymentMethodId
            return action(paymentMethodId)
        }

        private suspend fun action(id: String): PaymentMethodResult<Unit> {
            actionGate?.await()
            return actionFailures[id]?.let { PaymentMethodResult.Failure(it) } ?: PaymentMethodResult.Success(Unit)
        }
    }

    private class FakeConnectivity : ConnectivityObserver {
        val flow = MutableSharedFlow<Boolean>(replay = 1)
        override val isOnline: Flow<Boolean> = flow
    }

    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun method(id: String, name: String, status: PaymentMethodStatus = PaymentMethodStatus.ACTIVE) =
        PaymentMethod(id, name, status)

    // El orden recibido mezcla activos y archivados a propósito: debe conservarse dentro de cada grupo.
    private val transferencia = method("m-2", "Transferencia")
    private val vieja = method("m-3", "Tarjeta vieja", PaymentMethodStatus.DEPRECATED)
    private val efectivo = method("m-1", "Efectivo")
    private val otraArchivada = method("m-4", "Cheque", PaymentMethodStatus.DEPRECATED)
    private val all = listOf(transferencia, vieja, efectivo, otraArchivada)

    private fun TestScope.create(
        repo: FakeRepository,
        connectivity: FakeConnectivity = FakeConnectivity(),
        familyId: String = "f-1",
        onInsufficientRole: () -> Unit = {},
    ): PaymentMethodsViewModel {
        val vm = PaymentMethodsViewModel(repo, familyId, connectivity, onInsufficientRole)
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        return vm
    }

    private fun PaymentMethodsViewModel.content() = uiState.value as PaymentMethodsUiState.Content

    private fun repoWith(methods: List<PaymentMethod>? = all, stale: Boolean = false) =
        FakeRepository().apply { data.value = PaymentMethodsData(methods, stale) }

    // --- Estados ---

    @Test
    fun alAbrirSinDatos_muestraCargando_yPideLaFamiliaCorrecta() = runTest {
        val repo = FakeRepository().apply { gate = CompletableDeferred() }

        val vm = create(repo, familyId = "f-7")

        assertEquals(PaymentMethodsUiState.Loading, vm.uiState.value)
        assertEquals(listOf("f-7"), repo.refreshed)
        assertEquals(listOf("f-7"), repo.requestedFamilies)
    }

    @Test
    fun sinDatosYRefrescoFallido_muestraError_yReintentarCarga() = runTest {
        val repo = FakeRepository().apply { onRefresh = { PaymentMethodResult.Failure(PaymentMethodFailure.Network) } }
        val vm = create(repo)
        assertEquals(PaymentMethodsUiState.Error, vm.uiState.value)

        repo.onRefresh = { it.data.value = PaymentMethodsData(all); PaymentMethodResult.Success(Unit) }
        vm.retry()
        advanceUntilIdle()

        assertEquals(2, repo.refreshed.size)
        assertTrue(vm.uiState.value is PaymentMethodsUiState.Content)
    }

    @Test
    fun conDatosGuardados_muestraContenidoMientrasRefresca() = runTest {
        val repo = repoWith().apply { gate = CompletableDeferred() }

        val vm = create(repo)

        assertTrue(vm.content().isRefreshing)
        repo.gate!!.complete(Unit)
        advanceUntilIdle()
        assertFalse(vm.content().isRefreshing)
    }

    @Test
    fun conDatosGuardados_unRefrescoFallido_conservaElContenido() = runTest {
        val repo = repoWith().apply { onRefresh = { PaymentMethodResult.Failure(PaymentMethodFailure.Network) } }

        val vm = create(repo)

        assertTrue(vm.uiState.value is PaymentMethodsUiState.Content)
        assertEquals(listOf(transferencia, efectivo), vm.content().active)
    }

    @Test
    fun datosAntiguos_seMarcan() = runTest {
        assertTrue(create(repoWith(stale = true)).content().isStale)
        assertFalse(create(repoWith(stale = false)).content().isStale)
    }

    // --- Contenido (CA-03, CA-04) ---

    @Test
    fun separaActivosYArchivados_conservandoElOrdenDelBackend() = runTest {
        val vm = create(repoWith())

        assertEquals(listOf(transferencia, efectivo), vm.content().active)
        assertEquals(listOf(vieja, otraArchivada), vm.content().archived)
        assertFalse(vm.content().isEmpty)
    }

    @Test
    fun sinArchivados_laListaDeArchivadosEstaVacia() = runTest {
        val vm = create(repoWith(listOf(efectivo, transferencia)))

        assertTrue(vm.content().archived.isEmpty())
        assertEquals(listOf(efectivo, transferencia), vm.content().active)
    }

    @Test
    fun sinMediosActivos_esVacio_aunqueHayaArchivados() = runTest {
        val vm = create(repoWith(listOf(vieja)))

        assertTrue(vm.content().isEmpty)
        assertEquals(listOf(vieja), vm.content().archived)
    }

    @Test
    fun listaVaciaDelBackend_esContenidoVacio_noCargando() = runTest {
        val vm = create(repoWith(emptyList()))

        assertTrue(vm.content().isEmpty)
        assertTrue(vm.content().archived.isEmpty())
    }

    @Test
    fun laSeccionDeArchivadosEmpiezaPlegada_yAlternar_desplegaYPliega() = runTest {
        val vm = create(repoWith())
        assertFalse(vm.content().archivedExpanded)

        vm.toggleArchived()
        advanceUntilIdle()
        assertTrue(vm.content().archivedExpanded)

        vm.toggleArchived()
        advanceUntilIdle()
        assertFalse(vm.content().archivedExpanded)
    }

    @Test
    fun losDatosNuevosDelRepositorio_seReflejan() = runTest {
        val repo = repoWith(listOf(efectivo))
        val vm = create(repo)

        repo.data.value = PaymentMethodsData(listOf(efectivo, transferencia, vieja))
        advanceUntilIdle()

        assertEquals(listOf(efectivo, transferencia), vm.content().active)
        assertEquals(listOf(vieja), vm.content().archived)
    }

    // --- Refresco ---

    @Test
    fun alIniciar_refrescaUnaVez() = runTest {
        val repo = repoWith()

        create(repo)

        assertEquals(1, repo.refreshed.size)
    }

    @Test
    fun alVolverAPrimerPlano_refresca_peroSiHayUnoEnCurso_seIgnora() = runTest {
        val repo = repoWith().apply { gate = CompletableDeferred() }
        val vm = create(repo)

        vm.onStart()
        advanceUntilIdle()
        assertEquals(1, repo.refreshed.size)

        repo.gate!!.complete(Unit)
        advanceUntilIdle()
        vm.onStart()
        advanceUntilIdle()
        assertEquals(2, repo.refreshed.size)
    }

    @Test
    fun alRecuperarLaRed_refresca_peroNoEnLaPrimeraEmision() = runTest {
        val repo = repoWith()
        val connectivity = FakeConnectivity().apply { flow.tryEmit(true) }
        create(repo, connectivity)
        assertEquals(1, repo.refreshed.size)

        connectivity.flow.emit(false)
        advanceUntilIdle()
        assertEquals(1, repo.refreshed.size)

        connectivity.flow.emit(true)
        advanceUntilIdle()
        assertEquals(2, repo.refreshed.size)
    }

    // --- Formulario (RF-06, RF-07, RF-12, RF-13) ---

    private fun PaymentMethodsViewModel.form() = content().form

    private fun TestScope.typed(vm: PaymentMethodsViewModel, text: String) {
        vm.onNameChange(text)
        advanceUntilIdle()
    }

    @Test
    fun abrirElAlta_dejaElFormularioVacio() = runTest {
        val vm = create(repoWith())

        vm.openCreate()
        advanceUntilIdle()

        val form = vm.form()!!
        assertEquals(PaymentMethodFormTarget.Create, form.target)
        assertEquals("", form.text)
        assertNull(form.error)
        assertFalse(form.isSaving)
    }

    @Test
    fun abrirElRenombrado_empiezaConElNombreActual_yUnArchivadoNoSeRenombra() = runTest {
        val vm = create(repoWith())

        vm.openRename(vieja)
        advanceUntilIdle()
        assertNull(vm.form())

        vm.openRename(efectivo)
        advanceUntilIdle()
        assertEquals(PaymentMethodFormTarget.Rename("m-1"), vm.form()!!.target)
        assertEquals("Efectivo", vm.form()!!.text)
    }

    @Test
    fun conUnFormularioAbierto_otraAperturaSeIgnora() = runTest {
        val vm = create(repoWith())
        vm.openRename(efectivo)
        advanceUntilIdle()

        vm.openCreate()
        advanceUntilIdle()

        assertEquals(PaymentMethodFormTarget.Rename("m-1"), vm.form()!!.target)
    }

    @Test
    fun elTextoNoPasaDeCuarentaCaracteres() = runTest {
        val vm = create(repoWith())
        vm.openCreate()

        typed(vm, "a".repeat(55))

        assertEquals(40, vm.form()!!.text.length)
    }

    @Test
    fun guardarSinNombre_avisaDeQueEsObligatorio_sinLlamarAlBackend() = runTest {
        val repo = repoWith()
        val vm = create(repo)
        vm.openCreate()
        advanceUntilIdle()

        vm.save()
        advanceUntilIdle()
        assertEquals(PaymentMethodFormError.Required, vm.form()!!.error)

        typed(vm, "   ")
        vm.save()
        advanceUntilIdle()
        assertEquals(PaymentMethodFormError.Required, vm.form()!!.error)
        assertTrue(repo.created.isEmpty())
    }

    @Test
    fun escribirLimpiaElError() = runTest {
        val vm = create(repoWith())
        vm.openCreate()
        vm.save()
        advanceUntilIdle()

        typed(vm, "Bizum")

        assertNull(vm.form()!!.error)
    }

    @Test
    fun altaConExito_enviaElNombreRecortado_yCierraElFormulario() = runTest {
        val repo = repoWith()
        val vm = create(repo)
        vm.openCreate()
        typed(vm, "  Bizum  ")

        vm.save()
        advanceUntilIdle()

        assertEquals(listOf("Bizum"), repo.created)
        assertNull(vm.form())
        assertNull(vm.content().event)
    }

    @Test
    fun renombrarConExito_enviaElIdYElNombre_yCierra() = runTest {
        val repo = repoWith()
        val vm = create(repo)
        vm.openRename(efectivo)
        typed(vm, "Contante")

        vm.save()
        advanceUntilIdle()

        assertEquals(listOf("m-1" to "Contante"), repo.renamed)
        assertNull(vm.form())
    }

    @Test
    fun renombrarSinCambios_cierraSinLlamarAlBackend() = runTest {
        val repo = repoWith()
        val vm = create(repo)
        vm.openRename(efectivo)
        typed(vm, "  Efectivo ")

        vm.save()
        advanceUntilIdle()

        assertTrue(repo.renamed.isEmpty())
        assertNull(vm.form())
    }

    @Test
    fun nombreDuplicado_seMuestraJuntoAlCampo_yConservaElTexto() = runTest {
        val repo = repoWith().apply { saveFailure = PaymentMethodFailure.DuplicateName }
        val vm = create(repo)
        vm.openCreate()
        typed(vm, "efectivo")

        vm.save()
        advanceUntilIdle()

        val form = vm.form()!!
        assertEquals(PaymentMethodFormError.Duplicate, form.error)
        assertEquals("efectivo", form.text)
        assertFalse(form.isSaving)
        assertNull(vm.content().event)
    }

    @Test
    fun nombreInvalidoDelBackend_seMuestraJuntoAlCampo() = runTest {
        val repo = repoWith().apply { saveFailure = PaymentMethodFailure.InvalidName }
        val vm = create(repo)
        vm.openCreate()
        typed(vm, "Bizum")

        vm.save()
        advanceUntilIdle()

        assertEquals(PaymentMethodFormError.Failure(PaymentMethodFailure.InvalidName), vm.form()!!.error)
        assertEquals("Bizum", vm.form()!!.text)
    }

    @Test
    fun medioInexistente_cierraElFormulario_avisa_yRefresca() = runTest {
        val repo = repoWith().apply { saveFailure = PaymentMethodFailure.NotFound }
        val vm = create(repo)
        val refreshesBefore = repo.refreshed.size
        vm.openRename(efectivo)
        typed(vm, "Contante")

        vm.save()
        advanceUntilIdle()

        assertNull(vm.form())
        assertEquals(PaymentMethodsEvent.Failed(PaymentMethodFailure.NotFound), vm.content().event)
        assertEquals(refreshesBefore + 1, repo.refreshed.size)
    }

    @Test
    fun otrosFallos_muestranMensajeYConservanElTexto() = runTest {
        listOf(
            PaymentMethodFailure.Offline, PaymentMethodFailure.Network, PaymentMethodFailure.InsufficientRole,
            PaymentMethodFailure.InvalidData, PaymentMethodFailure.Unknown,
        ).forEach { failure ->
            val repo = repoWith().apply { saveFailure = failure }
            val vm = create(repo)
            vm.openCreate()
            typed(vm, "Bizum")

            vm.save()
            advanceUntilIdle()

            val form = vm.form()!!
            assertEquals(failure.toString(), PaymentMethodFormError.Failure(failure), form.error)
            assertEquals("Bizum", form.text)
            assertFalse(form.isSaving)
        }
    }

    @Test
    fun trasUnFalloSePuedeReintentar() = runTest {
        val repo = repoWith().apply { saveFailure = PaymentMethodFailure.Offline }
        val vm = create(repo)
        vm.openCreate()
        typed(vm, "Bizum")
        vm.save()
        advanceUntilIdle()

        repo.saveFailure = null
        vm.save()
        advanceUntilIdle()

        assertEquals(listOf("Bizum", "Bizum"), repo.created)
        assertNull(vm.form())
    }

    @Test
    fun dosPulsacionesDeGuardar_haceUnaSolaLlamada() = runTest {
        val repo = repoWith().apply { saveGate = CompletableDeferred() }
        val vm = create(repo)
        vm.openCreate()
        typed(vm, "Bizum")

        vm.save()
        vm.save()
        advanceUntilIdle()

        assertTrue(vm.form()!!.isSaving)
        repo.saveGate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf("Bizum"), repo.created)
        assertNull(vm.form())
    }

    @Test
    fun durantElGuardado_noSeEditaNiSeCierra() = runTest {
        val repo = repoWith().apply { saveGate = CompletableDeferred() }
        val vm = create(repo)
        vm.openCreate()
        typed(vm, "Bizum")
        vm.save()
        advanceUntilIdle()

        vm.onNameChange("Otro")
        vm.requestClose()
        vm.confirmDiscard()
        advanceUntilIdle()

        val form = vm.form()!!
        assertTrue(form.isSaving)
        assertEquals("Bizum", form.text)
        assertFalse(form.confirmingDiscard)
    }

    @Test
    fun cerrarConCambios_pideConfirmacion_yDescartarCierra() = runTest {
        val vm = create(repoWith())
        vm.openCreate()
        typed(vm, "Bizum")

        vm.requestClose()
        advanceUntilIdle()
        assertTrue(vm.form()!!.confirmingDiscard)

        vm.confirmDiscard()
        advanceUntilIdle()
        assertNull(vm.form())
    }

    @Test
    fun cancelarElDescarte_sigueEnElFormularioConLoEscrito() = runTest {
        val vm = create(repoWith())
        vm.openCreate()
        typed(vm, "Bizum")
        vm.requestClose()

        vm.dismissDiscard()
        advanceUntilIdle()

        val form = vm.form()!!
        assertFalse(form.confirmingDiscard)
        assertEquals("Bizum", form.text)
    }

    @Test
    fun cerrarSinCambios_cierraDirectamente() = runTest {
        val vm = create(repoWith())
        vm.openCreate()
        vm.requestClose()
        advanceUntilIdle()
        assertNull(vm.form())

        vm.openRename(efectivo)
        typed(vm, "Efectivo  ")
        vm.requestClose()
        advanceUntilIdle()
        assertNull(vm.form())
    }

    @Test
    fun alRenombrar_cambiarYVolverAlNombreInicial_noPideConfirmacion() = runTest {
        val vm = create(repoWith())
        vm.openRename(efectivo)
        typed(vm, "Efectivo 2")
        typed(vm, "Efectivo")

        vm.requestClose()
        advanceUntilIdle()

        assertNull(vm.form())
    }

    @Test
    fun consumirElEvento_loQuita() = runTest {
        val repo = repoWith().apply { saveFailure = PaymentMethodFailure.NotFound }
        val vm = create(repo)
        vm.openCreate()
        typed(vm, "Bizum")
        vm.save()
        advanceUntilIdle()

        vm.consumeEvent()
        advanceUntilIdle()

        assertNull(vm.content().event)
    }

    // --- Archivar y eliminar (RF-08, RF-09, RF-10, RF-12) ---

    private fun PaymentMethodsViewModel.pending() = content().pendingAction

    @Test
    fun pedirArchivar_abreLaConfirmacion_sinLlamarAlBackend() = runTest {
        val repo = repoWith()
        val vm = create(repo)

        vm.requestArchive(efectivo)
        advanceUntilIdle()

        assertEquals(PaymentMethodPendingAction.ConfirmArchive(efectivo), vm.pending())
        assertTrue(repo.deprecated.isEmpty())
    }

    @Test
    fun unArchivadoNoSePuedeArchivar() = runTest {
        val vm = create(repoWith())

        vm.requestArchive(vieja)
        advanceUntilIdle()

        assertNull(vm.pending())
    }

    @Test
    fun confirmarArchivar_llamaUnaVez_ySeCierraElDialogo() = runTest {
        val repo = repoWith()
        val vm = create(repo)
        vm.requestArchive(efectivo)

        vm.confirmPendingAction()
        advanceUntilIdle()

        assertEquals(listOf("m-1"), repo.deprecated)
        assertNull(vm.pending())
        assertNull(vm.content().event)
        assertFalse(vm.content().isBusy)
    }

    @Test
    fun cancelarArchivar_noLlamaAlBackend() = runTest {
        val repo = repoWith()
        val vm = create(repo)
        vm.requestArchive(efectivo)

        vm.dismissPendingAction()
        advanceUntilIdle()

        assertNull(vm.pending())
        assertTrue(repo.deprecated.isEmpty())
    }

    @Test
    fun confirmarEliminar_llamaUnaVez() = runTest {
        val repo = repoWith()
        val vm = create(repo)
        vm.requestDelete(vieja)
        advanceUntilIdle()
        assertEquals(PaymentMethodPendingAction.ConfirmDelete(vieja), vm.pending())

        vm.confirmPendingAction()
        advanceUntilIdle()

        assertEquals(listOf("m-3"), repo.deleted)
        assertNull(vm.pending())
        assertNull(vm.content().event)
    }

    @Test
    fun cancelarEliminar_noLlamaAlBackend() = runTest {
        val repo = repoWith()
        val vm = create(repo)
        vm.requestDelete(efectivo)

        vm.dismissPendingAction()
        advanceUntilIdle()

        assertTrue(repo.deleted.isEmpty())
        assertNull(vm.pending())
    }

    @Test
    fun eliminarUnActivoConMovimientos_ofreceArchivarlo_yAceptarArchiva() = runTest {
        val repo = repoWith().apply { actionFailures["m-1"] = PaymentMethodFailure.HasAssociatedItems }
        val vm = create(repo)
        vm.requestDelete(efectivo)
        vm.confirmPendingAction()
        advanceUntilIdle()

        assertEquals(PaymentMethodPendingAction.OfferArchive(efectivo), vm.pending())
        assertNull(vm.content().event)

        repo.actionFailures.clear()
        vm.confirmPendingAction()
        advanceUntilIdle()

        assertEquals(listOf("m-1"), repo.deprecated)
        assertNull(vm.pending())
    }

    @Test
    fun rechazarLaOfertaDeArchivar_noArchiva() = runTest {
        val repo = repoWith().apply { actionFailures["m-1"] = PaymentMethodFailure.HasAssociatedItems }
        val vm = create(repo)
        vm.requestDelete(efectivo)
        vm.confirmPendingAction()
        advanceUntilIdle()

        vm.dismissPendingAction()
        advanceUntilIdle()

        assertNull(vm.pending())
        assertTrue(repo.deprecated.isEmpty())
    }

    @Test
    fun eliminarUnArchivadoConMovimientos_soloInforma() = runTest {
        val repo = repoWith().apply { actionFailures["m-3"] = PaymentMethodFailure.HasAssociatedItems }
        val vm = create(repo)
        vm.requestDelete(vieja)
        vm.confirmPendingAction()
        advanceUntilIdle()

        assertNull(vm.pending())
        assertEquals(PaymentMethodsEvent.Failed(PaymentMethodFailure.HasAssociatedItems), vm.content().event)
    }

    @Test
    fun medioPredeterminadoDeAlguien_avisaSinCambios_alArchivarYAlEliminar() = runTest {
        val repo = repoWith().apply { actionFailures["m-1"] = PaymentMethodFailure.IsSomeonesDefault }
        val vm = create(repo)

        vm.requestArchive(efectivo)
        vm.confirmPendingAction()
        advanceUntilIdle()
        assertEquals(PaymentMethodsEvent.Failed(PaymentMethodFailure.IsSomeonesDefault), vm.content().event)
        vm.consumeEvent()
        advanceUntilIdle()

        vm.requestDelete(efectivo)
        vm.confirmPendingAction()
        advanceUntilIdle()
        assertEquals(PaymentMethodsEvent.Failed(PaymentMethodFailure.IsSomeonesDefault), vm.content().event)
        assertNull(vm.pending())
        assertTrue(vm.content().active.contains(efectivo))
    }

    @Test
    fun medioInexistente_avisaYRefresca() = runTest {
        val repo = repoWith().apply { actionFailures["m-1"] = PaymentMethodFailure.NotFound }
        val vm = create(repo)
        val before = repo.refreshed.size
        vm.requestArchive(efectivo)

        vm.confirmPendingAction()
        advanceUntilIdle()

        assertEquals(PaymentMethodsEvent.Failed(PaymentMethodFailure.NotFound), vm.content().event)
        assertEquals(before + 1, repo.refreshed.size)
    }

    @Test
    fun rolInsuficiente_avisaYNotificaAlContenedor() = runTest {
        val repo = repoWith().apply { actionFailures["m-1"] = PaymentMethodFailure.InsufficientRole }
        var notified = 0
        val vm = create(repo, onInsufficientRole = { notified++ })
        vm.requestDelete(efectivo)

        vm.confirmPendingAction()
        advanceUntilIdle()

        assertEquals(PaymentMethodsEvent.Failed(PaymentMethodFailure.InsufficientRole), vm.content().event)
        assertEquals(1, notified)
    }

    @Test
    fun rolInsuficienteAlGuardarElFormulario_tambienNotifica() = runTest {
        val repo = repoWith().apply { saveFailure = PaymentMethodFailure.InsufficientRole }
        var notified = 0
        val vm = create(repo, onInsufficientRole = { notified++ })
        vm.openCreate()
        typed(vm, "Bizum")

        vm.save()
        advanceUntilIdle()

        assertEquals(1, notified)
        assertEquals("Bizum", vm.form()!!.text)
    }

    @Test
    fun sinConexionOConFalloDeRed_avisaYElMedioNoCambia() = runTest {
        listOf(PaymentMethodFailure.Offline, PaymentMethodFailure.Network, PaymentMethodFailure.Unknown).forEach { f ->
            val repo = repoWith().apply { actionFailures["m-1"] = f }
            val vm = create(repo)
            vm.requestArchive(efectivo)

            vm.confirmPendingAction()
            advanceUntilIdle()

            assertEquals(f.toString(), PaymentMethodsEvent.Failed(f), vm.content().event)
            assertNull(vm.pending())
            assertFalse(vm.content().isBusy)
        }
    }

    @Test
    fun dosConfirmaciones_haceUnaSolaLlamada_yDuranteLaOperacionHayProgreso() = runTest {
        val repo = repoWith().apply { actionGate = CompletableDeferred() }
        val vm = create(repo)
        vm.requestDelete(efectivo)

        vm.confirmPendingAction()
        vm.confirmPendingAction()
        advanceUntilIdle()
        assertTrue(vm.content().isBusy)

        repo.actionGate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf("m-1"), repo.deleted)
        assertFalse(vm.content().isBusy)
    }

    @Test
    fun conUnaOperacionEnCurso_noSePidenOtrasAcciones() = runTest {
        val repo = repoWith().apply { actionGate = CompletableDeferred() }
        val vm = create(repo)
        vm.requestDelete(efectivo)
        vm.confirmPendingAction()
        advanceUntilIdle()

        vm.requestArchive(transferencia)
        vm.requestDelete(transferencia)
        advanceUntilIdle()

        assertNull(vm.pending())
    }

    @Test
    fun conElFormularioAbierto_noSePidenAcciones() = runTest {
        val vm = create(repoWith())
        vm.openCreate()

        vm.requestDelete(efectivo)
        vm.requestArchive(efectivo)
        advanceUntilIdle()

        assertNull(vm.pending())
    }

    @Test
    fun guardarElFormularioMientrasHayUnaOperacion_noLlamaAlBackend() = runTest {
        val repo = repoWith().apply { actionGate = CompletableDeferred() }
        val vm = create(repo)
        vm.requestDelete(efectivo)
        vm.confirmPendingAction()
        advanceUntilIdle()
        vm.openCreate()
        typed(vm, "Bizum")

        vm.save()
        advanceUntilIdle()

        assertTrue(repo.created.isEmpty())
        assertFalse(vm.form()!!.isSaving)
    }
}
