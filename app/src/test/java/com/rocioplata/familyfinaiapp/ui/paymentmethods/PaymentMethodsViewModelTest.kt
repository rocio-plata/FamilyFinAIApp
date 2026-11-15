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
        override suspend fun createPaymentMethod(familyId: String, name: String) = unused()
        override suspend fun renamePaymentMethod(familyId: String, paymentMethodId: String, name: String) = unused()
        override suspend fun deprecatePaymentMethod(familyId: String, paymentMethodId: String) = unused()
        override suspend fun deletePaymentMethod(familyId: String, paymentMethodId: String) = unused()

        private fun unused(): Nothing = error("No lo usa el listado")
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
    ): PaymentMethodsViewModel {
        val vm = PaymentMethodsViewModel(repo, familyId, connectivity)
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
}
