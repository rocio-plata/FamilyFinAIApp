package com.rocioplata.familyfinaiapp.ui.main

import com.rocioplata.familyfinaiapp.data.network.ConnectivityObserver
import com.rocioplata.familyfinaiapp.domain.categories.CategoryType
import com.rocioplata.familyfinaiapp.domain.family.FamilyAccessFailure
import com.rocioplata.familyfinaiapp.domain.family.FamilyAccessResult
import com.rocioplata.familyfinaiapp.domain.family.FamilyManagementRepository
import com.rocioplata.familyfinaiapp.domain.header.Family
import com.rocioplata.familyfinaiapp.domain.header.FamilyRole
import com.rocioplata.familyfinaiapp.domain.header.HeaderData
import com.rocioplata.familyfinaiapp.domain.header.HeaderFailure
import com.rocioplata.familyfinaiapp.domain.header.HeaderRefreshResult
import com.rocioplata.familyfinaiapp.domain.header.HeaderRepository
import com.rocioplata.familyfinaiapp.domain.header.Timed
import com.rocioplata.familyfinaiapp.domain.movements.MovementType
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

        override suspend fun addFamily(family: Family) = Unit
        override suspend fun removeFamily(familyId: String) = Unit
        override suspend fun setFamilyOrder(orderedIds: List<String>) = Unit
        override suspend fun updateFamily(familyId: String, change: (Family) -> Family) = Unit
        override suspend fun setPendingInvitations(count: Int) = Unit
    }

    /** Solo reordena (lo único que usa el `MainViewModel`); al tener éxito actualiza la cabecera como el repositorio real. */
    private class FakeFamilyManagement(private val header: FakeRepository) : FamilyManagementRepository {
        val reorders = mutableListOf<List<String>>()
        var result: FamilyAccessResult<Unit> = FamilyAccessResult.Success(Unit)
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun reorderFamilies(orderedFamilyIds: List<String>): FamilyAccessResult<Unit> {
            reorders += orderedFamilyIds
            gate?.await()
            if (result is FamilyAccessResult.Success) {
                val families = header.data.value.families!!.value
                val ordered = orderedFamilyIds.mapNotNull { id -> families.firstOrNull { it.id == id } }
                header.data.value = header.data.value.copy(families = Timed(ordered, 1L))
            }
            return result
        }

        override suspend fun createFamily(name: String) = error("no se usa")
        override suspend fun settings(familyId: String) = error("no se usa")
        override suspend fun changeCurrency(familyId: String, currency: String) = error("no se usa")
        override suspend fun changeMemberRole(familyId: String, memberId: String, newRole: FamilyRole) = error("no se usa")
        override suspend fun removeMember(familyId: String, memberId: String) = error("no se usa")
        override suspend fun leaveFamily(familyId: String) = error("no se usa")
        override suspend fun sentInvitations(familyId: String) = error("no se usa")
        override suspend fun invite(familyId: String, email: String, role: FamilyRole) = error("no se usa")
        override suspend fun revokeInvitation(invitationId: String) = error("no se usa")
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

    private val managements = mutableMapOf<FakeRepository, FakeFamilyManagement>()

    private fun management(repo: FakeRepository) = managements.getOrPut(repo) { FakeFamilyManagement(repo) }

    private fun TestScope.create(repo: FakeRepository, connectivity: FakeConnectivity = FakeConnectivity()): MainViewModel {
        val vm = MainViewModel(repo, connectivity, management(repo))
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
    fun unaSolaFamilia_abreElSelector() = runTest {
        val repo = FakeRepository().apply { onRefresh = { it.data.value = families(plata); HeaderRefreshResult() } }
        val vm = create(repo)

        vm.openSelector()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.selectorOpen)
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

    private fun MainViewModel.route() = uiState.value.managementRoute

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

        vm.managementBack()
        advanceUntilIdle()
        assertEquals(CategoriesRoute.List, vm.route())

        vm.managementBack()
        advanceUntilIdle()
        assertNull(vm.route())
    }

    @Test
    fun atras_desdeElAlta_vuelveAlListado() = runTest {
        val (_, vm) = createWithFamilies(plata)
        vm.openCategories()
        vm.openCategoryCreate(CategoryType.EXPENSE)
        advanceUntilIdle()

        vm.managementBack()
        advanceUntilIdle()

        assertEquals(CategoriesRoute.List, vm.route())
    }

    @Test
    fun atrasSinGestionAbierta_noHaceNada() = runTest {
        val (_, vm) = createWithFamilies(plata)

        vm.managementBack()
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

        vm.managementBack()
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
        vm.managementBack()
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

    // --- Gestión de medios de pago (006, T-08) ---

    @Test
    fun medioDePago_gestionarAbreElListadoDeLaFamiliaActiva() = runTest {
        val (_, vm) = createWithFamilies(plata, depto)
        vm.selectFamily("f-2")

        vm.openPaymentMethods()
        advanceUntilIdle()

        assertEquals(PaymentMethodsRoute, vm.route())
    }

    @Test
    fun medioDePago_sinFamiliaActiva_seIgnora() = runTest {
        val repo = FakeRepository().apply { onRefresh = { HeaderRefreshResult(families = HeaderFailure.Network) } }
        val vm = create(repo)

        vm.openPaymentMethods()
        advanceUntilIdle()

        assertNull(vm.route())
    }

    @Test
    fun medioDePago_atrasVuelveDirectamenteALasPestanas() = runTest {
        val (_, vm) = createWithFamilies(plata)
        vm.openPaymentMethods()
        advanceUntilIdle()

        vm.managementBack()
        advanceUntilIdle()

        assertNull(vm.route())
        // Sin gestión abierta, atrás no hace nada.
        vm.managementBack()
        assertNull(vm.route())
    }

    @Test
    fun medioDePago_noSePuedeCambiarDeFamiliaConLaGestionAbierta() = runTest {
        val (_, vm) = createWithFamilies(plata, depto)
        vm.openPaymentMethods()
        advanceUntilIdle()

        vm.openSelector()
        vm.selectFamily("f-2")
        advanceUntilIdle()

        assertFalse(vm.uiState.value.selectorOpen)
        assertEquals(plata, vm.content().activeFamily)
        assertEquals(PaymentMethodsRoute, vm.route())
    }

    @Test
    fun medioDePago_alVolverAInicioSePuedeElegirOtraFamiliaYGestionarLaNueva() = runTest {
        val (_, vm) = createWithFamilies(plata, depto)
        vm.openPaymentMethods()
        vm.managementBack()
        advanceUntilIdle()

        vm.selectFamily("f-2")
        vm.openPaymentMethods()
        advanceUntilIdle()

        assertEquals(depto, vm.content().activeFamily)
        assertEquals(PaymentMethodsRoute, vm.route())
    }

    @Test
    fun medioDePago_siLaFamiliaDeLaGestionDesaparece_vuelveALasPestanasYNoReaparece() = runTest {
        val (repo, vm) = createWithFamilies(plata, depto)
        vm.selectFamily("f-2")
        vm.openPaymentMethods()
        advanceUntilIdle()
        assertEquals(PaymentMethodsRoute, vm.route())

        repo.data.value = families(plata)
        advanceUntilIdle()
        assertNull(vm.route())

        repo.data.value = families(plata, depto)
        advanceUntilIdle()
        assertNull(vm.route())
    }

    @Test
    fun medioDePago_noSeAbreSobreLaGestionDeCategorias_niAlReves() = runTest {
        val (_, vm) = createWithFamilies(plata)
        vm.openCategories()
        advanceUntilIdle()

        vm.openPaymentMethods()
        advanceUntilIdle()
        assertEquals(CategoriesRoute.List, vm.route())

        vm.managementBack()
        vm.openPaymentMethods()
        advanceUntilIdle()

        vm.openCategories()
        vm.openCategoryCreate(CategoryType.EXPENSE)
        vm.openCategoryEdit("c-1")
        advanceUntilIdle()
        assertEquals(PaymentMethodsRoute, vm.route())
    }

    @Test
    fun medioDePago_refrescarElCargoPorRolInsuficiente_noCierraLaGestion() = runTest {
        val (repo, vm) = createWithFamilies(plata)
        vm.openPaymentMethods()
        advanceUntilIdle()

        vm.onInsufficientRole()
        advanceUntilIdle()

        assertEquals(2, repo.calls)
        assertEquals(PaymentMethodsRoute, vm.route())
    }

    // --- Pestaña seleccionada (T-11) ---

    @Test
    fun alIniciar_laPestanaEsInicio() = runTest {
        val (_, vm) = createWithFamilies(plata)

        assertEquals(MainDestination.Home, vm.uiState.value.selectedTab)
    }

    @Test
    fun seleccionarPestana_laConservaElEstado() = runTest {
        val (_, vm) = createWithFamilies(plata)

        vm.selectTab(MainDestination.Reports)
        advanceUntilIdle()

        assertEquals(MainDestination.Reports, vm.uiState.value.selectedTab)
    }

    @Test
    fun laPestana_sobrevivePorqueVivenEnElViewModel_yNoDependeDeLaFamilia() = runTest {
        val repo = FakeRepository().apply { onRefresh = { it.data.value = families(plata, depto); HeaderRefreshResult() } }
        val vm = create(repo)
        vm.selectTab(MainDestination.Movements)
        advanceUntilIdle()

        vm.selectFamily("f-2")
        advanceUntilIdle()

        assertEquals(MainDestination.Movements, vm.uiState.value.selectedTab)
    }

    @Test
    fun viewModelNuevo_empiezaEnInicio() = runTest {
        val repo = FakeRepository().apply { onRefresh = { it.data.value = families(plata); HeaderRefreshResult() } }
        val first = create(repo)
        first.selectTab(MainDestination.Budgets)
        advanceUntilIdle()

        val second = create(repo)

        assertEquals(MainDestination.Home, second.uiState.value.selectedTab)
    }

    @Test
    fun conUnaGestionAbierta_seIgnoraElCambioDePestana() = runTest {
        val (_, vm) = createWithFamilies(plata)
        vm.selectTab(MainDestination.Assistant)
        vm.openCategories()
        advanceUntilIdle()

        vm.selectTab(MainDestination.Reports)
        advanceUntilIdle()
        vm.managementBack()
        advanceUntilIdle()

        assertEquals(MainDestination.Assistant, vm.uiState.value.selectedTab)
    }

    // --- Instancia de la familia activa (T-14 de la 008: clave del ViewModel del dashboard) ---

    private fun MainViewModel.instance() = uiState.value.activeFamilyInstance
    private fun MainViewModel.activeId() = (uiState.value.families as FamiliesState.Content).activeFamily.id

    @Test
    fun instanciaDeFamilia_empiezaEnCero() = runTest {
        val (_, vm) = createWithFamilies(plata, depto)

        assertEquals(0, vm.instance())
        assertEquals("f-1", vm.activeId())
    }

    @Test
    fun instanciaDeFamilia_cambiaConCadaCambioDeFamilia_tambienAlVolverALaAnterior() = runTest {
        val (_, vm) = createWithFamilies(plata, depto)
        val start = vm.instance()

        vm.selectFamily("f-2")
        advanceUntilIdle()
        val second = vm.instance()
        vm.selectFamily("f-1")
        advanceUntilIdle()
        val third = vm.instance()

        assertEquals(start + 1, second)
        assertEquals(start + 2, third)
        assertEquals("f-1", vm.activeId())
    }

    @Test
    fun instanciaDeFamilia_elegirLaMismaFamilia_noCambia() = runTest {
        val (_, vm) = createWithFamilies(plata, depto)
        val start = vm.instance()

        vm.selectFamily("f-1")
        vm.selectFamily("f-1")
        advanceUntilIdle()

        assertEquals(start, vm.instance())
    }

    @Test
    fun instanciaDeFamilia_refrescarLaCabecera_noCambia() = runTest {
        val (repo, vm) = createWithFamilies(plata, depto)
        vm.selectFamily("f-2")
        advanceUntilIdle()
        val before = vm.instance()

        vm.retry()
        vm.onStart()
        repo.data.value = families(plata, depto)
        advanceUntilIdle()

        assertEquals(before, vm.instance())
        assertEquals("f-2", vm.activeId())
    }

    @Test
    fun instanciaDeFamilia_cambiarDePestanaOAbrirUnaGestion_noCambia() = runTest {
        val (_, vm) = createWithFamilies(plata, depto)
        val start = vm.instance()

        vm.selectTab(MainDestination.Movements)
        vm.selectTab(MainDestination.Home)
        vm.openCategories()
        vm.managementBack()
        vm.openMovementCreate("c-food", MovementType.EXPENSE)
        vm.managementBack()
        advanceUntilIdle()

        assertEquals(start, vm.instance())
    }

    @Test
    fun instanciaDeFamilia_siLaFamiliaActivaDesaparece_cambiaAlPasarALaFavorita() = runTest {
        val (repo, vm) = createWithFamilies(plata, depto)
        vm.selectFamily("f-2")
        advanceUntilIdle()
        val before = vm.instance()

        repo.data.value = families(plata)
        advanceUntilIdle()

        assertEquals("f-1", vm.activeId())
        assertEquals(before + 1, vm.instance())
    }

    @Test
    fun instanciaDeFamilia_conLaCabeceraSinDatos_noSeCuenta_yAlRecuperarlaSigueIgual() = runTest {
        val repo = FakeRepository().apply { onRefresh = { HeaderRefreshResult(families = HeaderFailure.Network) } }
        val vm = create(repo)
        assertEquals(0, vm.instance())

        repo.data.value = families(plata, depto)
        advanceUntilIdle()

        assertEquals(0, vm.instance())
    }

    // --- Rutas de movimientos (T-12) ---

    @Test
    fun movimiento_nuevoAbreElAltaDeLaFamiliaActiva() = runTest {
        val (_, vm) = createWithFamilies(plata)

        vm.openMovementCreate()
        advanceUntilIdle()

        assertEquals(MovementRoute.Create(), vm.route())
    }

    @Test
    fun movimiento_nuevoDesdeUnaCategoria_llevaLaCategoriaYElTipo() = runTest {
        val (_, vm) = createWithFamilies(plata)

        vm.openMovementCreate("c-food", MovementType.INCOME)
        advanceUntilIdle()

        assertEquals(MovementRoute.Create("c-food", MovementType.INCOME), vm.route())
    }

    @Test
    fun movimiento_nuevoConElMas_sigueSinCategoriaNiTipo() = runTest {
        val (_, vm) = createWithFamilies(plata)

        vm.openMovementCreate()
        advanceUntilIdle()

        val route = vm.route() as MovementRoute.Create
        assertNull(route.categoryId)
        assertNull(route.type)
    }

    @Test
    fun movimiento_variasPulsacionesSobreCategorias_abrenSoloUnFormulario() = runTest {
        val (_, vm) = createWithFamilies(plata)

        vm.openMovementCreate("c-food", MovementType.EXPENSE)
        vm.openMovementCreate("c-salary", MovementType.INCOME)
        vm.openMovementCreate()
        advanceUntilIdle()

        assertEquals(MovementRoute.Create("c-food", MovementType.EXPENSE), vm.route())
    }

    @Test
    fun movimiento_cadaAperturaDelAltaEsUnaInstanciaNueva_tambienDesdeUnaCategoria() = runTest {
        val (_, vm) = createWithFamilies(plata)
        vm.openMovementCreate("c-food", MovementType.EXPENSE)
        advanceUntilIdle()
        val first = vm.uiState.value.managementRouteInstance

        vm.managementBack()
        vm.openMovementCreate("c-food", MovementType.EXPENSE)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.managementRouteInstance > first)
    }

    @Test
    fun movimiento_atrasDesdeElAltaConCategoria_vuelveALasPestanasYConservaLaPestana() = runTest {
        val (_, vm) = createWithFamilies(plata)
        vm.openMovementCreate("c-food", MovementType.EXPENSE)
        advanceUntilIdle()

        vm.managementBack()
        advanceUntilIdle()

        assertNull(vm.route())
        assertEquals(MainDestination.Home, vm.uiState.value.selectedTab)
    }

    @Test
    fun movimiento_laCategoriaDeUnaFamiliaQueDesaparece_cierraElAlta() = runTest {
        val (repo, vm) = createWithFamilies(plata, depto)
        vm.openMovementCreate("c-food", MovementType.EXPENSE)
        advanceUntilIdle()

        repo.data.value = families(depto)
        advanceUntilIdle()

        assertNull(vm.route())
    }

    @Test
    fun movimiento_detalleSeAbreDesdeLasPestanas() = runTest {
        val (_, vm) = createWithFamilies(plata)

        vm.openMovementDetail("m-1")
        advanceUntilIdle()

        assertEquals(MovementRoute.Detail("m-1"), vm.route())
    }

    @Test
    fun movimiento_sinFamiliaActiva_seIgnora() = runTest {
        val vm = create(FakeRepository())

        vm.openMovementCreate()
        vm.openMovementDetail("m-1")
        advanceUntilIdle()

        assertNull(vm.route())
    }

    @Test
    fun movimiento_edicionSoloDesdeSuDetalle() = runTest {
        val (_, vm) = createWithFamilies(plata)

        vm.openMovementEdit("m-1")
        advanceUntilIdle()
        assertNull(vm.route())

        vm.openMovementDetail("m-1")
        vm.openMovementEdit("m-2")
        advanceUntilIdle()
        assertEquals(MovementRoute.Detail("m-1"), vm.route())

        vm.openMovementEdit("m-1")
        advanceUntilIdle()
        assertEquals(MovementRoute.Edit("m-1"), vm.route())
    }

    @Test
    fun movimiento_edicionNoSeAbreDesdeElAlta() = runTest {
        val (_, vm) = createWithFamilies(plata)
        vm.openMovementCreate()

        vm.openMovementEdit("m-1")
        advanceUntilIdle()

        assertEquals(MovementRoute.Create(), vm.route())
    }

    @Test
    fun movimiento_atrasDesdeEdicionVuelveAlDetalle_yDesdeElDetalleALasPestanas() = runTest {
        val (_, vm) = createWithFamilies(plata)
        vm.openMovementDetail("m-1")
        vm.openMovementEdit("m-1")
        advanceUntilIdle()

        vm.managementBack()
        advanceUntilIdle()
        assertEquals(MovementRoute.Detail("m-1"), vm.route())

        vm.managementBack()
        advanceUntilIdle()
        assertNull(vm.route())
    }

    @Test
    fun movimiento_atrasDesdeElAltaVuelveALasPestanas() = runTest {
        val (_, vm) = createWithFamilies(plata)
        vm.openMovementCreate()
        advanceUntilIdle()

        vm.managementBack()
        advanceUntilIdle()

        assertNull(vm.route())
    }

    @Test
    fun movimiento_alCerrarSeVuelveALaPestanaDeOrigen() = runTest {
        val (_, vm) = createWithFamilies(plata)
        vm.selectTab(MainDestination.Movements)
        vm.openMovementCreate()
        advanceUntilIdle()
        assertEquals(MainDestination.Movements, vm.uiState.value.selectedTab)

        vm.managementBack()
        advanceUntilIdle()

        assertNull(vm.route())
        assertEquals(MainDestination.Movements, vm.uiState.value.selectedTab)
    }

    @Test
    fun movimiento_noSeAbreSobreOtraGestion_niAlReves() = runTest {
        val (_, vm) = createWithFamilies(plata)
        vm.openCategories()
        advanceUntilIdle()
        vm.openMovementCreate()
        vm.openMovementDetail("m-1")
        advanceUntilIdle()
        assertEquals(CategoriesRoute.List, vm.route())

        vm.managementBack()
        vm.openMovementCreate()
        advanceUntilIdle()
        vm.openCategories()
        vm.openPaymentMethods()
        vm.openMovementDetail("m-1")
        advanceUntilIdle()

        assertEquals(MovementRoute.Create(), vm.route())
    }

    @Test
    fun movimiento_noSePuedeCambiarDeFamiliaConLaRutaAbierta() = runTest {
        val repo = FakeRepository().apply { onRefresh = { it.data.value = families(plata, depto); HeaderRefreshResult() } }
        val vm = create(repo)
        advanceUntilIdle()
        vm.openMovementCreate()
        advanceUntilIdle()

        vm.openSelector()
        vm.selectFamily("f-2")
        advanceUntilIdle()

        assertEquals("f-1", vm.content().activeFamily.id)
        assertFalse(vm.uiState.value.selectorOpen)
        assertEquals(MovementRoute.Create(), vm.route())
    }

    @Test
    fun movimiento_siLaFamiliaDeLaRutaDesaparece_vuelveALasPestanasYNoReaparece() = runTest {
        val repo = FakeRepository().apply { onRefresh = { it.data.value = families(plata, depto); HeaderRefreshResult() } }
        val vm = create(repo)
        advanceUntilIdle()
        vm.openMovementDetail("m-1")
        advanceUntilIdle()

        repo.data.value = families(depto)
        advanceUntilIdle()
        assertNull(vm.route())

        repo.data.value = families(plata, depto)
        advanceUntilIdle()
        assertNull(vm.route())
    }

    @Test
    fun movimiento_cadaAperturaTieneUnaInstanciaNueva_yAtrasDentroDeLaRutaLaConserva() = runTest {
        val (_, vm) = createWithFamilies(plata)
        assertEquals(0, vm.uiState.value.managementRouteInstance)

        vm.openMovementCreate()
        advanceUntilIdle()
        val first = vm.uiState.value.managementRouteInstance
        vm.managementBack()
        advanceUntilIdle()
        assertEquals(0, vm.uiState.value.managementRouteInstance)

        vm.openMovementCreate()
        advanceUntilIdle()
        val second = vm.uiState.value.managementRouteInstance
        assertTrue(first > 0)
        assertTrue(second > first)

        vm.managementBack()
        vm.openMovementDetail("m-1")
        advanceUntilIdle()
        val detail = vm.uiState.value.managementRouteInstance
        vm.openMovementEdit("m-1")
        advanceUntilIdle()
        val edit = vm.uiState.value.managementRouteInstance
        vm.managementBack()
        advanceUntilIdle()

        assertTrue(edit > detail)
        assertEquals(edit, vm.uiState.value.managementRouteInstance)
    }

    @Test
    fun movimiento_cerrarLaGestionDeUnaVez_vuelveALasPestanasDesdeLaEdicion() = runTest {
        val (_, vm) = createWithFamilies(plata)
        vm.selectTab(MainDestination.Movements)
        vm.openMovementDetail("m-1")
        vm.openMovementEdit("m-1")
        advanceUntilIdle()
        assertEquals(MovementRoute.Edit("m-1"), vm.route())

        vm.closeManagement()
        advanceUntilIdle()

        assertNull(vm.route())
        assertEquals(MainDestination.Movements, vm.uiState.value.selectedTab)
    }

    @Test
    fun cerrarLaGestionSinNingunaAbierta_noHaceNada() = runTest {
        val (_, vm) = createWithFamilies(plata)

        vm.closeManagement()
        advanceUntilIdle()

        assertNull(vm.route())
    }

    // --- Gestión de familias (009, T-14) ---

    private val nueva = Family("f-3", "Nueva", FamilyRole.OWNER, "CLP", memberCount = 1)

    private fun MainViewModel.open(route: ManagementRoute) = when (route) {
        FamilyRoute.Manage -> openFamilyManagement()
        FamilyRoute.Create -> openFamilyCreate()
        InvitationsRoute -> openInvitations()
        else -> error("no es una ruta de familias")
    }

    private val familyRoutes = listOf(FamilyRoute.Manage, FamilyRoute.Create, InvitationsRoute)

    @Test
    fun familias_cadaRutaSeAbreYAtrasVuelveALaPestanaDeOrigen() = runTest {
        val (_, vm) = createWithFamilies(plata, depto)

        for (route in familyRoutes) {
            for (tab in MainDestination.entries) {
                vm.selectTab(tab)
                vm.open(route)
                advanceUntilIdle()
                assertEquals("$route desde $tab", route, vm.route())

                vm.managementBack()
                advanceUntilIdle()
                assertNull("$route desde $tab", vm.route())
                assertEquals(tab, vm.uiState.value.selectedTab)
            }
        }
    }

    @Test
    fun miFamilia_seLigaALaFamiliaActiva_yNoSePuedeCambiarDeFamiliaMientrasEstaAbierta() = runTest {
        val (_, vm) = createWithFamilies(plata, depto)
        vm.selectFamily("f-2")

        vm.openFamilyManagement()
        advanceUntilIdle()
        vm.selectFamily("f-1")
        advanceUntilIdle()

        assertEquals(FamilyRoute.Manage, vm.route())
        assertEquals(depto, vm.content().activeFamily)
    }

    @Test
    fun crearFamiliaEInvitaciones_seAbrenSinNingunaFamilia_peroMiFamiliaNo() = runTest {
        val (_, vm) = createWithFamilies()
        assertEquals(FamiliesState.NoData, vm.uiState.value.families)

        vm.openFamilyManagement()
        advanceUntilIdle()
        assertNull(vm.route())

        vm.openFamilyCreate()
        advanceUntilIdle()
        assertEquals(FamilyRoute.Create, vm.route())
        vm.managementBack()
        vm.openInvitations()
        advanceUntilIdle()
        assertEquals(InvitationsRoute, vm.route())
    }

    @Test
    fun conLasFamiliasSinCargar_soloSePuedeCrearOVerInvitaciones() = runTest {
        val repo = FakeRepository().apply { onRefresh = { HeaderRefreshResult(families = HeaderFailure.Network) } }
        val vm = create(repo)
        assertEquals(FamiliesState.Error, vm.uiState.value.families)

        vm.openFamilyManagement()
        advanceUntilIdle()
        assertNull(vm.route())

        vm.openInvitations()
        advanceUntilIdle()
        assertEquals(InvitationsRoute, vm.route())
    }

    @Test
    fun noSeAbreUnaGestionSobreOtra() = runTest {
        val (_, vm) = createWithFamilies(plata, depto)
        val others = familyRoutes + listOf(CategoriesRoute.List, PaymentMethodsRoute, MovementRoute.Create())

        for (first in familyRoutes) {
            for (second in others.filter { it != first }) {
                vm.open(first)
                advanceUntilIdle()
                when (second) {
                    CategoriesRoute.List -> vm.openCategories()
                    PaymentMethodsRoute -> vm.openPaymentMethods()
                    is MovementRoute.Create -> vm.openMovementCreate()
                    else -> vm.open(second)
                }
                advanceUntilIdle()
                assertEquals("$second sobre $first", first, vm.route())
                vm.managementBack()
                advanceUntilIdle()
            }
        }
    }

    @Test
    fun lasGestionesDeCategoriasYMediosDePago_noSeAbrenSobreLasDeFamilias() = runTest {
        val (_, vm) = createWithFamilies(plata)
        vm.openCategories()
        advanceUntilIdle()

        vm.openFamilyManagement()
        vm.openFamilyCreate()
        vm.openInvitations()
        advanceUntilIdle()

        assertEquals(CategoriesRoute.List, vm.route())
    }

    @Test
    fun abrirUnaGestionDesdeElSelector_loCierra() = runTest {
        val (_, vm) = createWithFamilies(plata, depto)
        vm.openSelector()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.selectorOpen)

        vm.openFamilyManagement()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.selectorOpen)
        assertEquals(FamilyRoute.Manage, vm.route())
    }

    @Test
    fun familiaCreada_pasaAActiva_vaAInicio_yAbreSuGestion() = runTest {
        val (repo, vm) = createWithFamilies(plata, depto)
        vm.selectTab(MainDestination.Movements)
        vm.openFamilyCreate()
        advanceUntilIdle()
        val createInstance = vm.uiState.value.managementRouteInstance

        repo.data.value = families(plata, depto, nueva)
        vm.onFamilyCreated(nueva)
        advanceUntilIdle()

        assertEquals(nueva, vm.content().activeFamily)
        assertEquals(MainDestination.Home, vm.uiState.value.selectedTab)
        assertEquals(FamilyRoute.Manage, vm.route())
        assertTrue(vm.uiState.value.managementRouteInstance > createInstance)
        assertNull(vm.uiState.value.notice)

        vm.managementBack()
        advanceUntilIdle()
        assertNull(vm.route())
        assertEquals(nueva, vm.content().activeFamily)
        assertEquals(MainDestination.Home, vm.uiState.value.selectedTab)
    }

    @Test
    fun familiaCreada_sinElFormularioAbierto_seIgnora() = runTest {
        val (repo, vm) = createWithFamilies(plata, depto)
        repo.data.value = families(plata, depto, nueva)

        vm.onFamilyCreated(nueva)
        advanceUntilIdle()

        assertEquals(plata, vm.content().activeFamily)
        assertNull(vm.route())
    }

    @Test
    fun familiaCreadaSinNingunaFamiliaAntes_tambienPasaAActivaYAbreSuGestion() = runTest {
        val (repo, vm) = createWithFamilies()
        vm.openFamilyCreate()
        advanceUntilIdle()

        repo.data.value = families(nueva)
        vm.onFamilyCreated(nueva)
        advanceUntilIdle()

        assertEquals(nueva, vm.content().activeFamily)
        assertEquals(FamilyRoute.Manage, vm.route())
    }

    @Test
    fun cambiarAUnaFamilia_cierraLaGestion_laActivaYVaAInicio() = runTest {
        val (_, vm) = createWithFamilies(plata, depto)
        vm.selectTab(MainDestination.Reports)
        vm.openInvitations()
        advanceUntilIdle()

        vm.switchToFamily("f-2")
        advanceUntilIdle()

        assertNull(vm.route())
        assertEquals(depto, vm.content().activeFamily)
        assertEquals(MainDestination.Home, vm.uiState.value.selectedTab)
    }

    @Test
    fun cambiarAUnaFamiliaQueNoEsta_soloCierraLaGestion() = runTest {
        val (_, vm) = createWithFamilies(plata, depto)
        vm.selectTab(MainDestination.Reports)
        vm.openInvitations()
        advanceUntilIdle()

        vm.switchToFamily("f-9")
        advanceUntilIdle()

        assertNull(vm.route())
        assertEquals(plata, vm.content().activeFamily)
        assertEquals(MainDestination.Reports, vm.uiState.value.selectedTab)
    }

    @Test
    fun laFamiliaDeMiFamiliaDesaparece_cierraAvisaConSuNombreYVuelveAInicioConLaFavorita() = runTest {
        val (repo, vm) = createWithFamilies(plata, depto)
        vm.selectFamily("f-2")
        vm.selectTab(MainDestination.Budgets)
        vm.openFamilyManagement()
        advanceUntilIdle()

        repo.data.value = families(plata)
        advanceUntilIdle()

        assertNull(vm.route())
        assertEquals(MainNotice.NoLongerMember("Depto"), vm.uiState.value.notice)
        assertEquals(plata, vm.content().activeFamily)
        assertEquals(MainDestination.Home, vm.uiState.value.selectedTab)

        vm.consumeNotice()
        advanceUntilIdle()
        assertNull(vm.uiState.value.notice)
    }

    @Test
    fun laFamiliaDeOtraGestionDesaparece_cierraYAvisa_peroSinCambiarDePestana() = runTest {
        val (repo, vm) = createWithFamilies(plata, depto)
        vm.selectFamily("f-2")
        vm.selectTab(MainDestination.Movements)
        vm.openPaymentMethods()
        advanceUntilIdle()

        repo.data.value = families(plata)
        advanceUntilIdle()

        assertNull(vm.route())
        assertEquals(MainNotice.NoLongerMember("Depto"), vm.uiState.value.notice)
        assertEquals(MainDestination.Movements, vm.uiState.value.selectedTab)
    }

    @Test
    fun siUnaFamiliaDesapareceSinGestionAbierta_noHayAviso() = runTest {
        val (repo, vm) = createWithFamilies(plata, depto)

        repo.data.value = families(plata)
        advanceUntilIdle()

        assertNull(vm.uiState.value.notice)
    }

    @Test
    fun crearFamiliaEInvitaciones_noSeCierranNiAvisanSiCambianLasFamilias() = runTest {
        val (repo, vm) = createWithFamilies(plata, depto)
        vm.openInvitations()
        advanceUntilIdle()

        repo.data.value = families(plata)
        advanceUntilIdle()
        assertEquals(InvitationsRoute, vm.route())
        assertNull(vm.uiState.value.notice)

        vm.managementBack()
        vm.openFamilyCreate()
        repo.data.value = families()
        advanceUntilIdle()
        assertEquals(FamilyRoute.Create, vm.route())
        assertNull(vm.uiState.value.notice)
    }

    @Test
    fun siSePierdenLosDatosDeFamilias_laGestionLigadaSeCierraSinAviso() = runTest {
        val (repo, vm) = createWithFamilies(plata, depto)
        vm.openFamilyManagement()
        advanceUntilIdle()

        repo.data.value = HeaderData()
        advanceUntilIdle()

        assertNull(vm.route())
        assertNull(vm.uiState.value.notice)
    }

    // --- Reordenar las familias (009, T-15) ---

    private fun MainViewModel.order() = content().families.map { it.id }

    @Test
    fun reordenar_seVeEnseguida_yAlTenerExitoQuedaGuardado_sinCambiarLaActiva() = runTest {
        val (repo, vm) = createWithFamilies(plata, depto, nueva)
        vm.selectFamily("f-2")
        vm.openSelector()
        management(repo).gate = CompletableDeferred()

        vm.moveFamily("f-3", 0)
        advanceUntilIdle()

        // Mientras se guarda: orden nuevo a la vista y el selector no admite otro movimiento.
        assertEquals(listOf("f-3", "f-1", "f-2"), vm.order())
        assertTrue(vm.uiState.value.isReordering)
        assertEquals(listOf(listOf("f-3", "f-1", "f-2")), management(repo).reorders)

        management(repo).gate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("f-3", "f-1", "f-2"), vm.order())
        assertFalse(vm.uiState.value.isReordering)
        assertNull(vm.uiState.value.selectorError)
        assertEquals(depto, vm.content().activeFamily)
        assertTrue(vm.uiState.value.selectorOpen)
    }

    @Test
    fun reordenar_conFallo_vuelveElOrdenAnteriorYSeMuestraElError() = runTest {
        val (repo, vm) = createWithFamilies(plata, depto, nueva)
        management(repo).result = FamilyAccessResult.Failure(FamilyAccessFailure.Network)
        vm.openSelector()

        vm.moveFamily("f-3", 0)
        advanceUntilIdle()

        assertEquals(listOf("f-1", "f-2", "f-3"), vm.order())
        assertEquals(FamilyAccessFailure.Network, vm.uiState.value.selectorError)
        assertFalse(vm.uiState.value.isReordering)
        assertEquals(1, repo.calls)
    }

    @Test
    fun reordenar_sinConexion_informaYDejaElOrden() = runTest {
        val (repo, vm) = createWithFamilies(plata, depto)
        management(repo).result = FamilyAccessResult.Failure(FamilyAccessFailure.Offline)

        vm.moveFamily("f-2", 0)
        advanceUntilIdle()

        assertEquals(listOf("f-1", "f-2"), vm.order())
        assertEquals(FamilyAccessFailure.Offline, vm.uiState.value.selectorError)
    }

    @Test
    fun reordenar_ordenInvalido_vuelveAConsultarLaCabecera() = runTest {
        val (repo, vm) = createWithFamilies(plata, depto)
        management(repo).result = FamilyAccessResult.Failure(FamilyAccessFailure.InvalidOrder)

        vm.moveFamily("f-2", 0)
        advanceUntilIdle()

        assertEquals(FamilyAccessFailure.InvalidOrder, vm.uiState.value.selectorError)
        assertEquals(2, repo.calls)
    }

    @Test
    fun reordenar_dosSeguidos_soloUnaLlamada() = runTest {
        val (repo, vm) = createWithFamilies(plata, depto, nueva)
        management(repo).gate = CompletableDeferred()

        vm.moveFamily("f-3", 0)
        advanceUntilIdle()
        vm.moveFamily("f-2", 0)
        vm.moveFamily("f-3", 2)
        advanceUntilIdle()
        management(repo).gate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, management(repo).reorders.size)
        assertEquals(listOf("f-3", "f-1", "f-2"), vm.order())
    }

    @Test
    fun reordenar_laActivaImplicitaSeConserva() = runTest {
        val (repo, vm) = createWithFamilies(plata, depto)
        assertEquals(plata, vm.content().activeFamily)

        vm.moveFamily("f-2", 0)
        advanceUntilIdle()

        assertEquals(listOf("f-2", "f-1"), vm.order())
        assertEquals(plata, vm.content().activeFamily)
        assertEquals(1, management(repo).reorders.size)
    }

    @Test
    fun reordenar_laPrimeraEsLaFavoritaDeUnViewModelNuevo() = runTest {
        val (repo, first) = createWithFamilies(plata, depto)
        first.moveFamily("f-2", 0)
        advanceUntilIdle()
        // El backend ya tiene el orden nuevo: el siguiente arranque lo recibe en el refresco inicial.
        repo.onRefresh = { it.data.value = families(depto, plata); HeaderRefreshResult() }

        val second = create(repo)

        assertEquals(depto, second.content().activeFamily)
    }

    @Test
    fun reordenar_aLaMismaPosicion_conUnaFamiliaDesconocida_oConUnaSola_noHaceNada() = runTest {
        val (repo, vm) = createWithFamilies(plata, depto)

        vm.moveFamily("f-1", 0)
        vm.moveFamily("f-9", 0)
        vm.moveFamily("f-2", 5)
        advanceUntilIdle()
        assertEquals(emptyList<List<String>>(), management(repo).reorders)

        val (solo, one) = createWithFamilies(plata)
        one.moveFamily("f-1", 0)
        advanceUntilIdle()
        assertEquals(emptyList<List<String>>(), management(solo).reorders)
    }

    @Test
    fun reordenar_elIndiceSeAjustaALosLimites() = runTest {
        val (repo, vm) = createWithFamilies(plata, depto, nueva)

        vm.moveFamily("f-1", 99)
        advanceUntilIdle()

        assertEquals(listOf(listOf("f-2", "f-3", "f-1")), management(repo).reorders)
    }

    @Test
    fun elErrorDelSelector_seLimpiaAlCerrarloYAlMoverOtra() = runTest {
        val (repo, vm) = createWithFamilies(plata, depto)
        management(repo).result = FamilyAccessResult.Failure(FamilyAccessFailure.Network)
        vm.openSelector()
        vm.moveFamily("f-2", 0)
        advanceUntilIdle()
        assertEquals(FamilyAccessFailure.Network, vm.uiState.value.selectorError)

        vm.dismissSelector()
        advanceUntilIdle()
        assertNull(vm.uiState.value.selectorError)

        vm.openSelector()
        vm.moveFamily("f-2", 0)
        advanceUntilIdle()
        management(repo).result = FamilyAccessResult.Success(Unit)
        vm.moveFamily("f-2", 0)
        advanceUntilIdle()
        assertNull(vm.uiState.value.selectorError)
    }

    @Test
    fun reordenar_siUnaFamiliaDesapareceMientrasSeGuarda_laListaMostradaNoLaResucita() = runTest {
        val (repo, vm) = createWithFamilies(plata, depto, nueva)
        management(repo).gate = CompletableDeferred()
        vm.moveFamily("f-3", 0)
        advanceUntilIdle()

        repo.data.value = families(plata, nueva)
        advanceUntilIdle()

        assertEquals(listOf("f-3", "f-1"), vm.order())
    }
}
