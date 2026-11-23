package com.rocioplata.familyfinaiapp.ui.categories

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.unit.width
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rocioplata.familyfinaiapp.domain.categories.CategoryFailure
import com.rocioplata.familyfinaiapp.domain.categories.CategoryType
import com.rocioplata.familyfinaiapp.domain.categories.Tag
import com.rocioplata.familyfinaiapp.domain.categories.TagStatus
import com.rocioplata.familyfinaiapp.ui.theme.FamilyFinAIAppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CategoryEditContentTest {
    @get:Rule
    val rule = createComposeRule()

    /** Lo que la pantalla le pidió al ViewModel. */
    private class Events {
        var leaves = 0
        var submits = 0
        var archives = 0
        var deletes = 0
        var consumed = 0
        val names = mutableListOf<String>()
        val icons = mutableListOf<String?>()
        val colors = mutableListOf<String?>()
        val types = mutableListOf<CategoryType>()
        val created = mutableListOf<String>()
        val newTagNames = mutableListOf<String>()
        var tagAdds = 0
        val renameStarts = mutableListOf<String>()
        val renameTexts = mutableListOf<String>()
        var renameConfirms = 0
        var renameCancels = 0
        val removes = mutableListOf<String>()
        val tagArchives = mutableListOf<String>()
        val moves = mutableListOf<Pair<String, Int>>()
        val moveUps = mutableListOf<String>()
        val moveDowns = mutableListOf<String>()
    }

    // --- Datos de prueba ---

    private val tags = listOf(
        Tag("t1", "Supermercado", TagStatus.ACTIVE, 0),
        Tag("t2", "Feria", TagStatus.ACTIVE, 1),
        Tag("t3", "Despensa", TagStatus.ACTIVE, 2),
        Tag("t4", "Viejo", TagStatus.DEPRECATED, 3),
    )

    private fun form(
        isCreate: Boolean = false,
        type: CategoryType = CategoryType.EXPENSE,
        name: String = "Alimentación",
        nameError: NameFieldError? = null,
        icon: String? = "shopping_cart",
        readOnly: Boolean = false,
        archived: Boolean = false,
        tags: List<Tag> = this.tags,
        dirty: Boolean = false,
        busy: Boolean = false,
        event: CategoryEditEvent? = null,
        newTagName: String = "",
        tagError: NameFieldError? = null,
        renaming: TagRenameDraft? = null,
        color: String? = null,
    ) = CategoryEditUiState.Form(
        isCreate = isCreate, type = type, typeEditable = isCreate && !readOnly, name = name, nameError = nameError,
        icon = icon, readOnly = readOnly, archived = archived, tags = if (isCreate) emptyList() else tags,
        hasUnsavedChanges = dirty, isBusy = busy, event = event,
        newTagName = newTagName, tagError = tagError, renaming = renaming,
        color = color, effectiveColor = CategoryColors.resolve(color, 0),
    )

    /**
     * Muestra la pantalla con un host mínimo que reproduce lo que haría el ViewModel con el estado
     * (texto, icono, aviso consumido), sin repositorio ni red.
     */
    private fun show(initial: CategoryEditUiState.Form, events: Events = Events()): Events {
        rule.setContent { FamilyFinAIAppTheme { Host(initial, events) } }
        return events
    }

    @androidx.compose.runtime.Composable
    private fun Host(initial: CategoryEditUiState.Form, events: Events) {
        var state by remember { mutableStateOf(initial) }
        CategoryEditContent(
            state = state,
            onLeave = { events.leaves++ },
            onNameChange = {
                events.names += it
                state = state.copy(name = it)
            },
            onIconChange = {
                events.icons += it
                state = state.copy(icon = it)
            },
            onColorChange = {
                events.colors += it
                state = state.copy(color = it, effectiveColor = CategoryColors.resolve(it, 0))
            },
            onTypeChange = {
                events.types += it
                state = state.copy(type = it)
            },
            onSubmit = { events.submits++ },
            onArchive = { events.archives++ },
            onDelete = { events.deletes++ },
            onEventConsumed = {
                events.consumed++
                state = state.copy(event = null)
            },
            onCreated = { events.created += it },
            tagActions = tagActions(events, { state }, { state = it }),
        )
    }

    private fun tagActions(
        events: Events,
        state: () -> CategoryEditUiState.Form,
        update: (CategoryEditUiState.Form) -> Unit,
    ) = TagActions(
        onNewNameChange = {
            events.newTagNames += it
            update(state().copy(newTagName = it))
        },
        onAdd = { events.tagAdds++ },
        onStartRename = { id ->
            events.renameStarts += id
            val tag = state().tags.first { it.id == id }
            update(state().copy(renaming = TagRenameDraft(id, tag.name, null)))
        },
        onRenameTextChange = { text ->
            events.renameTexts += text
            update(state().copy(renaming = state().renaming?.copy(name = text)))
        },
        onConfirmRename = { events.renameConfirms++ },
        onCancelRename = {
            events.renameCancels++
            update(state().copy(renaming = null))
        },
        onRemove = { events.removes += it },
        onArchive = { events.tagArchives += it },
        onMove = { id, index -> events.moves += id to index },
        onMoveUp = { events.moveUps += it },
        onMoveDown = { events.moveDowns += it },
    )

    private fun absent(tag: String) = rule.onAllNodesWithTag(tag, useUnmergedTree = true).assertCountEquals(0)

    // --- Roles (CA-06) ---

    @Test
    fun aMemberSeesTheDataButNoActions() {
        show(form(readOnly = true))

        rule.onNodeWithText("Solo el Propietario de la familia puede modificar las categorías.").assertIsDisplayed()
        rule.onNodeWithTag("category_edit_name").assertIsDisplayed()
        absent("category_edit_submit")
        absent("category_edit_archive")
        absent("category_edit_delete")
        absent("tag_new_field")
        absent("tag_add_button")
        absent("tag_remove_t1")
        absent("tag_handle_t1")
        rule.onNodeWithText("Supermercado").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aMembersIconAndTypeSelectorsAreDisabled() {
        show(form(readOnly = true, icon = "pets"))

        rule.onNodeWithTag("category_icon_home").assertIsNotEnabled()
        rule.onNodeWithTag("category_icon_none").assertIsNotEnabled()
        rule.onNodeWithTag("category_type_expense").assertIsNotEnabled()
        rule.onNodeWithTag("category_type_income").assertIsNotEnabled()
    }

    @Test
    fun aMembersColorSelectorIsDisabledAndDoesNotReportTaps() {
        val events = show(form(readOnly = true, color = "plum"))

        rule.onNodeWithTag("category_color_none").assertIsNotEnabled()
        rule.onNodeWithTag("category_color_coral").assertIsNotEnabled()
        rule.onNodeWithTag("category_color_coral").performScrollTo().performClick()

        assertTrue(events.colors.isEmpty())
    }

    @Test
    fun aMemberCannotRenameATagByTappingIt() {
        val events = show(form(readOnly = true))

        rule.onNodeWithTag("tag_row_t1").performScrollTo().performClick()

        assertTrue(events.renameStarts.isEmpty())
        absent("tag_rename_field")
    }

    // --- Categoría archivada (CA-13) ---

    @Test
    fun anArchivedCategoryIsReadOnlyForTheOwnerToo() {
        show(form(archived = true, readOnly = true))

        rule.onNodeWithText("Esta categoría está archivada y no se puede modificar.").assertIsDisplayed()
        absent("category_edit_submit")
        absent("category_edit_archive")
        absent("category_edit_delete")
        absent("tag_new_field")
        rule.onNodeWithTag("category_icon_home").assertIsNotEnabled()
    }

    @Test
    fun anArchivedCategoryShowsTheSameForAMember() {
        show(form(archived = true, readOnly = true))

        rule.onAllNodesWithText("Solo el Propietario de la familia puede modificar las categorías.").assertCountEquals(0)
        rule.onNodeWithText("Esta categoría está archivada y no se puede modificar.").assertIsDisplayed()
    }

    // --- Formulario (CA-08, CA-09, CA-10) ---

    @Test
    fun theCreateFormHasItsTitleAndButtonAndNoTagsNorArchiveActions() {
        show(form(isCreate = true, name = "", icon = null))

        rule.onNodeWithText("Nueva categoría").assertIsDisplayed()
        rule.onNodeWithText("Crear categoría").assertIsDisplayed()
        absent("tag_list")
        absent("category_edit_archive")
        absent("category_edit_delete")
    }

    @Test
    fun whenCreatingTheTypeCanBeChosen() {
        val events = show(form(isCreate = true, name = "", icon = null))

        rule.onNodeWithTag("category_type_income").performClick()

        assertEquals(listOf(CategoryType.INCOME), events.types)
        rule.onNodeWithTag("category_type_income").assertIsSelected()
    }

    @Test
    fun whenEditingTheTypeIsShownButCannotBeChanged() {
        show(form(isCreate = false, type = CategoryType.INCOME))

        rule.onNodeWithText("Editar categoría").assertIsDisplayed()
        rule.onNodeWithTag("category_type_income").assertIsSelected().assertIsNotEnabled()
        rule.onNodeWithTag("category_type_expense").assertIsNotEnabled()
        rule.onNodeWithText("El tipo no se puede cambiar una vez creada la categoría.").assertIsDisplayed()
    }

    @Test
    fun saveIsDisabledWithoutChangesAndEnabledWithThem() {
        show(form(dirty = false))
        rule.onNodeWithTag("category_edit_submit").assertIsNotEnabled()
    }

    @Test
    fun saveIsEnabledWhenThereAreChangesAndAsksToSave() {
        val events = show(form(dirty = true))

        rule.onNodeWithTag("category_edit_submit").assertIsEnabled().performClick()

        assertEquals(1, events.submits)
    }

    @Test
    fun createIsAlwaysAvailableAndWhileBusyItShowsProgressAndIsDisabled() {
        show(form(isCreate = true, name = "", icon = null, busy = true))

        rule.onNodeWithTag("category_edit_submit").assertIsNotEnabled()
        rule.onNodeWithTag("category_edit_progress").assertIsDisplayed()
    }

    @Test
    fun typingTheNameReportsItAndShowsTheCounter() {
        val events = show(form(isCreate = true, name = "", icon = null))

        rule.onNodeWithTag("category_edit_name").performTextInput("Casa")

        assertEquals("Casa", events.names.last())
        rule.onNodeWithText("4 / 50").assertIsDisplayed()
    }

    @Test
    fun theNameErrorsAreShownNextToTheField() {
        show(form(nameError = NameFieldError.REQUIRED))
        rule.onNodeWithTag("category_edit_name_error", useUnmergedTree = true).assert(hasText("Escribe un nombre."))
    }

    @Test
    fun aDuplicateNameShowsItsMessageNextToTheFieldAndKeepsWhatWasWritten() {
        show(form(isCreate = true, name = "Comida", icon = null, nameError = NameFieldError.DUPLICATE))

        rule.onNodeWithTag("category_edit_name_error", useUnmergedTree = true).assert(hasText("Ya existe una categoría con ese nombre."))
        rule.onNodeWithText("Comida").assertIsDisplayed()
    }

    @Test
    fun aTooLongNameShowsItsMessage() {
        show(form(nameError = NameFieldError.TOO_LONG))
        rule.onNodeWithTag("category_edit_name_error", useUnmergedTree = true).assert(hasText("El nombre no puede superar los 50 caracteres."))
    }

    // --- Iconos (CA-10) ---

    @Test
    fun theSelectedIconIsAnnouncedAsSelected() {
        show(form(icon = "pets"))

        rule.onNodeWithTag("category_icon_pets").assertIsSelected()
            .assert(hasContentDescription("Mascotas, seleccionado"))
        rule.onNodeWithTag("category_icon_home").assertIsNotSelected()
            .assert(hasContentDescription("Casa"))
        rule.onNodeWithTag("category_icon_none").assertIsNotSelected()
    }

    @Test
    fun withoutAnIconTheNoIconOptionIsTheSelectedOne() {
        show(form(icon = null))

        rule.onNodeWithTag("category_icon_none").assertIsSelected()
        rule.onNodeWithTag("category_icon_pets").assertIsNotSelected()
    }

    @Test
    fun choosingAnIconAndRemovingItAreReported() {
        val events = show(form(icon = "pets"))

        rule.onNodeWithTag("category_icon_home").performClick()
        rule.onNodeWithTag("category_icon_none").performClick()

        assertEquals(listOf("home", null), events.icons)
    }

    // --- Color (RF-14 y RF-15 de la 008) ---

    private val allColors = CategoryColors.options

    @Test
    fun theColorCatalogShowsAll16ColorsAndTheAutomaticOption() {
        show(form())

        assertEquals(16, allColors.size)
        rule.onNodeWithTag("category_color_none").performScrollTo().assertIsDisplayed()
        allColors.forEach { rule.onNodeWithTag("category_color_${it.key}").performScrollTo().assertIsDisplayed() }
        rule.onNodeWithText("Salvia").assertExists()
        rule.onNodeWithText("Grafito").performScrollTo().assertIsDisplayed()
    }

    private fun swatchBounds(key: String): Triple<Float, Float, Int> {
        val node = rule.onNodeWithTag("category_color_$key").fetchSemanticsNode()
        return Triple(node.positionInRoot.x, node.positionInRoot.y, node.size.width)
    }

    @Test
    fun allSixteenColorsFitTheScreenWidth_withoutHorizontalScrolling_inFourRows() {
        show(form())
        rule.onNodeWithTag("category_color_grid").performScrollTo()

        val rootWidth = rule.onRoot().fetchSemanticsNode().size.width
        val bounds = allColors.map { swatchBounds(it.key) }
        bounds.forEachIndexed { index, (x, _, w) ->
            assertTrue("${allColors[index].key} se sale de la pantalla (x=$x, ancho=$w, pantalla=$rootWidth)", x >= 0f && x + w <= rootWidth)
        }
        // Filas completas de igual cantidad (la última puede tener menos) y sin un color suelto: en un teléfono, 4 × 4.
        val perRow = bounds.groupBy { it.second }.toSortedMap().values.map { it.size }
        assertEquals("los 16 colores en total", 16, perRow.sum())
        assertTrue("filas desiguales: $perRow", perRow.dropLast(1).all { it == perRow.first() } && perRow.last() <= perRow.first())
        assertTrue("al menos 3 por fila: $perRow", perRow.first() >= 3)
        if (rootWidth >= 1000) assertEquals("en un teléfono, cuatro filas de cuatro", listOf(4, 4, 4, 4), perRow)
    }

    @Test
    fun allSixteenColorsAreReachableWithoutSwipingSideways() {
        show(form(color = "graphite"))

        allColors.forEach { rule.onNodeWithTag("category_color_${it.key}").performScrollTo().assertIsDisplayed() }
        rule.onNodeWithTag("category_color_graphite").assertIsSelected()
    }

    @Test
    fun withTheTextAt200Percent_theColorGridUsesFewerColumns_andNothingLeavesTheScreen() {
        rule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, 2f)) {
                FamilyFinAIAppTheme { Host(form(), Events()) }
            }
        }
        rule.onNodeWithTag("category_color_grid").performScrollTo()

        val rootWidth = rule.onRoot().fetchSemanticsNode().size.width
        val bounds = allColors.map { swatchBounds(it.key) }
        assertTrue("todos dentro del ancho", bounds.all { (x, _, w) -> x >= 0f && x + w <= rootWidth })
        val perRow = bounds.groupBy { it.second }.values.map { it.size }
        assertTrue("con texto grande hay menos de cuatro columnas ($perRow)", perRow.max() < 4)
    }

    @Test
    fun withoutAColorTheAutomaticOptionIsTheSelectedOne() {
        show(form(color = null))

        rule.onNodeWithTag("category_color_none").assertIsSelected()
        allColors.forEach { rule.onNodeWithTag("category_color_${it.key}").assertIsNotSelected() }
    }

    @Test
    fun theSelectedColorIsAnnouncedByNameAsSelected() {
        show(form(color = "coral"))

        rule.onNodeWithTag("category_color_coral").assertIsSelected()
            .assert(hasContentDescription("Coral, seleccionado"))
        rule.onNodeWithTag("category_color_plum").assertIsNotSelected().assert(hasContentDescription("Ciruela"))
        rule.onNodeWithTag("category_color_none").assertIsNotSelected()
    }

    @Test
    fun aSavedColorTheAppDoesNotKnowSelectsNothing() {
        show(form(color = "fucsia_99"))

        rule.onNodeWithTag("category_color_none").assertIsNotSelected()
        allColors.forEach { rule.onNodeWithTag("category_color_${it.key}").assertIsNotSelected() }
    }

    @Test
    fun choosingAColorAndGoingBackToAutomaticAreReported() {
        val events = show(form(color = null))

        rule.onNodeWithTag("category_color_slate").performScrollTo().performClick()
        rule.onNodeWithTag("category_color_slate").assertIsSelected()
        rule.onNodeWithTag("category_color_none").performClick()

        assertEquals(listOf<String?>("slate", null), events.colors)
        rule.onNodeWithTag("category_color_none").assertIsSelected()
    }

    @Test
    fun thePreviewIconIsDescribedWithTheEffectiveColorName() {
        show(form(color = "plum"))

        rule.onNodeWithTag("category_color_preview", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithContentDescription("Vista previa del icono en Ciruela", useUnmergedTree = true).assertExists()
    }

    @Test
    fun theCatalogIconsAreDrawnInTheEffectiveColor() {
        show(form(color = "coral", icon = null))

        val coral = CategoryColors.find("coral")!!.tone.light
        val image = rule.onNodeWithTag("category_icon_pets").performScrollTo().captureToImage()

        assertTrue("el icono no se dibuja en el color elegido", image.containsColorNear(coral))
    }

    @Test
    fun theCatalogShowsEveryIcon() {
        show(form())

        CategoryIcons.options.forEach { rule.onNodeWithTag("category_icon_${it.key}").assertExists() }
        assertEquals(62, CategoryIcons.options.size)
    }

    @Test
    fun theLastIncompleteRowKeepsTheSameCellWidth() {
        show(form())

        val first = rule.onNodeWithTag("category_icon_${CategoryIcons.options.first().key}")
            .performScrollTo().getBoundsInRoot()
        val last = rule.onNodeWithTag("category_icon_${CategoryIcons.options.last().key}")
            .performScrollTo().getBoundsInRoot()

        assertEquals(first.width.value, last.width.value, 1f)
    }

    // --- Archivar y eliminar (CA-11, CA-12) ---

    @Test
    fun archivingAsksForConfirmationWarningItIsIrreversibleAndCancelChangesNothing() {
        val events = show(form())

        rule.onNodeWithTag("category_edit_archive").performScrollTo().performClick()
        rule.onNodeWithText("¿Archivar la categoría?").assertIsDisplayed()
        rule.onNodeWithText("Dejará de estar disponible para nuevos movimientos y no podrás reactivarla.")
            .assertIsDisplayed()
        rule.onNodeWithTag("archive_cancel").performClick()

        rule.onAllNodesWithText("¿Archivar la categoría?").assertCountEquals(0)
        assertEquals(0, events.archives)
    }

    @Test
    fun confirmingTheArchiveAsksToArchive() {
        val events = show(form())

        rule.onNodeWithTag("category_edit_archive").performScrollTo().performClick()
        rule.onNodeWithTag("archive_confirm").performClick()

        assertEquals(1, events.archives)
        rule.onAllNodesWithText("¿Archivar la categoría?").assertCountEquals(0)
    }

    @Test
    fun deletingAsksForConfirmationAndOnlyConfirmingDeletes() {
        val events = show(form())

        rule.onNodeWithTag("category_edit_delete").performScrollTo().performClick()
        rule.onNodeWithText("¿Eliminar la categoría?").assertIsDisplayed()
        rule.onNodeWithTag("delete_cancel").performClick()
        assertEquals(0, events.deletes)

        rule.onNodeWithTag("category_edit_delete").performScrollTo().performClick()
        rule.onNodeWithTag("delete_confirm").performClick()
        assertEquals(1, events.deletes)
    }

    @Test
    fun archiveAndDeleteAreDisabledWhileAnOperationIsInProgress() {
        show(form(busy = true))

        rule.onNodeWithTag("category_edit_archive").assertIsNotEnabled()
        rule.onNodeWithTag("category_edit_delete").assertIsNotEnabled()
    }

    @Test
    fun anActiveCategoryWithItemsOffersToArchiveItInstead() {
        val events = show(form(event = CategoryEditEvent.OfferArchive))

        rule.onNodeWithText("No se puede eliminar").assertIsDisplayed()
        rule.onNodeWithText("Esta categoría tiene movimientos asociados. Puedes archivarla en su lugar; no podrás reactivarla.")
            .assertIsDisplayed()
        rule.onNodeWithTag("offer_archive_confirm").performClick()

        assertEquals(1, events.consumed)
        assertEquals(1, events.archives)
    }

    @Test
    fun rejectingTheArchiveOfferOnlyDismissesIt() {
        val events = show(form(event = CategoryEditEvent.OfferArchive))

        rule.onNodeWithTag("offer_archive_cancel").performClick()

        assertEquals(1, events.consumed)
        assertEquals(0, events.archives)
    }

    // --- Descartar (CA-24) ---

    @Test
    fun leavingWithUnsavedChangesAsksBeforeDiscardingAndKeepEditingStays() {
        val events = show(form(dirty = true))

        rule.onNodeWithTag("category_edit_back").performClick()
        rule.onNodeWithText("¿Descartar los cambios?").assertIsDisplayed()
        rule.onNodeWithTag("discard_keep").performClick()

        rule.onAllNodesWithText("¿Descartar los cambios?").assertCountEquals(0)
        assertEquals(0, events.leaves)
    }

    @Test
    fun confirmingTheDiscardLeaves() {
        val events = show(form(dirty = true))

        rule.onNodeWithTag("category_edit_back").performClick()
        rule.onNodeWithTag("discard_confirm").performClick()

        assertEquals(1, events.leaves)
    }

    @Test
    fun leavingWithoutChangesLeavesStraightAway() {
        val events = show(form(dirty = false))

        rule.onNodeWithTag("category_edit_back").performClick()

        assertEquals(1, events.leaves)
        rule.onAllNodesWithText("¿Descartar los cambios?").assertCountEquals(0)
    }

    @Test
    fun theSystemBackGestureAlsoAsksBeforeDiscarding() {
        val events = show(form(dirty = true))

        pressBack()

        rule.onNodeWithText("¿Descartar los cambios?").assertIsDisplayed()
        assertEquals(0, events.leaves)
    }

    @Test
    fun theSystemBackGestureLeavesWhenThereAreNoChanges() {
        val events = show(form(dirty = false))

        pressBack()

        assertEquals(1, events.leaves)
    }

    // --- Eventos ---

    @Test
    fun aCreatedEventMovesOnToEditingTheNewCategory() {
        val events = show(form(isCreate = true, name = "Casa", icon = null, event = CategoryEditEvent.Created("c-9")))

        rule.waitForIdle()

        assertEquals(listOf("c-9"), events.created)
        assertEquals(1, events.consumed)
    }

    @Test
    fun aFailureIsShownAsAMessageAndConsumedOnce() {
        val events = show(form(event = CategoryEditEvent.Failed(CategoryFailure.Offline)))

        rule.waitForIdle()

        rule.onNodeWithText("Necesitas conexión para hacer cambios. Lo que escribiste se conserva.").assertIsDisplayed()
        assertEquals(1, events.consumed)
    }

    @Test
    fun aVanishedCategoryShowsANoticeAndLeavesWithoutAskingAboutTheDraft() {
        val events = show(form(dirty = true, event = CategoryEditEvent.Leave(CategoryFailure.NotFound)))

        rule.onNodeWithText("Esta categoría ya no existe. Actualizamos la lista.").assertIsDisplayed()
        rule.onNodeWithTag("gone_confirm").performClick()

        assertEquals(1, events.consumed)
        assertEquals(1, events.leaves)
        rule.onAllNodesWithText("¿Descartar los cambios?").assertCountEquals(0)
    }

    // --- Etiquetas (CA-14, CA-15, CA-17, CA-18) ---

    @Test
    fun theTagListShowsActiveTagsThenArchivedOnesMarkedAndDimmed() {
        show(form())

        rule.onNodeWithText("Etiquetas").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("3 activas").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Archivado").performScrollTo().assertIsDisplayed()
        // Posiciones sin recortar por el área visible (el formulario se desplaza).
        val lastActive = rule.onNodeWithTag("tag_row_t3").fetchSemanticsNode().positionInRoot.y
        val archived = rule.onNodeWithTag("tag_row_t4").fetchSemanticsNode().positionInRoot.y
        assertTrue("el archivado va después de los activos", archived > lastActive)
    }

    @Test
    fun archivedTagsHaveNoRemoveHandleNorRenameForAMemberButTheOwnerCanDeleteThem() {
        val events = show(form())

        absent("tag_handle_t4")
        rule.onNodeWithTag("tag_row_t4").performScrollTo().performClick()
        assertTrue(events.renameStarts.isEmpty())
        // El Propietario sí puede quitar un archivado (RF-19).
        rule.onNodeWithTag("tag_remove_t4").assertIsDisplayed()
    }

    @Test
    fun anEmptyCategoryShowsTheEmptyTagsMessage() {
        show(form(tags = emptyList()))

        rule.onNodeWithTag("tag_empty").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("0 activas").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aSingleActiveTagUsesTheSingularForm() {
        show(form(tags = listOf(Tag("t1", "Uno", TagStatus.ACTIVE, 0))))

        rule.onNodeWithText("1 activa").performScrollTo().assertIsDisplayed()
        absent("tag_handle_t1")
    }

    @Test
    fun typingAndAddingATagAreReportedAndItsErrorIsShownNextToTheField() {
        val events = show(form(tagError = NameFieldError.DUPLICATE, newTagName = "Feria"))

        rule.onNodeWithTag("tag_new_error", useUnmergedTree = true).assert(hasText("Ya existe una etiqueta con ese nombre."))
        rule.onNodeWithTag("tag_new_field").performScrollTo().performTextReplacement("Feriaa")
        rule.onNodeWithTag("tag_add_button").performScrollTo().performClick()

        assertEquals("Feriaa", events.newTagNames.last())
        assertEquals(1, events.tagAdds)
        rule.onNodeWithText("6 / 30").assertIsDisplayed()
    }

    @Test
    fun tappingAnActiveTagOpensTheRenameDialogWithItsName() {
        val events = show(form())

        rule.onNodeWithTag("tag_row_t2").performScrollTo().performClick()

        assertEquals(listOf("t2"), events.renameStarts)
        rule.onNodeWithText("Renombrar etiqueta").assertIsDisplayed()
        rule.onNodeWithTag("tag_rename_field").assertIsDisplayed()
        rule.onNodeWithTag("tag_rename_field").assert(hasText("Feria"))
    }

    @Test
    fun theRenameDialogReportsTheTextConfirmsCancelsAndShowsItsError() {
        val events = show(form(renaming = TagRenameDraft("t2", "Feria", NameFieldError.DUPLICATE)))

        rule.onNodeWithTag("tag_rename_error", useUnmergedTree = true).assert(hasText("Ya existe una etiqueta con ese nombre."))
        rule.onNodeWithTag("tag_rename_field").performTextReplacement("Ferias")
        rule.onNodeWithTag("tag_rename_confirm").performClick()
        rule.onNodeWithTag("tag_rename_cancel").performClick()

        assertEquals("Ferias", events.renameTexts.last())
        assertEquals(1, events.renameConfirms)
        assertEquals(1, events.renameCancels)
        absent("tag_rename_field")
    }

    @Test
    fun removingATagAsksForConfirmationAndCancelRemovesNothing() {
        val events = show(form())

        rule.onNodeWithTag("tag_remove_t1").performScrollTo().performClick()
        rule.onNodeWithText("¿Quitar la etiqueta?").assertIsDisplayed()
        rule.onNodeWithTag("tag_remove_cancel").performClick()
        assertTrue(events.removes.isEmpty())

        rule.onNodeWithTag("tag_remove_t1").performScrollTo().performClick()
        rule.onNodeWithTag("tag_remove_confirm").performClick()
        assertEquals(listOf("t1"), events.removes)
    }

    @Test
    fun removeButtonsAreAnnouncedWithTheTagName() {
        show(form())

        rule.onNodeWithContentDescription("Quitar Feria").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun anActiveTagWithItemsOffersToArchiveItAndAcceptingArchivesIt() {
        val events = show(form(event = CategoryEditEvent.OfferArchiveTag("t1")))

        rule.onNodeWithText("No se puede quitar").assertIsDisplayed()
        rule.onNodeWithText("Esta etiqueta tiene movimientos asociados. Puedes archivarla en su lugar; no podrás reactivarla.")
            .assertIsDisplayed()
        rule.onNodeWithTag("tag_offer_archive_confirm").performClick()

        assertEquals(1, events.consumed)
        assertEquals(listOf("t1"), events.tagArchives)
    }

    @Test
    fun anArchivedTagWithItemsOnlyInforms() {
        val events = show(form(event = CategoryEditEvent.TagCannotDelete))

        rule.onNodeWithText("Esta etiqueta archivada tiene movimientos asociados y no se puede eliminar.").assertIsDisplayed()
        absent("tag_offer_archive_confirm")
        rule.onNodeWithTag("tag_cannot_delete_confirm").performClick()

        assertEquals(1, events.consumed)
        assertTrue(events.tagArchives.isEmpty())
    }

    @Test
    fun tagNotFoundAndReorderFailedAreShownAsMessages() {
        show(form(event = CategoryEditEvent.TagNotFound))
        rule.waitForIdle()
        rule.onNodeWithText("Esta etiqueta ya no existe. Actualizamos la lista.").assertIsDisplayed()
    }

    @Test
    fun aFailedReorderIsShownAsAMessage() {
        show(form(event = CategoryEditEvent.ReorderFailed))
        rule.waitForIdle()
        rule.onNodeWithText("No se pudo guardar el nuevo orden. Se restauró el anterior.").assertIsDisplayed()
    }

    // --- Reordenar (CA-16) ---

    @Test
    fun dragHandlesAppearOnActiveTagsOnlyWhenThereIsMoreThanOne() {
        show(form())

        listOf("t1", "t2", "t3").forEach {
            rule.onNodeWithTag("tag_handle_$it", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        }
        rule.onNodeWithContentDescription("Arrastrar para reordenar Feria", useUnmergedTree = true).assertExists()
    }

    @Test
    fun noHandlesWhileAnOperationIsInProgress() {
        show(form(busy = true))

        absent("tag_handle_t1")
    }

    private fun customActionLabels(tag: String): List<String> =
        rule.onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions)
            ?.map { it.label }.orEmpty()

    private fun invokeCustomAction(tag: String, label: String) {
        // La acción se lee fuera del hilo de UI (las búsquedas sincronizan) y se ejecuta dentro de él.
        val action = rule.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsActions.CustomActions]
            .first { it.label == label }.action
        rule.runOnUiThread { action() }
    }

    @Test
    fun eachActiveTagExposesOnlyTheMoveActionsThatMakeSense() {
        show(form())

        assertEquals(listOf("Bajar"), customActionLabels("tag_row_t1"))
        assertEquals(listOf("Subir", "Bajar"), customActionLabels("tag_row_t2"))
        assertEquals(listOf("Subir"), customActionLabels("tag_row_t3"))
        assertEquals(emptyList<String>(), customActionLabels("tag_row_t4"))
    }

    @Test
    fun theMoveActionsAskForTheMovementWithoutDragging() {
        val events = show(form())

        invokeCustomAction("tag_row_t2", "Subir")
        invokeCustomAction("tag_row_t2", "Bajar")

        assertEquals(listOf("t2"), events.moveUps)
        assertEquals(listOf("t2"), events.moveDowns)
    }

    @Test
    fun noMoveActionsInReadOnly() {
        show(form(readOnly = true))

        assertEquals(emptyList<String>(), customActionLabels("tag_row_t2"))
    }

    /** Basta para superar el umbral de arrastre; ese primer tramo no cuenta en el desplazamiento medido. */
    private val SLOP = 60f

    // `getBoundsInRoot` recorta al área visible: la fila debe estar a la vista al medirla, o su alto sale 0.
    private fun dragStepPx(): Float {
        val bounds = rule.onNodeWithTag("tag_row_t1").getBoundsInRoot()
        return with(rule.density) { (bounds.bottom - bounds.top + 12.dp).toPx() }
    }

    /**
     * Lleva el asa de [handleTag] al centro de la pantalla: tras `performScrollTo` queda en el borde de la zona
     * visible y un arrastre saldría de ella. Con el formulario más largo (selector de color) ya no cabe sin desplazar.
     */
    private fun scrollHandleToTheMiddle(handleTag: String, extraScrollPx: Float = 500f) {
        rule.onNodeWithTag(handleTag, useUnmergedTree = true).performScrollTo()
        rule.onNodeWithTag("category_edit_form").performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, extraScrollPx) }
    }

    @Test
    fun draggingATagOneRowDownAsksToMoveItToTheNextPosition() {
        val events = show(form())
        scrollHandleToTheMiddle("tag_handle_t1")
        val step = dragStepPx()

        rule.onNodeWithTag("tag_handle_t1", useUnmergedTree = true).performScrollTo().performTouchInput {
            down(center)
            moveBy(Offset(0f, SLOP))
            moveBy(Offset(0f, step / 2))
            moveBy(Offset(0f, step / 2))
            up()
        }

        assertEquals(listOf("t1" to 1), events.moves)
    }

    @Test
    fun draggingATagTwoRowsUpMovesItToTheFirstPosition() {
        val events = show(form())
        scrollHandleToTheMiddle("tag_handle_t3")
        val step = dragStepPx()

        rule.onNodeWithTag("tag_handle_t3", useUnmergedTree = true).performScrollTo().performTouchInput {
            down(center)
            moveBy(Offset(0f, -SLOP))
            moveBy(Offset(0f, -step))
            moveBy(Offset(0f, -step))
            up()
        }

        assertEquals(listOf("t3" to 0), events.moves)
    }

    @Test
    fun aShortDragDoesNotChangeThePosition() {
        val events = show(form())
        scrollHandleToTheMiddle("tag_handle_t2")
        val step = dragStepPx()

        rule.onNodeWithTag("tag_handle_t2", useUnmergedTree = true).performScrollTo().performTouchInput {
            down(center)
            moveBy(Offset(0f, step * 0.3f))
            up()
        }

        assertTrue(events.moves.isEmpty())
    }

    @Test
    fun dragIsLimitedToTheActiveTags() {
        val events = show(form())
        // Más desplazamiento: el arrastre de 5 filas hacia abajo debe caber en la pantalla.
        scrollHandleToTheMiddle("tag_handle_t3", extraScrollPx = 1500f)
        val step = dragStepPx()

        rule.onNodeWithTag("tag_handle_t3", useUnmergedTree = true).performScrollTo().performTouchInput {
            down(center)
            moveBy(Offset(0f, step * 5))
            up()
        }

        // El último de los activos no baja más: se queda en su posición y no se pide nada.
        assertTrue(events.moves.isEmpty())
    }

    // --- Recrear la pantalla (CA-22) ---

    private var restoredState by mutableStateOf(form(isCreate = true, name = "", icon = null))

    @Test
    fun theTextAndTheChosenIconSurviveRecreatingTheScreen() {
        val restoration = StateRestorationTester(rule)
        val events = Events()
        restoration.setContent {
            FamilyFinAIAppTheme {
                // El estado vive fuera de la composición, como en el ViewModel, que sobrevive a la recreación.
                CategoryEditContent(
                    state = restoredState,
                    onLeave = {},
                    onNameChange = { restoredState = restoredState.copy(name = it) },
                    onIconChange = { restoredState = restoredState.copy(icon = it) },
                    onTypeChange = {},
                    onSubmit = {},
                    onArchive = {},
                    onDelete = {},
                    onEventConsumed = {},
                    onCreated = { events.created += it },
                )
            }
        }
        rule.onNodeWithTag("category_edit_name").performTextInput("Casa")
        rule.onNodeWithTag("category_icon_pets").performScrollTo().performClick()

        restoration.emulateSavedInstanceStateRestore()

        rule.onNodeWithText("Casa").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("category_icon_pets").performScrollTo().assertIsSelected()
    }

    @Test
    fun anOpenConfirmationDialogSurvivesRecreatingTheScreen() {
        val restoration = StateRestorationTester(rule)
        restoration.setContent { FamilyFinAIAppTheme { Host(form(), Events()) } }
        rule.onNodeWithTag("category_edit_archive").performScrollTo().performClick()
        rule.onNodeWithText("¿Archivar la categoría?").assertIsDisplayed()

        restoration.emulateSavedInstanceStateRestore()

        rule.onNodeWithText("¿Archivar la categoría?").assertIsDisplayed()
    }

    @Test
    fun anOpenRemoveTagDialogSurvivesRecreatingTheScreen() {
        val restoration = StateRestorationTester(rule)
        restoration.setContent { FamilyFinAIAppTheme { Host(form(), Events()) } }
        rule.onNodeWithTag("tag_remove_t1").performScrollTo().performClick()
        rule.onNodeWithText("¿Quitar la etiqueta?").assertIsDisplayed()

        restoration.emulateSavedInstanceStateRestore()

        rule.onNodeWithText("¿Quitar la etiqueta?").assertIsDisplayed()
    }
}

/** Si algún píxel de la imagen es casi exactamente [color] (los bordes suavizados no cuentan; el trazo pleno sí). */
internal fun androidx.compose.ui.graphics.ImageBitmap.containsColorNear(
    color: androidx.compose.ui.graphics.Color,
    tolerance: Int = 6,
): Boolean {
    val pixels = IntArray(width * height)
    readPixels(pixels, 0, 0, width, height)
    val target = color.toArgb()
    fun channel(value: Int, shift: Int) = (value shr shift) and 0xFF
    return pixels.any { px ->
        listOf(16, 8, 0).all { shift -> kotlin.math.abs(channel(px, shift) - channel(target, shift)) <= tolerance } &&
            channel(px, 24) == 0xFF
    }
}

