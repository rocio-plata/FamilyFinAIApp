package com.rocioplata.familyfinaiapp.ui.categories

import com.rocioplata.familyfinaiapp.domain.categories.CategoriesData
import com.rocioplata.familyfinaiapp.domain.categories.CategoriesRepository
import com.rocioplata.familyfinaiapp.domain.categories.Category
import com.rocioplata.familyfinaiapp.domain.categories.CategoryFailure
import com.rocioplata.familyfinaiapp.domain.categories.CategoryResult
import com.rocioplata.familyfinaiapp.domain.categories.CategoryStatus
import com.rocioplata.familyfinaiapp.domain.categories.CategoryType
import com.rocioplata.familyfinaiapp.domain.categories.IconUpdate
import com.rocioplata.familyfinaiapp.domain.categories.Tag
import com.rocioplata.familyfinaiapp.domain.categories.TagStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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
class CategoryEditViewModelTest {
    private data class Create(val type: CategoryType, val name: String, val icon: String?)
    private data class Update(val id: String, val newName: String?, val icon: IconUpdate)

    private class FakeRepository : CategoriesRepository {
        val data = MutableStateFlow(CategoriesData())
        val creates = mutableListOf<Create>()
        val updates = mutableListOf<Update>()
        var refreshes = 0
        var gate: CompletableDeferred<Unit>? = null
        var createResult: (Create) -> CategoryResult<Category> = {
            CategoryResult.Success(Category("c-new", it.type, it.name, it.icon, CategoryStatus.ACTIVE, emptyList()))
        }
        var updateResult: CategoryResult<Unit> = CategoryResult.Success(Unit)

        override fun categories(familyId: String): Flow<CategoriesData> = data

        override suspend fun refresh(familyId: String): CategoryResult<Unit> {
            refreshes++
            return CategoryResult.Success(Unit)
        }

        override suspend fun createCategory(familyId: String, type: CategoryType, name: String, icon: String?): CategoryResult<Category> {
            val request = Create(type, name, icon)
            creates += request
            gate?.await()
            return createResult(request)
        }

        override suspend fun updateCategory(familyId: String, categoryId: String, newName: String?, icon: IconUpdate): CategoryResult<Unit> {
            updates += Update(categoryId, newName, icon)
            gate?.await()
            val result = updateResult
            if (result is CategoryResult.Success) {
                data.value = CategoriesData(
                    data.value.categories?.map { category ->
                        if (category.id != categoryId) {
                            category
                        } else {
                            category.copy(
                                name = newName ?: category.name,
                                icon = when (icon) {
                                    IconUpdate.Unchanged -> category.icon
                                    IconUpdate.Remove -> null
                                    is IconUpdate.Set -> icon.key
                                },
                            )
                        }
                    },
                )
            }
            return result
        }

        override suspend fun clear() = Unit
        override suspend fun deprecateCategory(familyId: String, categoryId: String) = unused()
        override suspend fun deleteCategory(familyId: String, categoryId: String) = unused()
        override suspend fun addTag(familyId: String, categoryId: String, name: String) = unused()
        override suspend fun renameTag(familyId: String, categoryId: String, tagId: String, name: String) = unused()
        override suspend fun reorderTags(familyId: String, categoryId: String, activeTagIdsInOrder: List<String>) = unused()
        override suspend fun deprecateTag(familyId: String, categoryId: String, tagId: String) = unused()
        override suspend fun deleteTag(familyId: String, categoryId: String, tagId: String) = unused()
        private fun unused(): Nothing = error("Fuera del alcance de T-19")
    }

    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val comida = Category(
        "c-1", CategoryType.EXPENSE, "Alimentación", "shopping_cart", CategoryStatus.ACTIVE,
        listOf(
            Tag("t-3", "Viejo", TagStatus.DEPRECATED, 2),
            Tag("t-2", "Feria", TagStatus.ACTIVE, 1),
            Tag("t-1", "Súper", TagStatus.ACTIVE, 0),
        ),
    )
    private val gimnasio = Category("c-2", CategoryType.EXPENSE, "Gimnasio", "fitness_center", CategoryStatus.DEPRECATED, emptyList())

    private var roleCalls = 0

    private fun repoWith(vararg categories: Category) = FakeRepository().apply {
        data.value = CategoriesData(categories.toList())
    }

    private fun TestScope.create(
        repo: FakeRepository,
        categoryId: String? = null,
        type: CategoryType = CategoryType.EXPENSE,
        canManage: Boolean = true,
    ): CategoryEditViewModel {
        roleCalls = 0
        val vm = CategoryEditViewModel(repo, "f-1", categoryId, type, canManage) { roleCalls++ }
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        return vm
    }

    private fun CategoryEditViewModel.form() = uiState.value as CategoryEditUiState.Form

    private fun TestScope.type(vm: CategoryEditViewModel, text: String) {
        vm.setName(text)
        advanceUntilIdle()
    }

    private fun TestScope.submit(vm: CategoryEditViewModel) {
        vm.submit()
        advanceUntilIdle()
    }

    // --- Alta (RF-09, CA-08) ---

    @Test
    fun alta_empiezaVacia_conElTipoPreseleccionado_yElTipoSeEligePorElUsuario() = runTest {
        val vm = create(repoWith(comida), type = CategoryType.INCOME)

        assertTrue(vm.form().isCreate)
        assertEquals(CategoryType.INCOME, vm.form().type)
        assertTrue(vm.form().typeEditable)
        assertEquals("", vm.form().name)
        assertNull(vm.form().icon)
        assertTrue(vm.form().tags.isEmpty())
        assertFalse(vm.form().hasUnsavedChanges)

        vm.setType(CategoryType.EXPENSE)
        advanceUntilIdle()
        assertEquals(CategoryType.EXPENSE, vm.form().type)
    }

    @Test
    fun alta_conNombreValido_creaConElNombreRecortadoYAvisaQueSeCreo() = runTest {
        val repo = repoWith()
        val vm = create(repo, type = CategoryType.INCOME)
        type(vm, "  Sueldo  ")
        vm.setIcon("home")

        submit(vm)

        assertEquals(listOf(Create(CategoryType.INCOME, "Sueldo", "home")), repo.creates)
        assertEquals(CategoryEditEvent.Created("c-new"), vm.form().event)
        assertFalse(vm.form().isBusy)
    }

    @Test
    fun alta_sinIcono_envíaIconoNulo() = runTest {
        val repo = repoWith()
        val vm = create(repo)
        type(vm, "Casa")

        submit(vm)

        assertEquals(listOf(Create(CategoryType.EXPENSE, "Casa", null)), repo.creates)
    }

    @Test
    fun alta_conNombreVacioODeEspacios_noSeEnvia_yMuestraElError() = runTest {
        val repo = repoWith()
        val vm = create(repo)

        submit(vm)
        assertEquals(NameFieldError.REQUIRED, vm.form().nameError)

        type(vm, "     ")
        submit(vm)
        assertEquals(NameFieldError.REQUIRED, vm.form().nameError)
        assertTrue(repo.creates.isEmpty())
    }

    @Test
    fun elErrorDelNombreDesapareceAlEscribir() = runTest {
        val vm = create(repoWith())
        submit(vm)
        assertEquals(NameFieldError.REQUIRED, vm.form().nameError)

        type(vm, "C")

        assertNull(vm.form().nameError)
    }

    @Test
    fun noSePuedeEscribirMasAllaDelLimiteDe50() = runTest {
        val vm = create(repoWith())

        type(vm, "a".repeat(60))

        assertEquals(50, vm.form().name.length)
    }

    @Test
    fun alta_conNombreDuplicado_muestraElErrorJuntoAlCampo_yConservaLoEscrito() = runTest {
        val repo = repoWith().apply { createResult = { CategoryResult.Failure(CategoryFailure.DuplicateName) } }
        val vm = create(repo)
        type(vm, "Comida")
        vm.setIcon("pets")

        submit(vm)

        assertEquals(NameFieldError.DUPLICATE, vm.form().nameError)
        assertEquals("Comida", vm.form().name)
        assertEquals("pets", vm.form().icon)
        assertNull(vm.form().event)
    }

    @Test
    fun dosPulsacionesSeguidas_envianUnaSolaCreacion() = runTest {
        val repo = repoWith().apply { gate = CompletableDeferred() }
        val vm = create(repo)
        type(vm, "Casa")

        vm.submit()
        vm.submit()
        advanceUntilIdle()
        assertTrue(vm.form().isBusy)
        assertFalse(vm.form().canSubmit)
        repo.gate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, repo.creates.size)
        assertFalse(vm.form().isBusy)
    }

    @Test
    fun sinConexion_avisa_yConservaElBorrador_yPuedeReintentar() = runTest {
        val repo = repoWith().apply { createResult = { CategoryResult.Failure(CategoryFailure.Offline) } }
        val vm = create(repo)
        type(vm, "Casa")
        vm.setIcon("home")

        submit(vm)

        assertEquals(CategoryEditEvent.Failed(CategoryFailure.Offline), vm.form().event)
        assertEquals("Casa", vm.form().name)
        assertEquals("home", vm.form().icon)
        assertFalse(vm.form().isBusy)

        vm.consumeEvent()
        repo.createResult = { CategoryResult.Success(Category("c-9", it.type, it.name, it.icon, CategoryStatus.ACTIVE, emptyList())) }
        submit(vm)
        assertEquals(2, repo.creates.size)
        assertEquals(CategoryEditEvent.Created("c-9"), vm.form().event)
    }

    @Test
    fun otrosFallos_avisanConSuMotivo_sinPerderElBorrador() = runTest {
        listOf(CategoryFailure.Network, CategoryFailure.Unknown, CategoryFailure.InvalidData).forEach { failure ->
            val repo = repoWith().apply { createResult = { CategoryResult.Failure(failure) } }
            val vm = create(repo)
            type(vm, "Casa")

            submit(vm)

            assertEquals(CategoryEditEvent.Failed(failure), vm.form().event)
            assertEquals("Casa", vm.form().name)
        }
    }

    @Test
    fun noAutorizado_loResuelveLaSesion_sinMensaje() = runTest {
        val repo = repoWith().apply { createResult = { CategoryResult.Failure(CategoryFailure.Unauthorized) } }
        val vm = create(repo)
        type(vm, "Casa")

        submit(vm)

        assertNull(vm.form().event)
        assertFalse(vm.form().isBusy)
    }

    @Test
    fun elEventoSeConsumeUnaVez() = runTest {
        val repo = repoWith().apply { createResult = { CategoryResult.Failure(CategoryFailure.Offline) } }
        val vm = create(repo)
        type(vm, "Casa")
        submit(vm)
        assertEquals(CategoryEditEvent.Failed(CategoryFailure.Offline), vm.form().event)

        vm.consumeEvent()
        advanceUntilIdle()

        assertNull(vm.form().event)
    }

    // --- Cambios sin guardar (CA-24) ---

    @Test
    fun alta_hayCambiosConUnNombreOUnIcono_peroNoConSoloEspacios() = runTest {
        val vm = create(repoWith())
        assertFalse(vm.form().hasUnsavedChanges)

        type(vm, "   ")
        assertFalse(vm.form().hasUnsavedChanges)

        type(vm, "A")
        assertTrue(vm.form().hasUnsavedChanges)

        type(vm, "")
        assertFalse(vm.form().hasUnsavedChanges)

        vm.setIcon("pets")
        advanceUntilIdle()
        assertTrue(vm.form().hasUnsavedChanges)
    }

    @Test
    fun alta_cambiarSoloElTipo_noCuentaComoCambio() = runTest {
        val vm = create(repoWith())

        vm.setType(CategoryType.INCOME)
        advanceUntilIdle()

        assertFalse(vm.form().hasUnsavedChanges)
    }

    // --- Edición (RF-10, CA-10) ---

    @Test
    fun edicion_cargaElBorradorDeLoGuardado_conElTipoFijo_yLosTagsOrdenados() = runTest {
        val vm = create(repoWith(comida), categoryId = "c-1")

        val form = vm.form()
        assertFalse(form.isCreate)
        assertEquals("Alimentación", form.name)
        assertEquals("shopping_cart", form.icon)
        assertEquals(CategoryType.EXPENSE, form.type)
        assertFalse(form.typeEditable)
        assertFalse(form.readOnly)
        assertFalse(form.hasUnsavedChanges)
        assertFalse(form.canSubmit)
        assertEquals(listOf("t-1", "t-2", "t-3"), form.tags.map { it.id })
    }

    @Test
    fun edicion_elTipoNoSePuedeCambiar() = runTest {
        val vm = create(repoWith(comida), categoryId = "c-1")

        vm.setType(CategoryType.INCOME)
        advanceUntilIdle()

        assertEquals(CategoryType.EXPENSE, vm.form().type)
    }

    @Test
    fun edicion_cambiarElNombre_activaGuardar_yEnviaSoloElNombreRecortado() = runTest {
        val repo = repoWith(comida)
        val vm = create(repo, categoryId = "c-1")
        type(vm, "  Comida  ")
        assertTrue(vm.form().hasUnsavedChanges)
        assertTrue(vm.form().canSubmit)

        submit(vm)

        assertEquals(listOf(Update("c-1", "Comida", IconUpdate.Unchanged)), repo.updates)
        assertEquals("Comida", vm.form().name)
        assertFalse(vm.form().hasUnsavedChanges)
        assertNull(vm.form().event)
    }

    @Test
    fun edicion_cambiarSoloElIcono_enviaSoloElIcono() = runTest {
        val repo = repoWith(comida)
        val vm = create(repo, categoryId = "c-1")
        vm.setIcon("pets")
        advanceUntilIdle()

        submit(vm)

        assertEquals(listOf(Update("c-1", null, IconUpdate.Set("pets"))), repo.updates)
        assertEquals("pets", vm.form().icon)
        assertFalse(vm.form().hasUnsavedChanges)
    }

    @Test
    fun edicion_quitarElIcono_enviaRemove() = runTest {
        val repo = repoWith(comida)
        val vm = create(repo, categoryId = "c-1")
        vm.setIcon(null)
        advanceUntilIdle()

        submit(vm)

        assertEquals(listOf(Update("c-1", null, IconUpdate.Remove)), repo.updates)
        assertNull(vm.form().icon)
    }

    @Test
    fun edicion_nombreEIconoJuntos() = runTest {
        val repo = repoWith(comida)
        val vm = create(repo, categoryId = "c-1")
        type(vm, "Comida")
        vm.setIcon("home")
        advanceUntilIdle()

        submit(vm)

        assertEquals(listOf(Update("c-1", "Comida", IconUpdate.Set("home"))), repo.updates)
    }

    @Test
    fun edicion_sinCambiosReales_noLlamaAlBackend() = runTest {
        val repo = repoWith(comida)
        val vm = create(repo, categoryId = "c-1")
        type(vm, "Alimentación  ")

        submit(vm)

        assertTrue(repo.updates.isEmpty())
        assertFalse(vm.form().hasUnsavedChanges)
    }

    @Test
    fun edicion_nombreVacio_noSeEnvia() = runTest {
        val repo = repoWith(comida)
        val vm = create(repo, categoryId = "c-1")
        type(vm, "   ")

        submit(vm)

        assertEquals(NameFieldError.REQUIRED, vm.form().nameError)
        assertTrue(repo.updates.isEmpty())
    }

    @Test
    fun edicion_nombreDuplicado_muestraElErrorYConservaElBorrador() = runTest {
        val repo = repoWith(comida).apply { updateResult = CategoryResult.Failure(CategoryFailure.DuplicateName) }
        val vm = create(repo, categoryId = "c-1")
        type(vm, "Casa")

        submit(vm)

        assertEquals(NameFieldError.DUPLICATE, vm.form().nameError)
        assertEquals("Casa", vm.form().name)
        assertTrue(vm.form().hasUnsavedChanges)
        assertNull(vm.form().event)
    }

    @Test
    fun edicion_dosPulsaciones_unaSolaLlamada() = runTest {
        val repo = repoWith(comida).apply { gate = CompletableDeferred() }
        val vm = create(repo, categoryId = "c-1")
        type(vm, "Comida")

        vm.submit()
        vm.submit()
        advanceUntilIdle()
        repo.gate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, repo.updates.size)
    }

    @Test
    fun edicion_sinConexion_conservaLoEscrito() = runTest {
        val repo = repoWith(comida).apply { updateResult = CategoryResult.Failure(CategoryFailure.Offline) }
        val vm = create(repo, categoryId = "c-1")
        type(vm, "Comida")
        vm.setIcon("home")
        advanceUntilIdle()

        submit(vm)

        assertEquals(CategoryEditEvent.Failed(CategoryFailure.Offline), vm.form().event)
        assertEquals("Comida", vm.form().name)
        assertEquals("home", vm.form().icon)
        assertTrue(vm.form().hasUnsavedChanges)
    }

    @Test
    fun edicion_loGuardadoDesdeElRepositorio_seReflejaEnLosTags() = runTest {
        val repo = repoWith(comida)
        val vm = create(repo, categoryId = "c-1")

        repo.data.value = CategoriesData(listOf(comida.copy(tags = comida.tags + Tag("t-9", "Nuevo", TagStatus.ACTIVE, 3))))
        advanceUntilIdle()

        assertEquals(listOf("t-1", "t-2", "t-9", "t-3"), vm.form().tags.map { it.id })
    }

    // --- Rol y archivada (RF-07, RF-08, RF-14; CA-06, CA-07, CA-13) ---

    @Test
    fun integrante_veLosDatosEnSoloLectura_ynoPuedeEditarNiEnviar() = runTest {
        val repo = repoWith(comida)
        val vm = create(repo, categoryId = "c-1", canManage = false)

        vm.setName("Otro")
        vm.setIcon("pets")
        submit(vm)

        assertTrue(vm.form().readOnly)
        assertEquals("Alimentación", vm.form().name)
        assertEquals("shopping_cart", vm.form().icon)
        assertFalse(vm.form().hasUnsavedChanges)
        assertFalse(vm.form().canSubmit)
        assertTrue(repo.updates.isEmpty())
    }

    @Test
    fun categoriaArchivada_seAbreEnSoloLecturaTambienParaElPropietario() = runTest {
        val repo = repoWith(gimnasio)
        val vm = create(repo, categoryId = "c-2", canManage = true)

        vm.setName("Otro")
        submit(vm)

        assertTrue(vm.form().readOnly)
        assertTrue(vm.form().archived)
        assertEquals("Gimnasio", vm.form().name)
        assertTrue(repo.updates.isEmpty())
    }

    @Test
    fun siLaCategoriaSeArchivaConLaPantallaAbierta_pasaASoloLectura() = runTest {
        val repo = repoWith(comida)
        val vm = create(repo, categoryId = "c-1")
        assertFalse(vm.form().readOnly)

        repo.data.value = CategoriesData(listOf(comida.copy(status = CategoryStatus.DEPRECATED)))
        advanceUntilIdle()

        assertTrue(vm.form().readOnly)
        assertTrue(vm.form().archived)
    }

    @Test
    fun rolInsuficiente_avisa_notificaAlHeader_noCambiaNada_yConservaElBorrador() = runTest {
        val repo = repoWith(comida).apply { updateResult = CategoryResult.Failure(CategoryFailure.InsufficientRole) }
        val vm = create(repo, categoryId = "c-1")
        type(vm, "Comida")

        submit(vm)

        assertEquals(CategoryEditEvent.Failed(CategoryFailure.InsufficientRole), vm.form().event)
        assertEquals(1, roleCalls)
        assertEquals("Comida", vm.form().name)
        assertEquals("Alimentación", repo.data.value.categories?.first()?.name)
    }

    @Test
    fun alActualizarseElRol_laPantallaPasaASoloLectura() = runTest {
        val vm = create(repoWith(comida), categoryId = "c-1")
        assertFalse(vm.form().readOnly)

        vm.setCanManage(false)
        advanceUntilIdle()

        assertTrue(vm.form().readOnly)
        assertFalse(vm.form().canSubmit)
    }

    // --- Categoría que desaparece o deja de estar activa ---

    @Test
    fun edicion_alGuardarUnaCategoriaQueYaNoExiste_avisaYVuelveAlListadoActualizandolo() = runTest {
        val repo = repoWith(comida).apply { updateResult = CategoryResult.Failure(CategoryFailure.NotFound) }
        val vm = create(repo, categoryId = "c-1")
        type(vm, "Comida")

        submit(vm)

        assertEquals(CategoryEditEvent.Leave(CategoryFailure.NotFound), vm.form().event)
        assertEquals(1, repo.refreshes)
    }

    @Test
    fun edicion_alGuardarEnUnaCategoriaNoActiva_avisaYActualizaElListado() = runTest {
        val repo = repoWith(comida).apply { updateResult = CategoryResult.Failure(CategoryFailure.CategoryNotActive) }
        val vm = create(repo, categoryId = "c-1")
        type(vm, "Comida")

        submit(vm)

        assertEquals(CategoryEditEvent.Failed(CategoryFailure.CategoryNotActive), vm.form().event)
        assertEquals(1, repo.refreshes)
    }

    @Test
    fun edicion_sinListaCargada_muestraCargando() = runTest {
        val repo = FakeRepository()

        val vm = create(repo, categoryId = "c-1")

        assertEquals(CategoryEditUiState.Loading, vm.uiState.value)
    }

    @Test
    fun edicion_deUnaCategoriaQueNoEstaEnLaLista_esNoEncontrada() = runTest {
        val vm = create(repoWith(comida), categoryId = "c-404")

        assertEquals(CategoryEditUiState.NotFound, vm.uiState.value)
    }

    @Test
    fun edicion_cuandoLaListaSeCarga_elBorradorSeInicializa() = runTest {
        val repo = FakeRepository()
        val vm = create(repo, categoryId = "c-1")
        assertEquals(CategoryEditUiState.Loading, vm.uiState.value)

        repo.data.value = CategoriesData(listOf(comida))
        advanceUntilIdle()

        assertEquals("Alimentación", vm.form().name)
    }
}
