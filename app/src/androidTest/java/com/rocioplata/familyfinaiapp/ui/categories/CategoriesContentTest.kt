package com.rocioplata.familyfinaiapp.ui.categories

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rocioplata.familyfinaiapp.domain.categories.Category
import com.rocioplata.familyfinaiapp.domain.categories.CategoryStatus
import com.rocioplata.familyfinaiapp.domain.categories.CategoryType
import com.rocioplata.familyfinaiapp.domain.categories.Tag
import com.rocioplata.familyfinaiapp.domain.categories.TagStatus
import com.rocioplata.familyfinaiapp.ui.movements.formatShortMonth
import com.rocioplata.familyfinaiapp.ui.theme.FamilyFinAIAppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.YearMonth

@RunWith(AndroidJUnit4::class)
class CategoriesContentTest {
    @get:Rule
    val rule = createComposeRule()

    /** Lo que la pantalla le pidió al ViewModel. */
    private class Events {
        var backs = 0
        var retries = 0
        var toggles = 0
        val queries = mutableListOf<String>()
        val types = mutableListOf<CategoryType>()
        val opened = mutableListOf<String>()
        val created = mutableListOf<CategoryType>()
        val deleted = mutableListOf<String>()
        val reactivated = mutableListOf<String>()
        var reorderStarts = 0
        var reorderFinishes = 0
        val moves = mutableListOf<String>()
        var consumed = 0
    }

    // --- Datos de prueba ---

    private fun item(
        id: String,
        name: String,
        icon: String? = null,
        status: CategoryStatus = CategoryStatus.ACTIVE,
        vararg tags: String,
    ) = CategoryItem(
        Category(id, CategoryType.EXPENSE, name, icon, status, emptyList()),
        tags.mapIndexed { index, tag -> Tag("$id-$index", tag, TagStatus.ACTIVE, index) },
    )

    private fun content(
        active: List<CategoryItem> = emptyList(),
        archived: List<CategoryItem> = emptyList(),
        type: CategoryType = CategoryType.EXPENSE,
        expenseCount: Int = active.size,
        incomeCount: Int = 0,
        query: String = "",
        expanded: Boolean = false,
        isEmpty: Boolean = false,
        noResults: Boolean = false,
        isStale: Boolean = false,
        isBusy: Boolean = false,
        event: CategoriesEvent? = null,
        reorderMode: Boolean = false,
        canReorder: Boolean = active.size > 1 && query.isEmpty() && !isBusy,
    ) = CategoriesUiState.Content(
        selectedType = type, expenseActiveCount = expenseCount, incomeActiveCount = incomeCount, query = query,
        active = active, archived = archived, archivedExpanded = expanded, isEmpty = isEmpty,
        noResults = noResults, isStale = isStale, isRefreshing = false, isBusy = isBusy, event = event,
        reorderMode = reorderMode, canReorder = canReorder,
    )

    private val alimentacion = item("c1", "Alimentación", "shopping_cart", tags = arrayOf("Súper", "Feria", "Despensa"))
    private val sinIcono = item("c2", "Sin icono")
    private val iconoDesconocido = item("c3", "Icono desconocido", "no_existe", tags = arrayOf("Uno"))
    private val gimnasio = item("c4", "Gimnasio", "fitness_center", CategoryStatus.DEPRECATED)
    private val vacaciones = item("c5", "Vacaciones", "flight", CategoryStatus.DEPRECATED)

    private fun coloredItem(id: String, name: String, icon: String, colorKey: String) = CategoryItem(
        Category(id, CategoryType.EXPENSE, name, icon, CategoryStatus.ACTIVE, emptyList(), colorKey),
        emptyList(),
        CategoryColors.find(colorKey)!!,
    )

    /**
     * Muestra la pantalla con un host mínimo que reproduce lo que haría el ViewModel con el estado
     * (texto de búsqueda, pestaña y sección plegable), sin repositorio ni red.
     */
    private fun show(initial: CategoriesUiState, canManage: Boolean = true, events: Events = Events()): Events {
        rule.setContent {
            FamilyFinAIAppTheme {
                Host(initial, canManage, events)
            }
        }
        return events
    }

    @androidx.compose.runtime.Composable
    private fun Host(initial: CategoriesUiState, canManage: Boolean, events: Events) {
        var state by androidx.compose.runtime.remember { mutableStateOf(initial) }
        fun update(change: (CategoriesUiState.Content) -> CategoriesUiState.Content) {
            (state as? CategoriesUiState.Content)?.let { state = change(it) }
        }
        CategoriesContent(
            state = state,
            canManage = canManage,
            onBack = { events.backs++ },
            onQueryChange = { text ->
                events.queries += text
                update { it.copy(query = text) }
            },
            onSelectType = { type ->
                events.types += type
                update { it.copy(selectedType = type) }
            },
            onRetry = { events.retries++ },
            onCategoryClick = { events.opened += it },
            onCreateClick = { events.created += it },
            onToggleArchived = {
                events.toggles++
                update { it.copy(archivedExpanded = !it.archivedExpanded) }
            },
            onDeleteArchived = { events.deleted += it },
            onEventConsumed = { events.consumed++ },
            onReactivateArchived = { events.reactivated += it },
            reorder = CategoryReorderActions(
                onStart = {
                    events.reorderStarts++
                    update { it.copy(reorderMode = true) }
                },
                onFinish = {
                    events.reorderFinishes++
                    update { it.copy(reorderMode = false) }
                },
                onMove = { id, index -> events.moves += "$id->$index" },
                onMoveUp = { events.moves += "$it-up" },
                onMoveDown = { events.moves += "$it-down" },
            ),
        )
    }

    // --- Pestañas y contadores (CA-02) ---

    @Test
    fun eachCategoryIconIsDrawnInItsOwnColor() {
        show(content(active = listOf(coloredItem("c1", "Comida", "shopping_cart", "coral"), coloredItem("c2", "Casa", "home", "ocean"))))

        val coral = CategoryColors.find("coral")!!.tone.light
        val ocean = CategoryColors.find("ocean")!!.tone.light
        val first = rule.onNodeWithTag("category_row_c1").captureToImage()
        val second = rule.onNodeWithTag("category_row_c2").captureToImage()

        assertTrue("c1 sin su color", first.containsColorNear(coral))
        assertTrue("c2 sin su color", second.containsColorNear(ocean))
        assertFalse("c1 no debe usar el color de c2", first.containsColorNear(ocean))
        assertFalse("c2 no debe usar el color de c1", second.containsColorNear(coral))
    }

    private fun inTab(type: String, text: String) = hasText(text) and hasAnyAncestor(hasTestTag("categories_tab_$type"))

    @Test
    fun tabsShowTheirLabelsAndActiveCounts() {
        show(content(active = listOf(alimentacion, sinIcono), expenseCount = 5, incomeCount = 3))

        rule.onNodeWithTag("categories_tab_expense").assertIsSelected()
        rule.onNodeWithText("Gastos").assertIsDisplayed()
        // La cantidad del tipo visible también está en el título de su sección (RF-10 de la 010): se busca en la pestaña.
        rule.onNode(inTab("expense", "5 activas"), useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("Ingresos").assertIsDisplayed()
        rule.onNode(inTab("income", "3 activas"), useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun aSingleActiveCategoryUsesTheSingularForm() {
        show(content(active = listOf(alimentacion), expenseCount = 1, incomeCount = 0))

        rule.onNode(inTab("expense", "1 activa"), useUnmergedTree = true).assertIsDisplayed()
        rule.onNode(inTab("income", "0 activas"), useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun tappingIncomeAsksForTheIncomeTypeAndSelectsIt() {
        val events = show(content(active = listOf(alimentacion), incomeCount = 2))

        rule.onNodeWithTag("categories_tab_income").performClick()

        assertEquals(listOf(CategoryType.INCOME), events.types)
        rule.onNodeWithTag("categories_tab_income").assertIsSelected()
    }

    // --- Filas (CA-03) ---

    @Test
    fun aCategoryWithAnIconShowsItsOwnIconItsNameAndItsTagsInOrder() {
        show(content(active = listOf(alimentacion)))

        rule.onNodeWithText("Alimentación").assertIsDisplayed()
        rule.onNodeWithText("Súper · Feria · Despensa").assertIsDisplayed()
        rule.onNodeWithTag("category_row_c1", useUnmergedTree = true)
            .assert(hasAnyDescendant(hasContentDescription("Carrito de compras")))
    }

    @Test
    fun aCategoryWithoutIconAndOneWithAnUnknownIconShowTheGenericIcon() {
        show(content(active = listOf(sinIcono, iconoDesconocido)))

        listOf("category_row_c2", "category_row_c3").forEach {
            rule.onNodeWithTag(it, useUnmergedTree = true)
                .assert(hasAnyDescendant(hasContentDescription("Icono genérico de categoría")))
        }
    }

    @Test
    fun aCategoryWithoutTagsShowsNoTagLine() {
        show(content(active = listOf(sinIcono)))

        rule.onNodeWithTag("category_row_c2", useUnmergedTree = true)
            .assert(hasAnyDescendant(hasText("Sin icono")))
        rule.onAllNodesWithText("·", substring = true).assertCountEquals(0)
    }

    @Test
    fun tappingARowOpensThatCategory() {
        val events = show(content(active = listOf(alimentacion, sinIcono)))

        rule.onNodeWithTag("category_row_c2").performClick()

        assertEquals(listOf("c2"), events.opened)
    }

    // --- Archivadas (CA-04) ---

    @Test
    fun archivedSectionStartsCollapsedWithItsCountAndHidesTheRows() {
        show(content(active = listOf(alimentacion), archived = listOf(gimnasio, vacaciones)))

        rule.onNodeWithText("Categorías archivadas").assertIsDisplayed()
        rule.onNodeWithTag("categories_archived_header", useUnmergedTree = true)
            .assert(hasAnyDescendant(hasText("2")))
        rule.onAllNodesWithText("Gimnasio").assertCountEquals(0)
        rule.onAllNodesWithText("Archivada").assertCountEquals(0)
    }

    @Test
    fun expandingTheArchivedSectionShowsEachRowMarkedAsArchived() {
        val events = show(content(active = listOf(alimentacion), archived = listOf(gimnasio, vacaciones)))

        rule.onNodeWithTag("categories_archived_header").performClick()

        assertEquals(1, events.toggles)
        rule.onNodeWithText("Gimnasio").assertIsDisplayed()
        rule.onNodeWithText("Vacaciones").assertIsDisplayed()
        rule.onAllNodesWithText("Archivada").assertCountEquals(2)
    }

    @Test
    fun collapsingTheArchivedSectionHidesTheRowsAgain() {
        show(content(archived = listOf(gimnasio), expanded = true))
        rule.onNodeWithText("Gimnasio").assertIsDisplayed()

        rule.onNodeWithTag("categories_archived_header").performClick()

        rule.onAllNodesWithText("Gimnasio").assertCountEquals(0)
    }

    @Test
    fun withoutArchivedCategoriesTheSectionDoesNotAppear() {
        show(content(active = listOf(alimentacion), archived = emptyList()))

        rule.onAllNodesWithText("Categorías archivadas").assertCountEquals(0)
        rule.onAllNodesWithTag("categories_archived_header").assertCountEquals(0)
    }

    @Test
    fun theArchivedHeaderAnnouncesItsCountAndTheExpandAction() {
        show(content(archived = listOf(gimnasio, vacaciones)))

        rule.onNodeWithContentDescription("Categorías archivadas, 2").assertIsDisplayed()
    }

    @Test
    fun theOwnerSeesTheMenuOfAnArchivedCategoryAndCanAskToDeleteIt() {
        val events = show(content(archived = listOf(gimnasio), expanded = true), canManage = true)

        rule.onNodeWithContentDescription("Más acciones de Gimnasio").performClick()
        rule.onNodeWithText("Eliminar").assertIsDisplayed()
        rule.onNodeWithText("Eliminar").performClick()
        // Pide confirmación antes de eliminar (RF-13).
        rule.onNodeWithText("¿Eliminar la categoría?").assertIsDisplayed()
        assertEquals(emptyList<String>(), events.deleted)
        rule.onNodeWithTag("delete_confirm").performClick()

        assertEquals(listOf("c4"), events.deleted)
    }

    @Test
    fun cancellingTheDeleteConfirmationDeletesNothing() {
        val events = show(content(archived = listOf(gimnasio), expanded = true), canManage = true)
        rule.onNodeWithContentDescription("Más acciones de Gimnasio").performClick()
        rule.onNodeWithText("Eliminar").performClick()

        rule.onNodeWithTag("delete_cancel").performClick()

        rule.onAllNodesWithText("¿Eliminar la categoría?").assertCountEquals(0)
        assertEquals(emptyList<String>(), events.deleted)
    }

    @Test
    fun anArchivedCategoryWithItemsShowsOnlyAnInformativeDialog() {
        val events = show(content(archived = listOf(gimnasio), expanded = true, event = CategoriesEvent.CannotDelete))

        rule.onNodeWithText("No se puede eliminar").assertIsDisplayed()
        rule.onNodeWithText(
            "Esta categoría archivada tiene movimientos asociados y no se puede eliminar.",
        ).assertIsDisplayed()
        rule.onAllNodesWithText("Archivar").assertCountEquals(0)
        rule.onNodeWithTag("cannot_delete_confirm").performClick()

        assertEquals(1, events.consumed)
    }

    @Test
    fun theArchivedMenuIsDisabledWhileAnOperationIsInProgress() {
        show(content(archived = listOf(gimnasio), expanded = true, isBusy = true), canManage = true)

        rule.onNodeWithContentDescription("Más acciones de Gimnasio").assertIsNotEnabled()
    }

    @Test
    fun tappingAnArchivedRowOpensItInReadOnly() {
        val events = show(content(archived = listOf(gimnasio), expanded = true))

        rule.onNodeWithTag("category_row_c4").performClick()

        assertEquals(listOf("c4"), events.opened)
    }

    // --- Roles (CA-06) ---

    @Test
    fun aMemberSeesTheListButNoCreateButtonNorArchivedMenu() {
        show(
            content(active = listOf(alimentacion), archived = listOf(gimnasio), expanded = true),
            canManage = false,
        )

        rule.onNodeWithText("Alimentación").assertIsDisplayed()
        rule.onNodeWithText("Gimnasio").assertIsDisplayed()
        rule.onAllNodesWithText("Crear nueva categoría").assertCountEquals(0)
        rule.onAllNodesWithContentDescription("Más acciones de Gimnasio").assertCountEquals(0)
    }

    @Test
    fun theOwnerSeesTheCreateButtonAndItAsksForTheVisibleType() {
        val events = show(content(active = listOf(alimentacion), type = CategoryType.INCOME), canManage = true)

        rule.onNodeWithText("Crear nueva categoría").performClick()

        assertEquals(listOf(CategoryType.INCOME), events.created)
    }

    @Test
    fun theCreateButtonHasATouchTargetOfAtLeast48dp() {
        show(content(active = listOf(alimentacion)))

        rule.onNodeWithTag("categories_create").assertTouchHeightIsEqualTo(48.dp)
    }

    // --- Estados: vacío, sin resultados, carga y error ---

    @Test
    fun anEmptyExpenseTypeShowsItsMessageAndTheOwnerIsInvitedToCreateOne() {
        show(content(isEmpty = true, type = CategoryType.EXPENSE), canManage = true)

        rule.onNodeWithText("Aún no hay categorías de gasto.").assertIsDisplayed()
        rule.onNodeWithText("Crea la primera con «Crear nueva categoría».").assertIsDisplayed()
    }

    @Test
    fun anEmptyIncomeTypeShowsItsMessageAndAMemberIsNotInvited() {
        show(content(isEmpty = true, type = CategoryType.INCOME), canManage = false)

        rule.onNodeWithText("Aún no hay categorías de ingreso.").assertIsDisplayed()
        rule.onAllNodesWithText("Crea la primera con «Crear nueva categoría».").assertCountEquals(0)
    }

    @Test
    fun aSearchWithoutMatchesShowsTheNoResultsMessage() {
        show(content(query = "zzz", noResults = true))

        rule.onNodeWithText("No hay categorías ni etiquetas que coincidan con la búsqueda.").assertIsDisplayed()
        rule.onAllNodesWithText("Aún no hay categorías de gasto.").assertCountEquals(0)
    }

    @Test
    fun loadingShowsAProgressIndicatorAndItsText() {
        show(CategoriesUiState.Loading)

        rule.onNodeWithTag("categories_loading").assertIsDisplayed()
        rule.onNodeWithText("Cargando categorías…").assertIsDisplayed()
        rule.onAllNodesWithTag("categories_list").assertCountEquals(0)
    }

    @Test
    fun anErrorShowsItsMessageAndRetryAsksToReload() {
        val events = show(CategoriesUiState.Error)

        rule.onNodeWithText("No se pudieron cargar las categorías.").assertIsDisplayed()
        rule.onNodeWithText("Reintentar").performClick()

        assertEquals(1, events.retries)
    }

    @Test
    fun staleDataShowsTheWarningBanner() {
        show(content(active = listOf(alimentacion), isStale = true))

        rule.onNodeWithText("Los datos podrían estar desactualizados. Conéctate para actualizarlos.")
            .assertIsDisplayed()
    }

    @Test
    fun freshDataShowsNoWarningBanner() {
        show(content(active = listOf(alimentacion), isStale = false))

        rule.onAllNodesWithText("Los datos podrían estar desactualizados.", substring = true).assertCountEquals(0)
    }

    // --- Barra y buscador (CA-05) ---

    @Test
    fun theBackArrowAsksToLeave() {
        val events = show(content(active = listOf(alimentacion)))

        rule.onNodeWithContentDescription("Volver").performClick()

        assertEquals(1, events.backs)
    }

    @Test
    fun theSearchIconOpensTheFieldAndTypingReportsTheText() {
        val events = show(content(active = listOf(alimentacion)))
        rule.onAllNodesWithTag("categories_search_field").assertCountEquals(0)

        rule.onNodeWithContentDescription("Buscar").performClick()
        rule.onNodeWithTag("categories_search_field").assertIsDisplayed()
        rule.onNodeWithTag("categories_search_field").performTextInput("sup")

        assertEquals("sup", events.queries.last())
    }

    @Test
    fun theSearchFieldClearsItsTextWithTheClearButton() {
        val events = show(content(active = listOf(alimentacion), query = "sup"))

        rule.onNodeWithContentDescription("Borrar búsqueda").performClick()

        assertEquals("", events.queries.last())
        rule.onAllNodesWithContentDescription("Borrar búsqueda").assertCountEquals(0)
    }

    @Test
    fun theSearchOpensStraightAwayWhenThereIsAlreadyAQuery() {
        show(content(active = listOf(alimentacion), query = "sup"))

        rule.onNodeWithTag("categories_search_field").assertIsDisplayed()
    }

    @Test
    fun theSearchArrowClosesTheFieldAndClearsTheText() {
        val events = show(content(active = listOf(alimentacion), query = "sup"))

        rule.onNodeWithContentDescription("Cerrar búsqueda").performClick()

        assertEquals("", events.queries.last())
        rule.onAllNodesWithTag("categories_search_field").assertCountEquals(0)
        rule.onNodeWithText("Categorías").assertIsDisplayed()
        assertEquals(0, events.backs)
    }

    @Test
    fun systemBackClosesTheSearchFirstAndDoesNotLeaveTheScreen() {
        val events = show(content(active = listOf(alimentacion)))
        rule.onNodeWithContentDescription("Buscar").performClick()
        rule.onNodeWithTag("categories_search_field").performTextInput("sup")

        // Con el teclado abierto el primer atrás solo lo cierra; aquí se prueba el atrás de la pantalla.
        closeSoftKeyboard()
        pressBack()

        rule.onAllNodesWithTag("categories_search_field").assertCountEquals(0)
        assertEquals("", events.queries.last())
        assertEquals(0, events.backs)
    }

    @Test
    fun theOpenSearchSurvivesRecreatingTheScreen() {
        val restoration = StateRestorationTester(rule)
        restoration.setContent {
            FamilyFinAIAppTheme {
                Host(content(active = listOf(alimentacion)), canManage = true, events = Events())
            }
        }
        rule.onNodeWithContentDescription("Buscar").performClick()
        rule.onNodeWithTag("categories_search_field").assertIsDisplayed()

        restoration.emulateSavedInstanceStateRestore()

        rule.onNodeWithTag("categories_search_field").assertIsDisplayed()
    }

    // --- Reactivar y fecha de archivado (T-09 de la 010; RF-01, RF-03, RF-09; CA-01 a CA-03, CA-13, CA-19) ---

    @Test
    fun theOwnerReactivatesAnArchivedCategoryAfterConfirming() {
        val events = show(content(archived = listOf(gimnasio), expanded = true), canManage = true)

        rule.onNodeWithContentDescription("Más acciones de Gimnasio").performClick()
        rule.onNodeWithContentDescription("Reactivar Gimnasio").assertIsDisplayed().performClick()
        rule.onNodeWithText("¿Reactivar la categoría?").assertIsDisplayed()
        rule.onNodeWithText("Volverá a estar disponible para nuevos movimientos y quedará como la última de las activas.")
            .assertIsDisplayed()
        assertEquals(emptyList<String>(), events.reactivated)
        rule.onNodeWithTag("reactivate_confirm").performClick()

        assertEquals(listOf("c4"), events.reactivated)
        rule.onAllNodesWithText("¿Reactivar la categoría?").assertCountEquals(0)
    }

    @Test
    fun cancellingTheReactivationChangesNothing() {
        val events = show(content(archived = listOf(gimnasio), expanded = true), canManage = true)
        rule.onNodeWithContentDescription("Más acciones de Gimnasio").performClick()
        rule.onNodeWithTag("archived_reactivate_c4").performClick()

        rule.onNodeWithTag("reactivate_cancel").performClick()

        rule.onAllNodesWithText("¿Reactivar la categoría?").assertCountEquals(0)
        assertEquals(emptyList<String>(), events.reactivated)
    }

    @Test
    fun theReactivateOptionIsNamedAndHasATouchTargetOfAtLeast48dp() {
        show(content(archived = listOf(gimnasio), expanded = true), canManage = true)
        rule.onNodeWithContentDescription("Más acciones de Gimnasio").performClick()

        rule.onNodeWithTag("archived_reactivate_c4").assertHeightIsAtLeast(48.dp)
        rule.onNodeWithTag("archived_reactivate_c4").assert(hasContentDescription("Reactivar Gimnasio"))
    }

    @Test
    fun aMemberCannotReactivateAndOpensTheArchivedCategoryInReadOnly() {
        val events = show(content(archived = listOf(gimnasio), expanded = true), canManage = false)

        rule.onAllNodesWithContentDescription("Reactivar Gimnasio").assertCountEquals(0)
        rule.onAllNodesWithTag("archived_reactivate_c4").assertCountEquals(0)
        rule.onNodeWithTag("category_row_c4").performClick()

        assertEquals(listOf("c4"), events.opened)
    }

    @Test
    fun theReactivationConfirmationSurvivesRecreatingTheScreen() {
        val restoration = StateRestorationTester(rule)
        val events = Events()
        restoration.setContent {
            FamilyFinAIAppTheme {
                Host(content(archived = listOf(gimnasio), expanded = true), canManage = true, events = events)
            }
        }
        rule.onNodeWithContentDescription("Más acciones de Gimnasio").performClick()
        rule.onNodeWithTag("archived_reactivate_c4").performClick()

        restoration.emulateSavedInstanceStateRestore()

        rule.onNodeWithText("¿Reactivar la categoría?").assertIsDisplayed()
        rule.onNodeWithTag("reactivate_confirm").performClick()
        assertEquals(listOf("c4"), events.reactivated)
    }

    @Test
    fun anArchivedCategoryShowsItsArchiveMonthOrOnlyArchivedWithoutIt() {
        val september = YearMonth.of(2026, 9)
        show(content(archived = listOf(gimnasio.copy(archivedAt = september), vacaciones), expanded = true))

        rule.onNodeWithText("Archivada en ${formatShortMonth(september)}").assertIsDisplayed()
        rule.onAllNodesWithText("Archivada").assertCountEquals(1)
    }

    @Test
    fun aReactivationShowsItsNoticeOnce() {
        val events = show(content(active = listOf(alimentacion), event = CategoriesEvent.Reactivated))

        rule.onNodeWithText("Categoría reactivada").assertIsDisplayed()
        assertEquals(1, events.consumed)
    }

    @Test
    fun aReactivationThatCouldNotMoveToTheEndSaysSo() {
        val events = show(content(active = listOf(alimentacion), event = CategoriesEvent.ReactivatedNotMoved))

        rule.onNodeWithText("Categoría reactivada, pero no se pudo mover al final").assertIsDisplayed()
        assertEquals(1, events.consumed)
    }

    // --- Modo de orden (T-11 de la 010; RF-10; CA-14, CA-15, CA-19) ---

    private val ocio = item("c6", "Ocio", "sports_esports")
    private fun ownerList(query: String = "", isBusy: Boolean = false) =
        content(active = listOf(alimentacion, sinIcono, ocio), archived = listOf(gimnasio), query = query, isBusy = isBusy)

    @Test
    fun theOwnerSeesTheSectionTitleWithItsCountAndReorder() {
        show(ownerList(), canManage = true)

        rule.onNodeWithText("Categorías de gasto").assertIsDisplayed()
        rule.onNodeWithTag("categories_section_header").assert(hasAnyDescendant(hasText("3 activas")))
        rule.onNodeWithTag("categories_reorder").assertIsEnabled().assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun aMemberSeesTheSectionTitleButNoReorder() {
        show(ownerList(), canManage = false)

        rule.onNodeWithText("Categorías de gasto").assertIsDisplayed()
        rule.onAllNodesWithTag("categories_reorder").assertCountEquals(0)
    }

    @Test
    fun reorderIsDisabledWhileThereIsASearchOrAnOperation() {
        show(ownerList(query = "a"), canManage = true)
        rule.onNodeWithTag("categories_reorder").assertIsNotEnabled()
    }

    @Test
    fun reorderIsDisabledWhileAnOperationIsInProgress() {
        show(ownerList(isBusy = true), canManage = true)
        rule.onNodeWithTag("categories_reorder").assertIsNotEnabled()
    }

    @Test
    fun reorderModeShowsOnlyTheActiveWithHandlesAndHidesCreateArchivedAndSearch() {
        val events = show(ownerList().copy(archivedExpanded = true), canManage = true)

        rule.onNodeWithTag("categories_reorder").performClick()

        assertEquals(1, events.reorderStarts)
        rule.onNodeWithTag("categories_reorder_done").assertIsDisplayed()
        rule.onAllNodesWithText("Crear nueva categoría").assertCountEquals(0)
        rule.onAllNodesWithText("Categorías archivadas").assertCountEquals(0)
        rule.onAllNodesWithText("Gimnasio").assertCountEquals(0)
        rule.onAllNodesWithContentDescription("Buscar").assertCountEquals(0)
        rule.onNodeWithContentDescription("Arrastrar para reordenar Ocio").assertIsDisplayed()
        rule.onNodeWithTag("category_reorder_row_c6").performClick()
        assertTrue(events.opened.isEmpty())
    }

    @Test
    fun upAndDownMoveARowAndAreDisabledAtTheEnds() {
        val events = show(ownerList().copy(reorderMode = true), canManage = true)

        rule.onNodeWithTag("category_move_up_c1").assertIsNotEnabled()
        rule.onNodeWithTag("category_move_down_c6").assertIsNotEnabled()
        rule.onNodeWithContentDescription("Bajar Alimentación").performClick()
        rule.onNodeWithContentDescription("Subir Ocio").performClick()

        assertEquals(listOf("c1-down", "c6-up"), events.moves)
    }

    @Test
    fun eachReorderRowOffersUpAndDownAsAccessibilityActions() {
        show(ownerList().copy(reorderMode = true), canManage = true)

        rule.onNodeWithTag("category_reorder_row_c2")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions))
        rule.onNodeWithTag("category_handle_c2").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun doneAndSystemBackCloseTheReorderMode() {
        val events = show(ownerList().copy(reorderMode = true), canManage = true)

        rule.onNodeWithTag("categories_reorder_done").performClick()
        assertEquals(1, events.reorderFinishes)
        rule.onNodeWithTag("categories_reorder").performClick()
        pressBack()

        assertEquals(2, events.reorderFinishes)
        assertEquals(0, events.backs)
        rule.onNodeWithText("Crear nueva categoría").assertIsDisplayed()
    }

    @Test
    fun aMemberNeverSeesTheReorderModeEvenIfTheStateSaysSo() {
        val events = show(ownerList().copy(reorderMode = true), canManage = false)

        rule.onAllNodesWithTag("categories_reorder_list").assertCountEquals(0)
        rule.onNodeWithTag("category_row_c6").performClick()
        assertEquals(listOf("c6"), events.opened)
    }
}
