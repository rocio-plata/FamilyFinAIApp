package com.rocioplata.familyfinaiapp.ui.main

import com.rocioplata.familyfinaiapp.data.network.ConnectivityObserver
import com.rocioplata.familyfinaiapp.domain.categories.CategoryType
import com.rocioplata.familyfinaiapp.domain.header.Family
import com.rocioplata.familyfinaiapp.domain.header.FamilyRole
import com.rocioplata.familyfinaiapp.domain.header.HeaderData
import com.rocioplata.familyfinaiapp.domain.header.HeaderFailure
import com.rocioplata.familyfinaiapp.domain.header.HeaderRefreshResult
import com.rocioplata.familyfinaiapp.domain.header.HeaderRepository
import com.rocioplata.familyfinaiapp.domain.header.Timed
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    private val plata = Family("f-1", "Plata", FamilyRole.OWNER)
    private val depto = Family("f-2", "Depto", FamilyRole.MEMBER)

    private class FakeRepository : HeaderRepository {
        val data = MutableStateFlow(HeaderData())
        var calls = 0
        var gate: CompletableDeferred<Unit>? = null
        var onRefresh: (FakeRepository) -> HeaderRefreshResult = { HeaderRefreshResult() }
        override val header: Flow<HeaderData> = data
        override suspend fun refresh(): HeaderRefreshResult {
            calls++
            gate?.await()
            return onRefresh(this)
        }

        override suspend fun clear() {
            data.value = HeaderData()
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

    private fun families(vararg f: Family, stale: Boolean = false) =
        HeaderData(families = Timed(f.toList(), 1L), isStale = stale)

    private fun TestScope.create(repo: FakeRepository, connectivity: FakeConnectivity = FakeConnectivity()): MainViewModel {
        val vm = MainViewModel(repo, connectivity)
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        return vm
    }

    private fun MainViewModel.content() = uiState.value.families as FamiliesState.Content

    @Test
    fun alIniciar_laFavoritaEsLaPrimeraDeLaLista() = runTest {
        val repo = FakeRepository().apply { onRefresh = { it.data.value = families(plata, depto); HeaderRefreshResult() } }

        val vm = create(repo)

        assertEquals(plata, vm.content().activeFamily)
        assertEquals(listOf(plata, depto), vm.content().families)
        assertEquals(1, repo.calls)
    }

    @Test
    fun cambiarDeActiva_actualizaLaActivaYCierraElSelector() = runTest {
        val repo = FakeRepository().apply { onRefresh = { it.data.value = families(plata, depto); HeaderRefreshResult() } }
        val vm = create(repo)

        vm.openSelector()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.selectorOpen)
        vm.selectFamily("f-2")
        advanceUntilIdle()

        assertEquals(depto, vm.content().activeFamily)
        assertFalse(vm.uiState.value.selectorOpen)
    }

    @Test
    fun viewModelNuevo_vuelveALaFavorita() = runTest {
        val repo = FakeRepository().apply { onRefresh = { it.data.value = families(plata, depto); HeaderRefreshResult() } }
        val first = create(repo)
        first.selectFamily("f-2")
        advanceUntilIdle()

        val second = create(repo)

        assertEquals(depto, first.content().activeFamily)
        assertEquals(plata, second.content().activeFamily)
    }

    @Test
    fun unaSolaFamilia_noAbreElSelector() = runTest {
        val repo = FakeRepository().apply { onRefresh = { it.data.value = families(plata); HeaderRefreshResult() } }
        val vm = create(repo)

        vm.openSelector()
        advanceUntilIdle()

        assertFalse(vm.content().hasSelector)
        assertFalse(vm.uiState.value.selectorOpen)
    }

    @Test
    fun activaDesaparecida_vuelveALaFavorita() = runTest {
        val repo = FakeRepository().apply { onRefresh = { it.data.value = families(plata, depto); HeaderRefreshResult() } }
        val vm = create(repo)
        vm.selectFamily("f-2")
        advanceUntilIdle()

        repo.data.value = families(plata)
        advanceUntilIdle()

        assertEquals(plata, vm.content().activeFamily)
    }

    @Test
    fun dobleToqueEnReintentoOEnElSelector_noDuplicaCargasNiListas() = runTest {
        val repo = FakeRepository().apply {
            data.value = families(plata, depto)
            gate = CompletableDeferred()
        }
        val vm = create(repo)

        vm.retry()
        vm.retry()
        vm.onStart()
        vm.openSelector()
        vm.openSelector()
        advanceUntilIdle()

        assertEquals(1, repo.calls)
        assertTrue(vm.uiState.value.isRefreshing)
        assertTrue(vm.uiState.value.selectorOpen)

        repo.gate!!.complete(Unit)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isRefreshing)
    }

    @Test
    fun sinDatosYConFallo_muestraErrorYReintentoFunciona() = runTest {
        val repo = FakeRepository().apply { onRefresh = { HeaderRefreshResult(families = HeaderFailure.Network) } }
        val vm = create(repo)
        assertEquals(FamiliesState.Error, vm.uiState.value.families)

        repo.onRefresh = { it.data.value = families(plata); HeaderRefreshResult() }
        vm.retry()
        advanceUntilIdle()

        assertEquals(plata, vm.content().activeFamily)
        assertEquals(2, repo.calls)
    }

    @Test
    fun conDatosGuardadosYFallo_noMuestraErrorYMarcaDesactualizado() = runTest {
        val repo = FakeRepository().apply {
            data.value = families(plata, stale = true)
            onRefresh = { HeaderRefreshResult(families = HeaderFailure.Network) }
        }

        val vm = create(repo)

        assertTrue(vm.content().isStale)
    }

    @Test
    fun falloSoloDeInvitaciones_hayFamiliasYNoHayContador() = runTest {
        val repo = FakeRepository().apply {
            onRefresh = {
                it.data.value = families(plata, depto)
                HeaderRefreshResult(pendingInvitations = HeaderFailure.Network)
            }
        }

        val vm = create(repo)

        assertEquals(plata, vm.content().activeFamily)
        assertNull(vm.uiState.value.pendingInvitations)
    }

    @Test
    fun contadorYNombre_sonDelUsuarioYNoCambianAlElegirOtraFamilia() = runTest {
        val repo = FakeRepository().apply {
            data.value = families(plata, depto).copy(
                pendingInvitations = Timed(3, 1L),
                displayName = Timed("rocío", 1L),
            )
        }
        val vm = create(repo)

        vm.selectFamily("f-2")
        advanceUntilIdle()

        assertEquals(3, vm.uiState.value.pendingInvitations)
        assertEquals("rocío", vm.uiState.value.displayName)
        assertFalse(vm.uiState.value.hasLocalChanges)
    }

    @Test
    fun onInsufficientRole_vuelveAConsultarSinCambiarLaActiva() = runTest {
        val repo = FakeRepository().apply { onRefresh = { it.data.value = families(plata, depto); HeaderRefreshResult() } }
        val vm = create(repo)
        vm.selectFamily("f-2")
        advanceUntilIdle()

        repo.onRefresh = {
            it.data.value = families(plata, depto.copy(role = FamilyRole.OWNER))
            HeaderRefreshResult()
        }
        vm.onInsufficientRole()
        advanceUntilIdle()

        assertEquals(2, repo.calls)
        assertEquals("f-2", vm.content().activeFamily.id)
        assertEquals(FamilyRole.OWNER, vm.content().activeFamily.role)
    }

    @Test
    fun alRecuperarLaRed_refresca_peroNoEnLaPrimeraEmision() = runTest {
        val repo = FakeRepository().apply { data.value = families(plata) }
        val connectivity = FakeConnectivity().apply { flow.tryEmit(true) }
        create(repo, connectivity)
        assertEquals(1, repo.calls)

        connectivity.flow.emit(false)
        advanceUntilIdle()
        assertEquals(1, repo.calls)

        connectivity.flow.emit(true)
        advanceUntilIdle()
        assertEquals(2, repo.calls)
    }

    @Test
    fun familiasVacias_esNoData() = runTest {
        val repo = FakeRepository().apply { onRefresh = { it.data.value = families(); HeaderRefreshResult() } }

        val vm = create(repo)

        assertEquals(FamiliesState.NoData, vm.uiState.value.families)
    }

    // --- Ruta de categorías (T-12) ---

    private fun TestScope.createWithFamilies(vararg f: Family): Pair<FakeRepository, MainViewModel> {
        val repo = FakeRepository().apply { onRefresh = { it.data.value = families(*f); HeaderRefreshResult() } }
        return repo to create(repo)
    }

    private fun MainViewModel.route() = uiState.value.categoriesRoute

    @Test
    fun alIniciar_noHayGestionDeCategoriasAbierta() = runTest {
        val (_, vm) = createWithFamilies(plata)

        assertNull(vm.route())
    }

    @Test
    fun gestionar_abreElListadoDeLaFamiliaActiva() = runTest {
        val (_, vm) = createWithFamilies(plata, depto)

        vm.openCategories()
        advanceUntilIdle()

        assertEquals(CategoriesRoute.List, vm.route())
    }

    @Test
    fun gestionar_sinFamiliaActiva_seIgnora() = runTest {
        val repo = FakeRepository().apply { onRefresh = { HeaderRefreshResult(families = HeaderFailure.Network) } }
        val vm = create(repo)

        vm.openCategories()
        advanceUntilIdle()

        assertNull(vm.route())
    }

    @Test
    fun desdeElListado_seAbreAltaConElTipoYEdicion() = runTest {
        val (_, vm) = createWithFamilies(plata)
        vm.openCategories()

        vm.openCategoryCreate(CategoryType.INCOME)
        advanceUntilIdle()
        assertEquals(CategoriesRoute.Create(CategoryType.INCOME), vm.route())

        vm.openCategoryEdit("c-1")
        advanceUntilIdle()
        assertEquals(CategoriesRoute.Edit("c-1"), vm.route())
    }

    @Test
    fun altaYEdicion_noSeAbrenDesdeLasPestanas() = runTest {
        val (_, vm) = createWithFamilies(plata)

        vm.openCategoryCreate(CategoryType.EXPENSE)
        vm.openCategoryEdit("c-1")
        advanceUntilIdle()

        assertNull(vm.route())
    }

    @Test
    fun atras_vuelveUnNivelCadaVez() = runTest {
        val (_, vm) = createWithFamilies(plata)
        vm.openCategories()
        vm.openCategoryEdit("c-1")
        advanceUntilIdle()

        vm.categoriesBack()
        advanceUntilIdle()
        assertEquals(CategoriesRoute.List, vm.route())

        vm.categoriesBack()
        advanceUntilIdle()
        assertNull(vm.route())
    }

    @Test
    fun atras_desdeElAlta_vuelveAlListado() = runTest {
        val (_, vm) = createWithFamilies(plata)
        vm.openCategories()
        vm.openCategoryCreate(CategoryType.EXPENSE)
        advanceUntilIdle()

        vm.categoriesBack()
        advanceUntilIdle()

        assertEquals(CategoriesRoute.List, vm.route())
    }

    @Test
    fun atrasSinGestionAbierta_noHaceNada() = runTest {
        val (_, vm) = createWithFamilies(plata)

        vm.categoriesBack()
        advanceUntilIdle()

        assertNull(vm.route())
    }

    @Test
    fun tras_crearUnaCategoria_laAltaPasaAEdicionYAtrasVuelveAlListado() = runTest {
        val (_, vm) = createWithFamilies(plata)
        vm.openCategories()
        vm.openCategoryCreate(CategoryType.EXPENSE)

        vm.openCategoryEdit("c-nueva")
        advanceUntilIdle()
        assertEquals(CategoriesRoute.Edit("c-nueva"), vm.route())

        vm.categoriesBack()
        advanceUntilIdle()
        assertEquals(CategoriesRoute.List, vm.route())
    }

    @Test
    fun dentroDeLaGestion_nosePuedeCambiarDeFamilia() = runTest {
        val (_, vm) = createWithFamilies(plata, depto)
        vm.openCategories()
        advanceUntilIdle()

        vm.openSelector()
        vm.selectFamily("f-2")
        advanceUntilIdle()

        assertFalse(vm.uiState.value.selectorOpen)
        assertEquals(plata, vm.content().activeFamily)
        assertEquals(CategoriesRoute.List, vm.route())
    }

    @Test
    fun alVolverAInicio_sePuedeElegirOtraFamiliaYGestionarLaNueva() = runTest {
        val (_, vm) = createWithFamilies(plata, depto)
        vm.openCategories()
        vm.categoriesBack()
        advanceUntilIdle()

        vm.selectFamily("f-2")
        vm.openCategories()
        advanceUntilIdle()

        assertEquals(depto, vm.content().activeFamily)
        assertEquals(CategoriesRoute.List, vm.route())
    }

    @Test
    fun siLaFamiliaDeLaGestionDesaparece_vuelveALasPestanasYNoReaparece() = runTest {
        val (repo, vm) = createWithFamilies(plata, depto)
        vm.selectFamily("f-2")
        vm.openCategories()
        advanceUntilIdle()
        assertEquals(CategoriesRoute.List, vm.route())

        repo.data.value = families(plata)
        advanceUntilIdle()
        assertNull(vm.route())

        // Aunque la familia vuelva, la gestión no se reabre sola.
        repo.data.value = families(plata, depto)
        advanceUntilIdle()
        assertNull(vm.route())
    }

    @Test
    fun siSePierdenLosDatosDeFamilias_vuelveALasPestanas() = runTest {
        val (repo, vm) = createWithFamilies(plata)
        vm.openCategories()
        advanceUntilIdle()

        repo.data.value = HeaderData()
        advanceUntilIdle()

        assertNull(vm.route())
    }

    @Test
    fun unViewModelNuevo_empiezaSinGestionAbierta() = runTest {
        val repo = FakeRepository().apply { onRefresh = { it.data.value = families(plata); HeaderRefreshResult() } }
        val first = create(repo)
        first.openCategories()
        advanceUntilIdle()

        val second = create(repo)

        assertEquals(CategoriesRoute.List, first.route())
        assertNull(second.route())
    }

    @Test
    fun refrescarElCargoPorRolInsuficiente_noCierraLaGestion() = runTest {
        val (repo, vm) = createWithFamilies(plata)
        vm.openCategories()
        advanceUntilIdle()

        vm.onInsufficientRole()
        advanceUntilIdle()

        assertEquals(2, repo.calls)
        assertEquals(CategoriesRoute.List, vm.route())
    }
}
