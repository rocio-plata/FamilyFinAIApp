package com.rocioplata.familyfinaiapp.ui.categories

import com.rocioplata.familyfinaiapp.data.network.ConnectivityObserver
import com.rocioplata.familyfinaiapp.domain.categories.CategoriesData
import com.rocioplata.familyfinaiapp.domain.categories.CategoriesRepository
import com.rocioplata.familyfinaiapp.domain.categories.Category
import com.rocioplata.familyfinaiapp.domain.categories.CategoryFailure
import com.rocioplata.familyfinaiapp.domain.categories.CategoryResult
import com.rocioplata.familyfinaiapp.domain.categories.CategoryStatus
import com.rocioplata.familyfinaiapp.domain.categories.CategoryType
import com.rocioplata.familyfinaiapp.domain.categories.ColorUpdate
import com.rocioplata.familyfinaiapp.domain.categories.IconUpdate
import com.rocioplata.familyfinaiapp.domain.categories.ReactivationResult
import com.rocioplata.familyfinaiapp.domain.categories.Tag
import com.rocioplata.familyfinaiapp.domain.categories.TagStatus
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
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class CategoriesViewModelTest {
    private class FakeRepository : CategoriesRepository {
        val data = MutableStateFlow(CategoriesData())
        val refreshed = mutableListOf<String>()
        var gate: CompletableDeferred<Unit>? = null
        var deleteGate: CompletableDeferred<Unit>? = null
        var onRefresh: (FakeRepository) -> CategoryResult<Unit> = { CategoryResult.Success(Unit) }
        val deletes = mutableListOf<String>()
        var deleteResult: CategoryResult<Unit> = CategoryResult.Success(Unit)
        val requestedFamilies = mutableListOf<String>()

        override fun categories(familyId: String): Flow<CategoriesData> {
            requestedFamilies += familyId
            return data
        }

        override suspend fun refresh(familyId: String): CategoryResult<Unit> {
            refreshed += familyId
            gate?.await()
            return onRefresh(this)
        }

        override suspend fun clear() = Unit
    override suspend fun retainFamilies(familyIds: Set<String>) = Unit
        override suspend fun createCategory(familyId: String, type: CategoryType, name: String, icon: String?, color: String?) = unused()
        override suspend fun updateCategory(familyId: String, categoryId: String, newName: String?, icon: IconUpdate, color: ColorUpdate) =
            unused()

        override suspend fun deprecateCategory(familyId: String, categoryId: String) = unused()
        override suspend fun deleteCategory(familyId: String, categoryId: String): CategoryResult<Unit> {
            deletes += categoryId
            deleteGate?.await()
            if (deleteResult is CategoryResult.Success) {
                data.value = CategoriesData(data.value.categories?.filterNot { it.id == categoryId }, data.value.isStale)
            }
            return deleteResult
        }

        override suspend fun addTag(familyId: String, categoryId: String, name: String) = unused()
        override suspend fun renameTag(familyId: String, categoryId: String, tagId: String, name: String) = unused()
        override suspend fun reorderTags(familyId: String, categoryId: String, activeTagIdsInOrder: List<String>) =
            unused()

        override suspend fun deprecateTag(familyId: String, categoryId: String, tagId: String) = unused()
        override suspend fun deleteTag(familyId: String, categoryId: String, tagId: String) = unused()

        val reactivations = mutableListOf<String>()
        var reactivateGate: CompletableDeferred<Unit>? = null
        var reactivateResult: ReactivationResult = ReactivationResult.Success

        override suspend fun reactivateCategory(familyId: String, categoryId: String): ReactivationResult {
            reactivations += categoryId
            reactivateGate?.await()
            if (reactivateResult !is ReactivationResult.Failure) {
                // Como el repositorio real: activa y, si se pudo, al final de las activas de su tipo.
                val list = data.value.categories.orEmpty()
                val target = list.first { it.id == categoryId }.copy(status = CategoryStatus.ACTIVE, deprecatedAt = null)
                val updated = if (reactivateResult == ReactivationResult.Success) {
                    list.filterNot { it.id == categoryId } + target
                } else {
                    list.map { if (it.id == categoryId) target else it }
                }
                data.value = CategoriesData(updated, data.value.isStale)
            }
            return reactivateResult
        }
        val reorders = mutableListOf<Pair<CategoryType, List<String>>>()
        var reorderGate: CompletableDeferred<Unit>? = null
        var reorderResult: CategoryResult<Unit> = CategoryResult.Success(Unit)

        override suspend fun reorderCategories(
            familyId: String,
            type: CategoryType,
            activeCategoryIdsInOrder: List<String>,
        ): CategoryResult<Unit> {
            reorders += type to activeCategoryIdsInOrder
            reorderGate?.await()
            if (reorderResult is CategoryResult.Success) {
                // Como el repositorio real: las enviadas primero dentro de su tipo y el resto después.
                val list = data.value.categories.orEmpty()
                val byId = list.associateBy { it.id }
                val sent = activeCategoryIdsInOrder.mapNotNull { byId[it] }
                val ordered = sent + list.filter { it.type == type && it !in sent } + list.filter { it.type != type }
                data.value = CategoriesData(ordered, data.value.isStale)
            }
            return reorderResult
        }

        override suspend fun reactivateTag(familyId: String, categoryId: String, tagId: String) = unused()
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

    private fun tag(id: String, name: String, order: Int, status: TagStatus = TagStatus.ACTIVE) =
        Tag(id, name, status, order)

    private fun category(
        id: String,
        name: String,
        type: CategoryType = CategoryType.EXPENSE,
        status: CategoryStatus = CategoryStatus.ACTIVE,
        icon: String? = null,
        vararg tags: Tag,
    ) = Category(id, type, name, icon, status, tags.toList())

    private val comida = category(
        "c-1", "Alimentación", icon = "shopping_cart",
        tags = arrayOf(tag("t-2", "Feria", 1), tag("t-1", "Súper", 0), tag("t-3", "Viejo", 2, TagStatus.DEPRECATED)),
    )
    private val casa = category("c-2", "Casa y hogar", tags = arrayOf(tag("t-4", "Arriendo", 0)))
    private val gimnasio = category(
        "c-3", "Gimnasio", status = CategoryStatus.DEPRECATED,
        tags = arrayOf(tag("t-5", "Pesas", 0), tag("t-6", "Yoga", 1, TagStatus.DEPRECATED)),
    )
    private val sueldo = category("c-4", "Sueldo", type = CategoryType.INCOME, tags = arrayOf(tag("t-7", "Mensual", 0)))
    private val bono = category("c-5", "Bono", type = CategoryType.INCOME, status = CategoryStatus.DEPRECATED)
    private val all = listOf(comida, casa, gimnasio, sueldo, bono)

    private var roleCalls = 0

    private fun TestScope.create(
        repo: FakeRepository,
        connectivity: FakeConnectivity = FakeConnectivity(),
        familyId: String = "f-1",
    ): CategoriesViewModel {
        roleCalls = 0
        val vm = CategoriesViewModel(repo, familyId, connectivity) { roleCalls++ }
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        return vm
    }

    private fun CategoriesViewModel.content() = uiState.value as CategoriesUiState.Content
    private fun repoWith(categories: List<Category>? = all, stale: Boolean = false) =
        FakeRepository().apply { data.value = CategoriesData(categories, stale) }

    private fun CategoryItem.id() = category.id

    // --- Estados ---

    @Test
    fun alAbrirSinDatos_muestraCargando_yPideLaFamiliaCorrecta() = runTest {
        val repo = FakeRepository().apply { gate = CompletableDeferred() }

        val vm = create(repo, familyId = "f-7")

        assertEquals(CategoriesUiState.Loading, vm.uiState.value)
        assertEquals(listOf("f-7"), repo.refreshed)
        assertEquals(listOf("f-7"), repo.requestedFamilies)
    }

    @Test
    fun sinDatosYRefrescoFallido_muestraError_yReintentarCarga() = runTest {
        val repo = FakeRepository().apply { onRefresh = { CategoryResult.Failure(CategoryFailure.Network) } }
        val vm = create(repo)
        assertEquals(CategoriesUiState.Error, vm.uiState.value)

        repo.onRefresh = { it.data.value = CategoriesData(all); CategoryResult.Success(Unit) }
        vm.retry()
        advanceUntilIdle()

        assertEquals(2, repo.refreshed.size)
        assertTrue(vm.uiState.value is CategoriesUiState.Content)
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
        val repo = repoWith().apply { onRefresh = { CategoryResult.Failure(CategoryFailure.Network) } }

        val vm = create(repo)

        assertTrue(vm.uiState.value is CategoriesUiState.Content)
        assertEquals(listOf("c-1", "c-2"), vm.content().active.map { it.id() })
    }

    @Test
    fun datosAntiguos_seMarcan() = runTest {
        assertTrue(create(repoWith(stale = true)).content().isStale)
        assertFalse(create(repoWith(stale = false)).content().isStale)
    }

    // --- Pestañas y contadores (CA-02) ---

    @Test
    fun alAbrir_muestraGastosConSusCategoriasYContadores() = runTest {
        val vm = create(repoWith())

        val content = vm.content()
        assertEquals(CategoryType.EXPENSE, content.selectedType)
        assertEquals(listOf("c-1", "c-2"), content.active.map { it.id() })
        assertEquals(2, content.expenseActiveCount)
        assertEquals(1, content.incomeActiveCount)
        assertEquals(2, content.selectedActiveCount)
    }

    @Test
    fun alTocarIngresos_muestraLasDeIngresoYSuContador() = runTest {
        val vm = create(repoWith())

        vm.selectType(CategoryType.INCOME)
        advanceUntilIdle()

        assertEquals(CategoryType.INCOME, vm.content().selectedType)
        assertEquals(listOf("c-4"), vm.content().active.map { it.id() })
        assertEquals(1, vm.content().selectedActiveCount)
        assertEquals(2, vm.content().expenseActiveCount)
    }

    @Test
    fun loContadoSonSoloLasActivas_noLasArchivadas() = runTest {
        val vm = create(repoWith(listOf(casa, gimnasio, bono)))

        assertEquals(1, vm.content().expenseActiveCount)
        assertEquals(0, vm.content().incomeActiveCount)
    }

    @Test
    fun conservaElOrdenDelBackend() = runTest {
        val vm = create(repoWith(listOf(casa, comida)))

        assertEquals(listOf("c-2", "c-1"), vm.content().active.map { it.id() })
    }

    // --- Filas (CA-03) ---

    @Test
    fun cadaFilaTrae_soloLosTagsActivos_porSuPosicion() = runTest {
        val vm = create(repoWith())

        val item = vm.content().active.first { it.id() == "c-1" }

        assertEquals(listOf("Súper", "Feria"), item.activeTags.map { it.name })
        assertEquals("shopping_cart", item.category.icon)
    }

    // --- Archivadas (CA-04) ---

    @Test
    fun archivadas_seSeparanPorTipo_yLaSeccionEmpiezaPlegada() = runTest {
        val vm = create(repoWith())

        assertEquals(listOf("c-3"), vm.content().archived.map { it.id() })
        assertFalse(vm.content().archivedExpanded)

        vm.toggleArchived()
        advanceUntilIdle()
        assertTrue(vm.content().archivedExpanded)

        vm.selectType(CategoryType.INCOME)
        advanceUntilIdle()
        assertEquals(listOf("c-5"), vm.content().archived.map { it.id() })
        assertTrue(vm.content().archivedExpanded)

        vm.toggleArchived()
        advanceUntilIdle()
        assertFalse(vm.content().archivedExpanded)
    }

    @Test
    fun sinArchivadasEnElTipo_laListaVieneVacia() = runTest {
        val vm = create(repoWith(listOf(casa, sueldo)))

        assertTrue(vm.content().archived.isEmpty())
    }

    // --- Vacío (CA-02/spec) ---

    @Test
    fun tipoSinCategorias_esVacio_noSinResultados() = runTest {
        val vm = create(repoWith(listOf(comida, casa)))

        vm.selectType(CategoryType.INCOME)
        advanceUntilIdle()

        assertTrue(vm.content().isEmpty)
        assertFalse(vm.content().noResults)
        assertEquals(0, vm.content().selectedActiveCount)
    }

    @Test
    fun listaTotalmenteVacia_esVacioEnAmbosTipos() = runTest {
        val vm = create(repoWith(emptyList()))

        assertTrue(vm.content().isEmpty)
        vm.selectType(CategoryType.INCOME)
        advanceUntilIdle()
        assertTrue(vm.content().isEmpty)
    }

    @Test
    fun tipoVacioConBusqueda_siguesiendoVacio_noSinResultados() = runTest {
        val vm = create(repoWith(listOf(comida)))
        vm.selectType(CategoryType.INCOME)
        vm.setQuery("algo")
        advanceUntilIdle()

        assertTrue(vm.content().isEmpty)
        assertFalse(vm.content().noResults)
    }

    // --- Búsqueda (CA-05) ---

    @Test
    fun buscarPorNombreDeCategoria_sinMayusculasNiAcentos() = runTest {
        val vm = create(repoWith())

        vm.setQuery("ALIMENTACION")
        advanceUntilIdle()
        assertEquals(listOf("c-1"), vm.content().active.map { it.id() })

        vm.setQuery("alimentación")
        advanceUntilIdle()
        assertEquals(listOf("c-1"), vm.content().active.map { it.id() })

        vm.setQuery("hogar")
        advanceUntilIdle()
        assertEquals(listOf("c-2"), vm.content().active.map { it.id() })
    }

    @Test
    fun buscarPorTag_dejaLaCategoriaQueLoTiene() = runTest {
        val vm = create(repoWith())

        vm.setQuery("super")
        advanceUntilIdle()

        assertEquals(listOf("c-1"), vm.content().active.map { it.id() })
    }

    @Test
    fun noSeBuscaEnLosTagsArchivadosDeUnaCategoriaActiva() = runTest {
        val vm = create(repoWith())

        vm.setQuery("viejo")
        advanceUntilIdle()

        assertTrue(vm.content().noResults)
        assertTrue(vm.content().active.isEmpty())
    }

    @Test
    fun enUnaArchivada_seBuscaEnTodosSusTags() = runTest {
        val vm = create(repoWith())

        vm.setQuery("yoga")
        advanceUntilIdle()

        assertEquals(listOf("c-3"), vm.content().archived.map { it.id() })
        assertTrue(vm.content().active.isEmpty())
        assertFalse(vm.content().noResults)
    }

    @Test
    fun laBusquedaAplicaATodasLasArchivadasDelTipo() = runTest {
        val vm = create(repoWith())

        vm.setQuery("gim")
        advanceUntilIdle()

        assertEquals(listOf("c-3"), vm.content().archived.map { it.id() })
        vm.setQuery("casa")
        advanceUntilIdle()
        assertTrue(vm.content().archived.isEmpty())
    }

    @Test
    fun sinCoincidencias_esSinResultados() = runTest {
        val vm = create(repoWith())

        vm.setQuery("zzz")
        advanceUntilIdle()

        assertTrue(vm.content().noResults)
        assertFalse(vm.content().isEmpty)
        assertTrue(vm.content().active.isEmpty() && vm.content().archived.isEmpty())
    }

    @Test
    fun busquedaVaciaOSoloEspacios_noFiltra_yElTextoSeConserva() = runTest {
        val vm = create(repoWith())

        vm.setQuery("   ")
        advanceUntilIdle()

        assertEquals(listOf("c-1", "c-2"), vm.content().active.map { it.id() })
        assertEquals("   ", vm.content().query)
        assertFalse(vm.content().noResults)
    }

    @Test
    fun laBusquedaSeRecortaYNoAfectaALosContadores() = runTest {
        val vm = create(repoWith())

        vm.setQuery("  casa ")
        advanceUntilIdle()

        assertEquals(listOf("c-2"), vm.content().active.map { it.id() })
        assertEquals(2, vm.content().expenseActiveCount)
        assertEquals(1, vm.content().incomeActiveCount)
    }

    @Test
    fun laBusquedaYLaPestanaSeConservanAlCambiarDeTipo() = runTest {
        val vm = create(repoWith())

        vm.setQuery("sue")
        vm.selectType(CategoryType.INCOME)
        advanceUntilIdle()

        assertEquals("sue", vm.content().query)
        assertEquals(listOf("c-4"), vm.content().active.map { it.id() })
    }

    // --- Refresco ---

    @Test
    fun alIniciar_refrescaUnaVez() = runTest {
        val repo = repoWith()

        create(repo)

        assertEquals(1, repo.refreshed.size)
    }

    @Test
    fun alVolverAPrimerPlano_refresca_perosiHayUnoEnCurso_seIgnora() = runTest {
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

    @Test
    fun losDatosNuevosDelRepositorio_seReflejan() = runTest {
        val repo = repoWith(listOf(casa))
        val vm = create(repo)

        repo.data.value = CategoriesData(listOf(casa, comida))
        advanceUntilIdle()

        assertEquals(listOf("c-2", "c-1"), vm.content().active.map { it.id() })
        assertEquals(2, vm.content().expenseActiveCount)
    }

    // --- Eliminar una archivada (T-21; RF-13; CA-12, CA-20) ---

    @Test
    fun eliminarArchivada_pideEliminarla_yDesapareceDelListado() = runTest {
        val repo = repoWith()
        val vm = create(repo)

        vm.deleteArchived("c-3")
        advanceUntilIdle()

        assertEquals(listOf("c-3"), repo.deletes)
        assertTrue(vm.content().archived.isEmpty())
        assertNull(vm.content().event)
        assertFalse(vm.content().isBusy)
    }

    @Test
    fun eliminarArchivada_conMovimientosAsociados_soloInforma() = runTest {
        val repo = repoWith().apply { deleteResult = CategoryResult.Failure(CategoryFailure.HasAssociatedItems) }
        val vm = create(repo)

        vm.deleteArchived("c-3")
        advanceUntilIdle()

        assertEquals(CategoriesEvent.CannotDelete, vm.content().event)
        assertEquals(listOf("c-3"), vm.content().archived.map { it.id() })
    }

    @Test
    fun eliminar_soloSePuedeUnaCategoriaArchivada() = runTest {
        val repo = repoWith()
        val vm = create(repo)

        vm.deleteArchived("c-1")
        vm.deleteArchived("c-404")
        advanceUntilIdle()

        assertTrue(repo.deletes.isEmpty())
    }

    @Test
    fun eliminarArchivada_sinConexionOConError_avisaYNoCambiaNada() = runTest {
        listOf(CategoryFailure.Offline, CategoryFailure.Network, CategoryFailure.Unknown).forEach { failure ->
            val repo = repoWith().apply { deleteResult = CategoryResult.Failure(failure) }
            val vm = create(repo)

            vm.deleteArchived("c-3")
            advanceUntilIdle()

            assertEquals(CategoriesEvent.Failed(failure), vm.content().event)
            assertEquals(listOf("c-3"), vm.content().archived.map { it.id() })
            assertFalse(vm.content().isBusy)
        }
    }

    @Test
    fun eliminarArchivada_rolInsuficiente_avisaYNotificaAlHeader() = runTest {
        val repo = repoWith().apply { deleteResult = CategoryResult.Failure(CategoryFailure.InsufficientRole) }
        val vm = create(repo)

        vm.deleteArchived("c-3")
        advanceUntilIdle()

        assertEquals(CategoriesEvent.Failed(CategoryFailure.InsufficientRole), vm.content().event)
        assertEquals(1, roleCalls)
    }

    @Test
    fun eliminarArchivada_queYaNoExiste_avisaYActualizaElListado() = runTest {
        val repo = repoWith().apply { deleteResult = CategoryResult.Failure(CategoryFailure.NotFound) }
        val vm = create(repo)
        val refreshesBefore = repo.refreshed.size

        vm.deleteArchived("c-3")
        advanceUntilIdle()

        assertEquals(CategoriesEvent.Failed(CategoryFailure.NotFound), vm.content().event)
        assertEquals(refreshesBefore + 1, repo.refreshed.size)
    }

    @Test
    fun eliminarArchivada_noAutorizado_loResuelveLaSesion_sinMensaje() = runTest {
        val repo = repoWith().apply { deleteResult = CategoryResult.Failure(CategoryFailure.Unauthorized) }
        val vm = create(repo)

        vm.deleteArchived("c-3")
        advanceUntilIdle()

        assertNull(vm.content().event)
        assertFalse(vm.content().isBusy)
    }

    @Test
    fun eliminarArchivada_dosPulsaciones_unaSolaLlamada_yMientrasTantoEstaOcupado() = runTest {
        val repo = repoWith().apply { deleteGate = CompletableDeferred() }
        val vm = create(repo)

        vm.deleteArchived("c-3")
        vm.deleteArchived("c-3")
        advanceUntilIdle()
        assertTrue(vm.content().isBusy)
        repo.deleteGate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, repo.deletes.size)
        assertFalse(vm.content().isBusy)
    }

    @Test
    fun elEventoSeConsumeUnaVez() = runTest {
        val repo = repoWith().apply { deleteResult = CategoryResult.Failure(CategoryFailure.HasAssociatedItems) }
        val vm = create(repo)
        vm.deleteArchived("c-3")
        advanceUntilIdle()
        assertEquals(CategoriesEvent.CannotDelete, vm.content().event)

        vm.consumeEvent()
        advanceUntilIdle()

        assertNull(vm.content().event)
    }

    // --- Reactivar y fecha de archivado (T-08 de la 010; RF-01, RF-06, RF-07, RF-09, RF-11) ---

    @Test
    fun reactivar_pasaALasActivasAlFinal_actualizaContadoresYAvisa() = runTest {
        val repo = repoWith()
        val vm = create(repo)

        vm.reactivate("c-3")
        advanceUntilIdle()

        assertEquals(listOf("c-3"), repo.reactivations)
        assertEquals(listOf("c-1", "c-2", "c-3"), vm.content().active.map { it.id() })
        assertTrue(vm.content().archived.isEmpty())
        assertEquals(3, vm.content().expenseActiveCount)
        assertEquals(CategoriesEvent.Reactivated, vm.content().event)
        assertFalse(vm.content().isBusy)
    }

    @Test
    fun reactivar_sinPoderMoverlaAlFinal_avisaYActualizaLaLista() = runTest {
        val repo = repoWith().apply { reactivateResult = ReactivationResult.Partial(CategoryFailure.Network) }
        val vm = create(repo)
        val refreshesBefore = repo.refreshed.size

        vm.reactivate("c-3")
        advanceUntilIdle()

        assertEquals(CategoriesEvent.ReactivatedNotMoved, vm.content().event)
        assertEquals(refreshesBefore + 1, repo.refreshed.size)
        assertTrue(vm.content().archived.isEmpty())
        assertEquals(0, roleCalls)
    }

    @Test
    fun reactivar_parcialPorRol_notificaAlHeader() = runTest {
        val repo = repoWith().apply { reactivateResult = ReactivationResult.Partial(CategoryFailure.InsufficientRole) }
        val vm = create(repo)

        vm.reactivate("c-3")
        advanceUntilIdle()

        assertEquals(CategoriesEvent.ReactivatedNotMoved, vm.content().event)
        assertEquals(1, roleCalls)
    }

    @Test
    fun reactivar_soloUnaCategoriaArchivada() = runTest {
        val repo = repoWith()
        val vm = create(repo)

        vm.reactivate("c-1")
        vm.reactivate("c-404")
        advanceUntilIdle()

        assertTrue(repo.reactivations.isEmpty())
    }

    @Test
    fun reactivar_sinConexionOConError_avisaYNoCambiaNada() = runTest {
        listOf(CategoryFailure.Offline, CategoryFailure.Network, CategoryFailure.Unknown).forEach { failure ->
            val repo = repoWith().apply { reactivateResult = ReactivationResult.Failure(failure) }
            val vm = create(repo)

            vm.reactivate("c-3")
            advanceUntilIdle()

            assertEquals(CategoriesEvent.Failed(failure), vm.content().event)
            assertEquals(listOf("c-3"), vm.content().archived.map { it.id() })
            assertFalse(vm.content().isBusy)
        }
    }

    @Test
    fun reactivar_queYaNoExiste_avisaYActualiza_yPorRol_notificaAlHeader() = runTest {
        val gone = repoWith().apply { reactivateResult = ReactivationResult.Failure(CategoryFailure.NotFound) }
        val vm = create(gone)
        val refreshesBefore = gone.refreshed.size

        vm.reactivate("c-3")
        advanceUntilIdle()

        assertEquals(CategoriesEvent.Failed(CategoryFailure.NotFound), vm.content().event)
        assertEquals(refreshesBefore + 1, gone.refreshed.size)

        val noRole = repoWith().apply { reactivateResult = ReactivationResult.Failure(CategoryFailure.InsufficientRole) }
        val vm2 = create(noRole)
        vm2.reactivate("c-3")
        advanceUntilIdle()

        assertEquals(CategoriesEvent.Failed(CategoryFailure.InsufficientRole), vm2.content().event)
        assertEquals(1, roleCalls)
    }

    @Test
    fun reactivar_dosPulsacionesUOtraOperacion_unaSolaLlamada() = runTest {
        val repo = repoWith().apply { reactivateGate = CompletableDeferred() }
        val vm = create(repo)

        vm.reactivate("c-3")
        vm.reactivate("c-3")
        vm.deleteArchived("c-3")
        advanceUntilIdle()
        assertTrue(vm.content().isBusy)
        repo.reactivateGate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, repo.reactivations.size)
        assertTrue(repo.deletes.isEmpty())
        assertFalse(vm.content().isBusy)
    }

    @Test
    fun mesDeArchivado_soloEnArchivadasConFecha_enLaZonaDelDispositivo() = runTest {
        val repo = FakeRepository().apply {
            data.value = CategoriesData(
                listOf(
                    comida.copy(deprecatedAt = Instant.parse("2026-09-01T02:00:00Z")),
                    gimnasio.copy(deprecatedAt = Instant.parse("2026-09-01T02:00:00Z")),
                    category("c-6", "Viaje", status = CategoryStatus.DEPRECATED),
                ),
            )
        }
        roleCalls = 0
        val vm = CategoriesViewModel(repo, "f-1", FakeConnectivity(), ZoneId.of("America/Santiago")) { roleCalls++ }
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertNull(vm.content().active.single().archivedAt)
        // A las 02:00 UTC del 1 de septiembre en Santiago todavía es agosto.
        assertEquals(listOf(YearMonth.of(2026, 8), null), vm.content().archived.map { it.archivedAt })
    }

    // --- Modo de orden (T-10 de la 010; RF-06, RF-10, RF-12; CA-14 a CA-16, CA-18) ---

    private val ocio = category("c-6", "Ocio")
    private val threeActive = listOf(comida, casa, gimnasio, ocio, sueldo, bono)

    private fun TestScope.reordering(repo: FakeRepository = repoWith(threeActive)): CategoriesViewModel {
        val vm = create(repo)
        vm.startReorder()
        advanceUntilIdle()
        return vm
    }

    @Test
    fun reordenar_abreYCierraElModo_conAlMenosDosActivas() = runTest {
        val vm = create(repoWith(threeActive))
        assertTrue(vm.content().canReorder)
        assertFalse(vm.content().reorderMode)

        vm.startReorder()
        advanceUntilIdle()
        assertTrue(vm.content().reorderMode)

        vm.finishReorder()
        advanceUntilIdle()
        assertFalse(vm.content().reorderMode)
    }

    @Test
    fun reordenar_noSeAbreConUnaSolaActivaNiConBusqueda() = runTest {
        val vm = create(repoWith(threeActive))
        vm.selectType(CategoryType.INCOME)
        advanceUntilIdle()
        assertFalse(vm.content().canReorder)
        vm.startReorder()
        advanceUntilIdle()
        assertFalse(vm.content().reorderMode)

        vm.selectType(CategoryType.EXPENSE)
        vm.setQuery("a")
        advanceUntilIdle()
        assertFalse(vm.content().canReorder)
        vm.startReorder()
        advanceUntilIdle()
        assertFalse(vm.content().reorderMode)
    }

    @Test
    fun escribirEnElBuscador_cierraElModo_yBorrarloVuelveAPermitirlo() = runTest {
        val vm = reordering()

        vm.setQuery("ocio")
        advanceUntilIdle()
        assertFalse(vm.content().reorderMode)
        assertFalse(vm.content().canReorder)

        vm.setQuery("")
        advanceUntilIdle()
        assertTrue(vm.content().canReorder)
    }

    @Test
    fun mover_muestraElOrdenAlInstante_yLoGuarda() = runTest {
        val repo = repoWith(threeActive).apply { reorderGate = CompletableDeferred() }
        val vm = reordering(repo)

        vm.moveCategory("c-6", 0)
        advanceUntilIdle()
        assertEquals(listOf("c-6", "c-1", "c-2"), vm.content().active.map { it.id() })
        assertTrue(vm.content().isBusy)
        assertFalse(vm.content().canReorder)

        repo.reorderGate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf(CategoryType.EXPENSE to listOf("c-6", "c-1", "c-2")), repo.reorders)
        assertEquals(listOf("c-6", "c-1", "c-2"), vm.content().active.map { it.id() })
        assertEquals(listOf("c-3"), vm.content().archived.map { it.id() })
        assertFalse(vm.content().isBusy)
        assertTrue(vm.content().reorderMode)
        assertNull(vm.content().event)
    }

    @Test
    fun subirYBajar_muevenUnaPosicion_yEnLosExtremosNoHacenNada() = runTest {
        val repo = repoWith(threeActive)
        val vm = reordering(repo)

        vm.moveCategoryUp("c-1")
        vm.moveCategoryDown("c-6")
        advanceUntilIdle()
        assertTrue(repo.reorders.isEmpty())

        vm.moveCategoryDown("c-1")
        advanceUntilIdle()
        vm.moveCategoryUp("c-6")
        advanceUntilIdle()

        assertEquals(
            listOf(
                CategoryType.EXPENSE to listOf("c-2", "c-1", "c-6"),
                CategoryType.EXPENSE to listOf("c-2", "c-6", "c-1"),
            ),
            repo.reorders,
        )
        assertEquals(listOf("c-2", "c-6", "c-1"), vm.content().active.map { it.id() })
    }

    @Test
    fun mover_fueraDelModoOMientrasHayOtraOperacion_seIgnora() = runTest {
        val repo = repoWith(threeActive).apply { reorderGate = CompletableDeferred() }
        val vm = create(repo)

        vm.moveCategory("c-6", 0)
        advanceUntilIdle()
        assertTrue(repo.reorders.isEmpty())

        vm.startReorder()
        vm.moveCategory("c-6", 0)
        vm.moveCategory("c-2", 0)
        vm.reactivate("c-3")
        advanceUntilIdle()
        repo.reorderGate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, repo.reorders.size)
        assertTrue(repo.reactivations.isEmpty())
    }

    @Test
    fun mover_rechazado_restauraElOrden_avisaYActualiza() = runTest {
        val repo = repoWith(threeActive).apply { reorderResult = CategoryResult.Failure(CategoryFailure.Unknown) }
        val vm = reordering(repo)
        val refreshesBefore = repo.refreshed.size

        vm.moveCategory("c-6", 0)
        advanceUntilIdle()

        assertEquals(listOf("c-1", "c-2", "c-6"), vm.content().active.map { it.id() })
        assertEquals(CategoriesEvent.ReorderFailed, vm.content().event)
        assertEquals(refreshesBefore + 1, repo.refreshed.size)
        assertFalse(vm.content().isBusy)
    }

    @Test
    fun mover_sinConexion_avisaYConservaElOrdenAnterior_sinRefrescar() = runTest {
        val repo = repoWith(threeActive).apply { reorderResult = CategoryResult.Failure(CategoryFailure.Offline) }
        val vm = reordering(repo)
        val refreshesBefore = repo.refreshed.size

        vm.moveCategory("c-6", 0)
        advanceUntilIdle()

        assertEquals(listOf("c-1", "c-2", "c-6"), vm.content().active.map { it.id() })
        assertEquals(CategoriesEvent.Failed(CategoryFailure.Offline), vm.content().event)
        assertEquals(refreshesBefore, repo.refreshed.size)
    }

    @Test
    fun mover_unaQueYaNoExiste_avisaYActualiza_yPorRol_notificaAlHeader() = runTest {
        val gone = repoWith(threeActive).apply { reorderResult = CategoryResult.Failure(CategoryFailure.NotFound) }
        val vm = reordering(gone)
        val refreshesBefore = gone.refreshed.size

        vm.moveCategory("c-6", 0)
        advanceUntilIdle()

        assertEquals(CategoriesEvent.Failed(CategoryFailure.NotFound), vm.content().event)
        assertEquals(refreshesBefore + 1, gone.refreshed.size)
        assertEquals(listOf("c-1", "c-2", "c-6"), vm.content().active.map { it.id() })

        val noRole = repoWith(threeActive).apply { reorderResult = CategoryResult.Failure(CategoryFailure.InsufficientRole) }
        val vm2 = reordering(noRole)
        vm2.moveCategory("c-6", 0)
        advanceUntilIdle()

        assertEquals(CategoriesEvent.Failed(CategoryFailure.InsufficientRole), vm2.content().event)
        assertEquals(1, roleCalls)
    }
}
