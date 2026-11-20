package com.rocioplata.familyfinaiapp.ui.categories

import com.rocioplata.familyfinaiapp.domain.categories.CategoriesData
import com.rocioplata.familyfinaiapp.domain.categories.CategoriesRepository
import com.rocioplata.familyfinaiapp.domain.categories.Category
import com.rocioplata.familyfinaiapp.domain.categories.CategoryFailure
import com.rocioplata.familyfinaiapp.domain.categories.CategoryResult
import com.rocioplata.familyfinaiapp.domain.categories.CategoryStatus
import com.rocioplata.familyfinaiapp.domain.categories.CategoryType
import com.rocioplata.familyfinaiapp.domain.categories.ColorUpdate
import com.rocioplata.familyfinaiapp.domain.categories.IconUpdate
import com.rocioplata.familyfinaiapp.domain.categories.Tag
import com.rocioplata.familyfinaiapp.domain.categories.TagStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
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
        val archives = mutableListOf<String>()
        val deletes = mutableListOf<String>()
        var archiveResult: CategoryResult<Unit> = CategoryResult.Success(Unit)
        var deleteResult: CategoryResult<Unit> = CategoryResult.Success(Unit)

        override fun categories(familyId: String): Flow<CategoriesData> = data

        var onRefresh: () -> Unit = {}

        override suspend fun refresh(familyId: String): CategoryResult<Unit> {
            refreshes++
            onRefresh()
            return CategoryResult.Success(Unit)
        }

        override suspend fun createCategory(familyId: String, type: CategoryType, name: String, icon: String?, color: String?): CategoryResult<Category> {
            val request = Create(type, name, icon)
            creates += request
            gate?.await()
            return createResult(request)
        }

        override suspend fun updateCategory(familyId: String, categoryId: String, newName: String?, icon: IconUpdate, color: ColorUpdate): CategoryResult<Unit> {
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
        override suspend fun deprecateCategory(familyId: String, categoryId: String): CategoryResult<Unit> {
            archives += categoryId
            gate?.await()
            if (archiveResult is CategoryResult.Success) {
                data.value = CategoriesData(
                    data.value.categories?.map { if (it.id == categoryId) it.copy(status = CategoryStatus.DEPRECATED) else it },
                )
            }
            return archiveResult
        }

        override suspend fun deleteCategory(familyId: String, categoryId: String): CategoryResult<Unit> {
            deletes += categoryId
            gate?.await()
            if (deleteResult is CategoryResult.Success) {
                data.value = CategoriesData(data.value.categories?.filterNot { it.id == categoryId })
                // Como en el dispositivo: la lista se actualiza y la respuesta llega después, dando
                // tiempo a que la pantalla observe la categoría ya ausente.
                yield()
            }
            return deleteResult
        }

        val tagAdds = mutableListOf<String>()
        val tagRenames = mutableListOf<Pair<String, String>>()
        val tagArchives = mutableListOf<String>()
        val tagDeletes = mutableListOf<String>()
        var addTagResult: CategoryResult<Unit> = CategoryResult.Success(Unit)
        var renameTagResult: CategoryResult<Unit> = CategoryResult.Success(Unit)
        var archiveTagResult: CategoryResult<Unit> = CategoryResult.Success(Unit)
        var deleteTagResult: CategoryResult<Unit> = CategoryResult.Success(Unit)

        private fun changeTags(categoryId: String, change: (List<Tag>) -> List<Tag>) {
            data.value = CategoriesData(
                data.value.categories?.map { if (it.id == categoryId) it.copy(tags = change(it.tags)) else it },
            )
        }

        override suspend fun addTag(familyId: String, categoryId: String, name: String): CategoryResult<Unit> {
            tagAdds += name
            gate?.await()
            if (addTagResult is CategoryResult.Success) {
                changeTags(categoryId) { tags -> tags + Tag("t-new-${tags.size}", name, TagStatus.ACTIVE, tags.size) }
            }
            return addTagResult
        }

        override suspend fun renameTag(familyId: String, categoryId: String, tagId: String, name: String): CategoryResult<Unit> {
            tagRenames += tagId to name
            gate?.await()
            if (renameTagResult is CategoryResult.Success) {
                changeTags(categoryId) { tags -> tags.map { if (it.id == tagId) it.copy(name = name) else it } }
            }
            return renameTagResult
        }

        override suspend fun deprecateTag(familyId: String, categoryId: String, tagId: String): CategoryResult<Unit> {
            tagArchives += tagId
            gate?.await()
            if (archiveTagResult is CategoryResult.Success) {
                changeTags(categoryId) { tags ->
                    tags.map { if (it.id == tagId) it.copy(status = TagStatus.DEPRECATED) else it }
                }
            }
            return archiveTagResult
        }

        override suspend fun deleteTag(familyId: String, categoryId: String, tagId: String): CategoryResult<Unit> {
            tagDeletes += tagId
            gate?.await()
            if (deleteTagResult is CategoryResult.Success) {
                changeTags(categoryId) { tags -> tags.filterNot { it.id == tagId } }
            }
            return deleteTagResult
        }

        val reorders = mutableListOf<List<String>>()
        var reorderResult: CategoryResult<Unit> = CategoryResult.Success(Unit)

        override suspend fun reorderTags(
            familyId: String,
            categoryId: String,
            activeTagIdsInOrder: List<String>,
        ): CategoryResult<Unit> {
            reorders += activeTagIdsInOrder
            gate?.await()
            if (reorderResult is CategoryResult.Success) {
                // Como el repositorio real: los activos en el nuevo orden y después los archivados.
                changeTags(categoryId) { tags ->
                    val archived = tags.filter { it.status == TagStatus.DEPRECATED }.sortedBy { it.displayOrder }
                    val byId = tags.associateBy { it.id }
                    (activeTagIdsInOrder.mapNotNull { byId[it] } + archived).mapIndexed { index, tag -> tag.copy(displayOrder = index) }
                }
            }
            return reorderResult
        }
        private fun unused(): Nothing = error("Fuera del alcance de T-23")
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

    // --- Archivar y eliminar (T-21; RF-12, RF-13; CA-11, CA-12, CA-20) ---

    @Test
    fun archivar_pideArchivarLaCategoria_yAlTerminarVuelveAlListado() = runTest {
        val repo = repoWith(comida)
        val vm = create(repo, categoryId = "c-1")

        vm.archive()
        advanceUntilIdle()

        assertEquals(listOf("c-1"), repo.archives)
        assertEquals(CategoryEditUiState.Finished, vm.uiState.value)
        assertEquals(CategoryStatus.DEPRECATED, repo.data.value.categories?.first()?.status)
    }

    @Test
    fun archivar_dosPulsaciones_unaSolaLlamada() = runTest {
        val repo = repoWith(comida).apply { gate = CompletableDeferred() }
        val vm = create(repo, categoryId = "c-1")

        vm.archive()
        vm.archive()
        advanceUntilIdle()
        assertTrue(vm.form().isBusy)
        repo.gate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, repo.archives.size)
    }

    @Test
    fun archivar_sinConexion_avisa_noCambiaNada_yPuedeReintentar() = runTest {
        val repo = repoWith(comida).apply { archiveResult = CategoryResult.Failure(CategoryFailure.Offline) }
        val vm = create(repo, categoryId = "c-1")

        vm.archive()
        advanceUntilIdle()

        assertEquals(CategoryEditEvent.Failed(CategoryFailure.Offline), vm.form().event)
        assertFalse(vm.form().isBusy)
        assertFalse(vm.form().archived)

        vm.consumeEvent()
        repo.archiveResult = CategoryResult.Success(Unit)
        vm.archive()
        advanceUntilIdle()
        assertEquals(CategoryEditUiState.Finished, vm.uiState.value)
        assertEquals(2, repo.archives.size)
    }

    @Test
    fun archivar_rolInsuficiente_avisaYNotificaAlHeader() = runTest {
        val repo = repoWith(comida).apply { archiveResult = CategoryResult.Failure(CategoryFailure.InsufficientRole) }
        val vm = create(repo, categoryId = "c-1")

        vm.archive()
        advanceUntilIdle()

        assertEquals(CategoryEditEvent.Failed(CategoryFailure.InsufficientRole), vm.form().event)
        assertEquals(1, roleCalls)
    }

    @Test
    fun archivar_unaCategoriaQueYaNoExiste_avisaYVuelveAlListadoActualizandolo() = runTest {
        val repo = repoWith(comida).apply { archiveResult = CategoryResult.Failure(CategoryFailure.NotFound) }
        val vm = create(repo, categoryId = "c-1")

        vm.archive()
        advanceUntilIdle()

        assertEquals(CategoryEditEvent.Leave(CategoryFailure.NotFound), vm.form().event)
        assertEquals(1, repo.refreshes)
    }

    @Test
    fun archivar_unaCategoriaYaArchivadaEnOtroLugar_avisaYActualiza() = runTest {
        val repo = repoWith(comida).apply { archiveResult = CategoryResult.Failure(CategoryFailure.CategoryNotActive) }
        val vm = create(repo, categoryId = "c-1")

        vm.archive()
        advanceUntilIdle()

        assertEquals(CategoryEditEvent.Failed(CategoryFailure.CategoryNotActive), vm.form().event)
        assertEquals(1, repo.refreshes)
    }

    @Test
    fun archivarYEliminar_noHacenNadaEnSoloLecturaNiEnAlta() = runTest {
        val member = repoWith(comida)
        val memberVm = create(member, categoryId = "c-1", canManage = false)
        memberVm.archive()
        memberVm.delete()
        advanceUntilIdle()
        assertTrue(member.archives.isEmpty() && member.deletes.isEmpty())

        val archived = repoWith(gimnasio)
        val archivedVm = create(archived, categoryId = "c-2")
        archivedVm.archive()
        archivedVm.delete()
        advanceUntilIdle()
        assertTrue(archived.archives.isEmpty() && archived.deletes.isEmpty())

        val creating = repoWith(comida)
        val creatingVm = create(creating)
        creatingVm.archive()
        creatingVm.delete()
        advanceUntilIdle()
        assertTrue(creating.archives.isEmpty() && creating.deletes.isEmpty())
    }

    @Test
    fun eliminar_pideEliminarLaCategoria_yAlTerminarVuelveAlListado_sinPasarPorNoEncontrada() = runTest {
        val repo = repoWith(comida)
        val vm = CategoryEditViewModel(repo, "f-1", "c-1", CategoryType.EXPENSE, true) { }
        val seen = mutableListOf<CategoryEditUiState>()
        backgroundScope.launch { vm.uiState.collect { seen += it } }
        advanceUntilIdle()

        vm.delete()
        advanceUntilIdle()

        assertEquals(listOf("c-1"), repo.deletes)
        assertEquals(CategoryEditUiState.Finished, vm.uiState.value)
        assertFalse(seen.any { it == CategoryEditUiState.NotFound })
        assertTrue(repo.data.value.categories?.isEmpty() == true)
    }

    @Test
    fun eliminar_dosPulsaciones_unaSolaLlamada() = runTest {
        val repo = repoWith(comida).apply { gate = CompletableDeferred() }
        val vm = create(repo, categoryId = "c-1")

        vm.delete()
        vm.delete()
        advanceUntilIdle()
        repo.gate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, repo.deletes.size)
    }

    @Test
    fun eliminar_conMovimientosAsociados_ofreceArchivar_yAceptarArchiva() = runTest {
        val repo = repoWith(comida).apply { deleteResult = CategoryResult.Failure(CategoryFailure.HasAssociatedItems) }
        val vm = create(repo, categoryId = "c-1")

        vm.delete()
        advanceUntilIdle()

        assertEquals(CategoryEditEvent.OfferArchive, vm.form().event)
        assertFalse(vm.form().archived)
        assertTrue(repo.archives.isEmpty())

        vm.consumeEvent()
        vm.archive()
        advanceUntilIdle()

        assertEquals(listOf("c-1"), repo.archives)
        assertEquals(CategoryEditUiState.Finished, vm.uiState.value)
    }

    @Test
    fun eliminar_conMovimientosAsociados_rechazarLaOfertaNoCambiaNada() = runTest {
        val repo = repoWith(comida).apply { deleteResult = CategoryResult.Failure(CategoryFailure.HasAssociatedItems) }
        val vm = create(repo, categoryId = "c-1")
        vm.delete()
        advanceUntilIdle()

        vm.consumeEvent()
        advanceUntilIdle()

        assertNull(vm.form().event)
        assertTrue(repo.archives.isEmpty())
        assertEquals(1, repo.deletes.size)
        assertEquals(comida, repo.data.value.categories?.first())
    }

    @Test
    fun eliminar_sinConexion_avisa_yLaPantallaSigueMostrandoLaCategoria() = runTest {
        val repo = repoWith(comida).apply { deleteResult = CategoryResult.Failure(CategoryFailure.Offline) }
        val vm = create(repo, categoryId = "c-1")

        vm.delete()
        advanceUntilIdle()

        assertEquals(CategoryEditEvent.Failed(CategoryFailure.Offline), vm.form().event)
        assertEquals("Alimentación", vm.form().name)
        assertFalse(vm.form().isBusy)
    }

    @Test
    fun eliminar_rolInsuficiente_avisaYNotificaAlHeader() = runTest {
        val repo = repoWith(comida).apply { deleteResult = CategoryResult.Failure(CategoryFailure.InsufficientRole) }
        val vm = create(repo, categoryId = "c-1")

        vm.delete()
        advanceUntilIdle()

        assertEquals(CategoryEditEvent.Failed(CategoryFailure.InsufficientRole), vm.form().event)
        assertEquals(1, roleCalls)
    }

    @Test
    fun eliminar_conUnBorradorSinGuardar_seElimina_sinPedirDescarte() = runTest {
        val repo = repoWith(comida)
        val vm = create(repo, categoryId = "c-1")
        type(vm, "Otro nombre")
        assertTrue(vm.form().hasUnsavedChanges)

        vm.delete()
        advanceUntilIdle()

        assertEquals(CategoryEditUiState.Finished, vm.uiState.value)
    }

    // --- Tags: añadir, renombrar, quitar y archivar (T-23; RF-15, RF-16, RF-18, RF-19) ---

    private fun CategoryEditViewModel.tagIds() = form().tags.map { it.id }
    private fun CategoryEditViewModel.tagNames() = form().tags.map { it.name }

    private fun TestScope.typeTag(vm: CategoryEditViewModel, text: String) {
        vm.setNewTagName(text)
        advanceUntilIdle()
    }

    @Test
    fun anadirTag_enviaElNombreRecortado_apareceAlFinalDeLosActivos_yVaciaElCampo() = runTest {
        val repo = repoWith(comida)
        val vm = create(repo, categoryId = "c-1")
        typeTag(vm, "  Despensa  ")

        vm.addTag()
        advanceUntilIdle()

        assertEquals(listOf("Despensa"), repo.tagAdds)
        assertEquals(listOf("Súper", "Feria", "Despensa", "Viejo"), vm.tagNames())
        assertEquals("", vm.form().newTagName)
        assertNull(vm.form().tagError)
        assertNull(vm.form().event)
    }

    @Test
    fun anadirTag_conNombreVacioODeEspacios_noSeEnvia() = runTest {
        val repo = repoWith(comida)
        val vm = create(repo, categoryId = "c-1")

        vm.addTag()
        advanceUntilIdle()
        assertEquals(NameFieldError.REQUIRED, vm.form().tagError)

        typeTag(vm, "   ")
        vm.addTag()
        advanceUntilIdle()
        assertEquals(NameFieldError.REQUIRED, vm.form().tagError)
        assertTrue(repo.tagAdds.isEmpty())
    }

    @Test
    fun anadirTag_elTopeEsDe30_yElErrorDesapareceAlEscribir() = runTest {
        val vm = create(repoWith(comida), categoryId = "c-1")
        vm.addTag()
        advanceUntilIdle()
        assertEquals(NameFieldError.REQUIRED, vm.form().tagError)

        typeTag(vm, "a".repeat(40))

        assertEquals(30, vm.form().newTagName.length)
        assertNull(vm.form().tagError)
    }

    @Test
    fun anadirTag_duplicado_muestraElErrorJuntoAlCampo_ConservaLoEscrito_yNoLoAnade() = runTest {
        val repo = repoWith(comida).apply { addTagResult = CategoryResult.Failure(CategoryFailure.DuplicateName) }
        val vm = create(repo, categoryId = "c-1")
        typeTag(vm, "Súper")

        vm.addTag()
        advanceUntilIdle()

        assertEquals(NameFieldError.DUPLICATE, vm.form().tagError)
        assertEquals("Súper", vm.form().newTagName)
        assertEquals(listOf("t-1", "t-2", "t-3"), vm.tagIds())
        assertNull(vm.form().event)
        assertNull(vm.form().nameError)

        typeTag(vm, "Súper2")
        assertNull(vm.form().tagError)
    }

    @Test
    fun anadirTag_sinConexion_avisa_yConservaLoEscrito() = runTest {
        val repo = repoWith(comida).apply { addTagResult = CategoryResult.Failure(CategoryFailure.Offline) }
        val vm = create(repo, categoryId = "c-1")
        typeTag(vm, "Despensa")

        vm.addTag()
        advanceUntilIdle()

        assertEquals(CategoryEditEvent.Failed(CategoryFailure.Offline), vm.form().event)
        assertEquals("Despensa", vm.form().newTagName)
        assertFalse(vm.form().isBusy)
    }

    @Test
    fun anadirTag_dosPulsaciones_unaSolaLlamada() = runTest {
        val repo = repoWith(comida).apply { gate = CompletableDeferred() }
        val vm = create(repo, categoryId = "c-1")
        typeTag(vm, "Despensa")

        vm.addTag()
        vm.addTag()
        advanceUntilIdle()
        repo.gate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, repo.tagAdds.size)
    }

    @Test
    fun anadirTag_aUnaCategoriaYaNoActiva_avisaYActualiza() = runTest {
        val repo = repoWith(comida).apply { addTagResult = CategoryResult.Failure(CategoryFailure.CategoryNotActive) }
        val vm = create(repo, categoryId = "c-1")
        typeTag(vm, "Despensa")

        vm.addTag()
        advanceUntilIdle()

        assertEquals(CategoryEditEvent.Failed(CategoryFailure.CategoryNotActive), vm.form().event)
        assertEquals(1, repo.refreshes)
    }

    @Test
    fun anadirTag_rolInsuficiente_avisaYNotificaAlHeader() = runTest {
        val repo = repoWith(comida).apply { addTagResult = CategoryResult.Failure(CategoryFailure.InsufficientRole) }
        val vm = create(repo, categoryId = "c-1")
        typeTag(vm, "Despensa")

        vm.addTag()
        advanceUntilIdle()

        assertEquals(CategoryEditEvent.Failed(CategoryFailure.InsufficientRole), vm.form().event)
        assertEquals(1, roleCalls)
    }

    @Test
    fun tags_enSoloLecturaOEnAlta_seIgnoranTodasLasOperaciones() = runTest {
        val contexts = listOf(
            create(repoWith(comida), categoryId = "c-1", canManage = false) to "integrante",
            create(repoWith(gimnasio.copy(tags = listOf(Tag("t-9", "Pesas", TagStatus.ACTIVE, 0)))), categoryId = "c-2") to "archivada",
            create(repoWith(comida)) to "alta",
        )
        contexts.forEach { (vm, _) ->
            vm.setNewTagName("x")
            vm.addTag()
            vm.startRename("t-1")
            vm.removeTag("t-1")
            vm.archiveTag("t-1")
            advanceUntilIdle()
            assertEquals("", vm.form().newTagName)
            assertNull(vm.form().renaming)
        }
    }

    @Test
    fun tags_enSoloLectura_noLlamanAlBackend() = runTest {
        val repo = repoWith(comida)
        val vm = create(repo, categoryId = "c-1", canManage = false)

        vm.addTag()
        vm.removeTag("t-1")
        vm.archiveTag("t-1")
        advanceUntilIdle()

        assertTrue(repo.tagAdds.isEmpty() && repo.tagDeletes.isEmpty() && repo.tagArchives.isEmpty())
    }

    // --- Renombrar ---

    @Test
    fun renombrar_abreElDialogoConElNombreActual_soloParaTagsActivos() = runTest {
        val vm = create(repoWith(comida), categoryId = "c-1")

        vm.startRename("t-2")
        advanceUntilIdle()
        assertEquals(TagRenameDraft("t-2", "Feria", null), vm.form().renaming)

        vm.cancelRename()
        vm.startRename("t-3") // archivado
        vm.startRename("t-404")
        advanceUntilIdle()
        assertNull(vm.form().renaming)
    }

    @Test
    fun renombrar_enviaElNombreRecortado_ycierraElDialogo() = runTest {
        val repo = repoWith(comida)
        val vm = create(repo, categoryId = "c-1")
        vm.startRename("t-2")
        vm.setRenameText("  Mercado  ")
        advanceUntilIdle()

        vm.confirmRename()
        advanceUntilIdle()

        assertEquals(listOf("t-2" to "Mercado"), repo.tagRenames)
        assertNull(vm.form().renaming)
        assertEquals(listOf("Súper", "Mercado", "Viejo"), vm.tagNames())
    }

    @Test
    fun renombrar_sinCambiosReales_noLlamaAlBackend_yCierraElDialogo() = runTest {
        val repo = repoWith(comida)
        val vm = create(repo, categoryId = "c-1")
        vm.startRename("t-2")
        vm.setRenameText("Feria ")
        advanceUntilIdle()

        vm.confirmRename()
        advanceUntilIdle()

        assertTrue(repo.tagRenames.isEmpty())
        assertNull(vm.form().renaming)
    }

    @Test
    fun renombrar_nombreVacio_noSeEnvia_yElDialogoSigue() = runTest {
        val repo = repoWith(comida)
        val vm = create(repo, categoryId = "c-1")
        vm.startRename("t-2")
        vm.setRenameText("   ")
        advanceUntilIdle()

        vm.confirmRename()
        advanceUntilIdle()

        assertEquals(NameFieldError.REQUIRED, vm.form().renaming?.error)
        assertTrue(repo.tagRenames.isEmpty())

        vm.setRenameText("x")
        advanceUntilIdle()
        assertNull(vm.form().renaming?.error)
    }

    @Test
    fun renombrar_duplicado_muestraElErrorEnElDialogo_yConservaElTexto() = runTest {
        val repo = repoWith(comida).apply { renameTagResult = CategoryResult.Failure(CategoryFailure.DuplicateName) }
        val vm = create(repo, categoryId = "c-1")
        vm.startRename("t-2")
        vm.setRenameText("Súper")
        advanceUntilIdle()

        vm.confirmRename()
        advanceUntilIdle()

        assertEquals(TagRenameDraft("t-2", "Súper", NameFieldError.DUPLICATE), vm.form().renaming)
        assertNull(vm.form().event)
    }

    @Test
    fun renombrar_sinConexion_avisa_yElDialogoConservaLoEscrito() = runTest {
        val repo = repoWith(comida).apply { renameTagResult = CategoryResult.Failure(CategoryFailure.Offline) }
        val vm = create(repo, categoryId = "c-1")
        vm.startRename("t-2")
        vm.setRenameText("Mercado")
        advanceUntilIdle()

        vm.confirmRename()
        advanceUntilIdle()

        assertEquals(CategoryEditEvent.Failed(CategoryFailure.Offline), vm.form().event)
        assertEquals("Mercado", vm.form().renaming?.name)
    }

    @Test
    fun renombrar_unTagQueYaNoExiste_avisaDelTag_cierraElDialogo_yActualizaLaLista() = runTest {
        val repo = repoWith(comida).apply { renameTagResult = CategoryResult.Failure(CategoryFailure.NotFound) }
        val vm = create(repo, categoryId = "c-1")
        vm.startRename("t-2")
        vm.setRenameText("Mercado")
        advanceUntilIdle()

        vm.confirmRename()
        advanceUntilIdle()

        assertEquals(CategoryEditEvent.TagNotFound, vm.form().event)
        assertNull(vm.form().renaming)
        assertEquals(1, repo.refreshes)
    }

    @Test
    fun renombrar_siLaCategoriaYaNoExiste_laPantallaPasaANoEncontrada() = runTest {
        val repo = repoWith(comida).apply {
            renameTagResult = CategoryResult.Failure(CategoryFailure.NotFound)
            onRefresh = { data.value = CategoriesData(emptyList()) }
        }
        val vm = create(repo, categoryId = "c-1")
        vm.startRename("t-2")
        vm.setRenameText("Mercado")
        advanceUntilIdle()

        vm.confirmRename()
        advanceUntilIdle()

        assertEquals(CategoryEditUiState.NotFound, vm.uiState.value)
    }

    // --- Quitar y archivar ---

    @Test
    fun quitarTag_sinMovimientos_loElimina() = runTest {
        val repo = repoWith(comida)
        val vm = create(repo, categoryId = "c-1")

        vm.removeTag("t-1")
        advanceUntilIdle()

        assertEquals(listOf("t-1"), repo.tagDeletes)
        assertEquals(listOf("Feria", "Viejo"), vm.tagNames())
        assertNull(vm.form().event)
    }

    @Test
    fun quitarTag_activoConMovimientos_ofreceArchivarlo_yAceptarLoArchiva() = runTest {
        val repo = repoWith(comida).apply { deleteTagResult = CategoryResult.Failure(CategoryFailure.HasAssociatedItems) }
        val vm = create(repo, categoryId = "c-1")

        vm.removeTag("t-1")
        advanceUntilIdle()

        assertEquals(CategoryEditEvent.OfferArchiveTag("t-1"), vm.form().event)
        assertTrue(repo.tagArchives.isEmpty())

        vm.consumeEvent()
        vm.archiveTag("t-1")
        advanceUntilIdle()

        assertEquals(listOf("t-1"), repo.tagArchives)
        // Archivado: pasa tras los activos y queda marcado.
        assertEquals(listOf("t-2", "t-1", "t-3"), vm.tagIds())
        assertEquals(TagStatus.DEPRECATED, vm.form().tags.first { it.id == "t-1" }.status)
    }

    @Test
    fun quitarTag_activoConMovimientos_rechazarLaOfertaNoCambiaNada() = runTest {
        val repo = repoWith(comida).apply { deleteTagResult = CategoryResult.Failure(CategoryFailure.HasAssociatedItems) }
        val vm = create(repo, categoryId = "c-1")
        vm.removeTag("t-1")
        advanceUntilIdle()

        vm.consumeEvent()
        advanceUntilIdle()

        assertNull(vm.form().event)
        assertTrue(repo.tagArchives.isEmpty())
        assertEquals(listOf("t-1", "t-2", "t-3"), vm.tagIds())
    }

    @Test
    fun quitarTag_archivadoConMovimientos_soloInforma() = runTest {
        val repo = repoWith(comida).apply { deleteTagResult = CategoryResult.Failure(CategoryFailure.HasAssociatedItems) }
        val vm = create(repo, categoryId = "c-1")

        vm.removeTag("t-3")
        advanceUntilIdle()

        assertEquals(CategoryEditEvent.TagCannotDelete, vm.form().event)
        assertTrue(repo.tagArchives.isEmpty())
    }

    @Test
    fun quitarTag_archivadoSinMovimientos_loElimina() = runTest {
        val repo = repoWith(comida)
        val vm = create(repo, categoryId = "c-1")

        vm.removeTag("t-3")
        advanceUntilIdle()

        assertEquals(listOf("t-3"), repo.tagDeletes)
        assertEquals(listOf("t-1", "t-2"), vm.tagIds())
    }

    @Test
    fun quitarTag_unTagDesconocido_noLlamaAlBackend() = runTest {
        val repo = repoWith(comida)
        val vm = create(repo, categoryId = "c-1")

        vm.removeTag("t-404")
        vm.archiveTag("t-404")
        advanceUntilIdle()

        assertTrue(repo.tagDeletes.isEmpty() && repo.tagArchives.isEmpty())
    }

    @Test
    fun quitarTag_sinConexion_avisa_yNoCambiaNada() = runTest {
        val repo = repoWith(comida).apply { deleteTagResult = CategoryResult.Failure(CategoryFailure.Offline) }
        val vm = create(repo, categoryId = "c-1")

        vm.removeTag("t-1")
        advanceUntilIdle()

        assertEquals(CategoryEditEvent.Failed(CategoryFailure.Offline), vm.form().event)
        assertEquals(listOf("t-1", "t-2", "t-3"), vm.tagIds())
    }

    @Test
    fun quitarTag_dosPulsaciones_unaSolaLlamada() = runTest {
        val repo = repoWith(comida).apply { gate = CompletableDeferred() }
        val vm = create(repo, categoryId = "c-1")

        vm.removeTag("t-1")
        vm.removeTag("t-1")
        advanceUntilIdle()
        repo.gate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, repo.tagDeletes.size)
    }

    @Test
    fun archivarTag_soloSeArchivaUnTagActivo() = runTest {
        val repo = repoWith(comida)
        val vm = create(repo, categoryId = "c-1")

        vm.archiveTag("t-3") // ya archivado
        advanceUntilIdle()
        assertTrue(repo.tagArchives.isEmpty())

        vm.archiveTag("t-2")
        advanceUntilIdle()
        assertEquals(listOf("t-2"), repo.tagArchives)
    }

    @Test
    fun archivarTag_unTagQueYaNoExiste_avisaDelTag() = runTest {
        val repo = repoWith(comida).apply { archiveTagResult = CategoryResult.Failure(CategoryFailure.NotFound) }
        val vm = create(repo, categoryId = "c-1")

        vm.archiveTag("t-2")
        advanceUntilIdle()

        assertEquals(CategoryEditEvent.TagNotFound, vm.form().event)
        assertEquals(1, repo.refreshes)
    }

    // --- Reordenar tags (T-24; RF-17; CA-16, CA-20) ---

    @Test
    fun reordenar_moverUnTag_enviaSoloLosActivosEnElNuevoOrden_yLosArchivadosSiguenAlFinal() = runTest {
        val repo = repoWith(comida)
        val vm = create(repo, categoryId = "c-1")

        vm.moveTag("t-1", 1)
        advanceUntilIdle()

        assertEquals(listOf(listOf("t-2", "t-1")), repo.reorders)
        assertEquals(listOf("t-2", "t-1", "t-3"), vm.tagIds())
        assertEquals(listOf(0, 1, 2), vm.form().tags.map { it.displayOrder })
    }

    @Test
    fun reordenar_subirYBajar_muevenUnaPosicion_yEnLosLimitesNoHacenNada() = runTest {
        val extra = comida.copy(
            tags = comida.tags + Tag("t-4", "Verdulería", TagStatus.ACTIVE, 3),
        )
        val repo = repoWith(extra)
        val vm = create(repo, categoryId = "c-1")

        vm.moveTagUp("t-1") // ya es el primero
        advanceUntilIdle()
        assertTrue(repo.reorders.isEmpty())

        vm.moveTagDown("t-1")
        advanceUntilIdle()
        assertEquals(listOf("t-2", "t-1", "t-4"), repo.reorders.last())

        vm.moveTagUp("t-4")
        advanceUntilIdle()
        assertEquals(listOf("t-2", "t-4", "t-1"), repo.reorders.last())

        vm.moveTagDown("t-1") // ya es el último de los activos
        advanceUntilIdle()
        assertEquals(2, repo.reorders.size)
    }

    @Test
    fun reordenar_laPosicionSeAjustaALosLimites_yLaMismaPosicionNoLlamaAlBackend() = runTest {
        val repo = repoWith(comida)
        val vm = create(repo, categoryId = "c-1")

        vm.moveTag("t-2", 0) // sube de la segunda a la primera posición
        advanceUntilIdle()
        assertEquals(listOf(listOf("t-2", "t-1")), repo.reorders)

        vm.moveTag("t-2", -5) // ya es el primero
        vm.moveTag("t-1", 99) // ya es el último de los activos
        advanceUntilIdle()
        assertEquals(1, repo.reorders.size)
    }

    @Test
    fun reordenar_esOptimista_yAlConfirmarSeConserva() = runTest {
        val repo = repoWith(comida).apply { gate = CompletableDeferred() }
        val vm = create(repo, categoryId = "c-1")

        vm.moveTag("t-1", 1)
        advanceUntilIdle()

        // Aún sin respuesta: ya se ve el nuevo orden y la pantalla está ocupada.
        assertEquals(listOf("t-2", "t-1", "t-3"), vm.tagIds())
        assertTrue(vm.form().isBusy)

        repo.gate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("t-2", "t-1", "t-3"), vm.tagIds())
        assertFalse(vm.form().isBusy)
        assertNull(vm.form().event)
    }

    @Test
    fun reordenar_siElBackendLoRechaza_restauraElOrden_informa_yActualizaLaLista() = runTest {
        val repo = repoWith(comida).apply { reorderResult = CategoryResult.Failure(CategoryFailure.Unknown) }
        val vm = create(repo, categoryId = "c-1")

        vm.moveTag("t-1", 1)
        advanceUntilIdle()

        assertEquals(listOf("t-1", "t-2", "t-3"), vm.tagIds())
        assertEquals(CategoryEditEvent.ReorderFailed, vm.form().event)
        assertEquals(1, repo.refreshes)
        assertFalse(vm.form().isBusy)
    }

    @Test
    fun reordenar_otrosFallosGenericos_tambienRestauranEInforman() = runTest {
        listOf(CategoryFailure.Network, CategoryFailure.InvalidData).forEach { failure ->
            val repo = repoWith(comida).apply { reorderResult = CategoryResult.Failure(failure) }
            val vm = create(repo, categoryId = "c-1")

            vm.moveTag("t-1", 1)
            advanceUntilIdle()

            assertEquals(listOf("t-1", "t-2", "t-3"), vm.tagIds())
            assertEquals(CategoryEditEvent.ReorderFailed, vm.form().event)
        }
    }

    @Test
    fun reordenar_sinConexion_restaura_avisaDeLaConexion_yNoActualizaLaLista() = runTest {
        val repo = repoWith(comida).apply { reorderResult = CategoryResult.Failure(CategoryFailure.Offline) }
        val vm = create(repo, categoryId = "c-1")

        vm.moveTag("t-1", 1)
        advanceUntilIdle()

        assertEquals(listOf("t-1", "t-2", "t-3"), vm.tagIds())
        assertEquals(CategoryEditEvent.Failed(CategoryFailure.Offline), vm.form().event)
        assertEquals(0, repo.refreshes)
    }

    @Test
    fun reordenar_rolInsuficiente_restauraAvisaYNotificaAlHeader() = runTest {
        val repo = repoWith(comida).apply { reorderResult = CategoryResult.Failure(CategoryFailure.InsufficientRole) }
        val vm = create(repo, categoryId = "c-1")

        vm.moveTag("t-1", 1)
        advanceUntilIdle()

        assertEquals(listOf("t-1", "t-2", "t-3"), vm.tagIds())
        assertEquals(CategoryEditEvent.Failed(CategoryFailure.InsufficientRole), vm.form().event)
        assertEquals(1, roleCalls)
    }

    @Test
    fun reordenar_categoriaNoActiva_restauraAvisaYActualizaUnaSolaVez() = runTest {
        val repo = repoWith(comida).apply { reorderResult = CategoryResult.Failure(CategoryFailure.CategoryNotActive) }
        val vm = create(repo, categoryId = "c-1")

        vm.moveTag("t-1", 1)
        advanceUntilIdle()

        assertEquals(listOf("t-1", "t-2", "t-3"), vm.tagIds())
        assertEquals(CategoryEditEvent.Failed(CategoryFailure.CategoryNotActive), vm.form().event)
        assertEquals(1, repo.refreshes)
    }

    @Test
    fun reordenar_tagInexistente_restauraAvisaDelTagYActualizaUnaSolaVez() = runTest {
        val repo = repoWith(comida).apply { reorderResult = CategoryResult.Failure(CategoryFailure.NotFound) }
        val vm = create(repo, categoryId = "c-1")

        vm.moveTag("t-1", 1)
        advanceUntilIdle()

        assertEquals(listOf("t-1", "t-2", "t-3"), vm.tagIds())
        assertEquals(CategoryEditEvent.TagNotFound, vm.form().event)
        assertEquals(1, repo.refreshes)
    }

    @Test
    fun reordenar_tras_unFallo_sePuedeReintentar() = runTest {
        val repo = repoWith(comida).apply { reorderResult = CategoryResult.Failure(CategoryFailure.Unknown) }
        val vm = create(repo, categoryId = "c-1")
        vm.moveTag("t-1", 1)
        advanceUntilIdle()
        vm.consumeEvent()

        repo.reorderResult = CategoryResult.Success(Unit)
        vm.moveTag("t-1", 1)
        advanceUntilIdle()

        assertEquals(2, repo.reorders.size)
        assertEquals(listOf("t-2", "t-1", "t-3"), vm.tagIds())
        assertNull(vm.form().event)
    }

    @Test
    fun reordenar_losArchivadosNoSeMueven_niLosIdsDesconocidos() = runTest {
        val repo = repoWith(comida)
        val vm = create(repo, categoryId = "c-1")

        vm.moveTag("t-3", 0)
        vm.moveTagUp("t-3")
        vm.moveTagDown("t-3")
        vm.moveTag("t-404", 0)
        advanceUntilIdle()

        assertTrue(repo.reorders.isEmpty())
        assertEquals(listOf("t-1", "t-2", "t-3"), vm.tagIds())
    }

    @Test
    fun reordenar_enSoloLecturaOAlta_noHaceNada() = runTest {
        val member = repoWith(comida)
        create(member, categoryId = "c-1", canManage = false).apply {
            moveTag("t-1", 1)
            moveTagDown("t-1")
        }
        val creating = repoWith(comida)
        create(creating).moveTag("t-1", 1)
        advanceUntilIdle()

        assertTrue(member.reorders.isEmpty() && creating.reorders.isEmpty())
    }

    @Test
    fun reordenar_mientrasHayUnaOperacionEnCurso_seIgnora_yNoPisaElOrdenPendiente() = runTest {
        val repo = repoWith(comida).apply { gate = CompletableDeferred() }
        val vm = create(repo, categoryId = "c-1")

        vm.moveTag("t-1", 1)
        vm.moveTag("t-2", 1)
        advanceUntilIdle()
        assertEquals(listOf("t-2", "t-1", "t-3"), vm.tagIds())
        repo.gate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, repo.reorders.size)
        assertEquals(listOf("t-2", "t-1", "t-3"), vm.tagIds())
    }

    @Test
    fun reordenar_unaVezAnadidosOArchivados_usaSoloLosActivosActuales() = runTest {
        val repo = repoWith(comida)
        val vm = create(repo, categoryId = "c-1")
        vm.setNewTagName("Despensa")
        vm.addTag()
        advanceUntilIdle()
        vm.archiveTag("t-1")
        advanceUntilIdle()

        vm.moveTag("t-new-3", 0)
        advanceUntilIdle()

        assertEquals(listOf("t-new-3", "t-2"), repo.reorders.last())
        assertEquals(listOf("t-new-3", "t-2", "t-1", "t-3").toSet(), vm.tagIds().toSet())
        assertEquals(TagStatus.DEPRECATED, vm.form().tags.last().status)
    }
}
