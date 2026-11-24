package com.rocioplata.familyfinaiapp.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rocioplata.familyfinaiapp.domain.family.FamilyAccessFailure
import com.rocioplata.familyfinaiapp.domain.header.Family
import com.rocioplata.familyfinaiapp.domain.header.FamilyRole
import com.rocioplata.familyfinaiapp.ui.theme.FamilyFinAIAppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val SLOP = 60f

@RunWith(AndroidJUnit4::class)
class FamilySelectorContentTest {
    @get:Rule
    val rule = createComposeRule()

    private val plata = Family("f1", "Familia Plata", FamilyRole.OWNER, "CLP", memberCount = 4)
    private val casa = Family("f2", "Casa de Campo", FamilyRole.MEMBER, "CLP", memberCount = 2)
    private val vacaciones = Family("f3", "Proyecto Vacaciones", FamilyRole.OWNER, "CLP", memberCount = 1)
    private val three = listOf(plata, casa, vacaciones)

    private class Events {
        val selects = mutableListOf<String>()
        val moves = mutableListOf<Pair<String, Int>>()
        var manage = 0
        var create = 0
        var dismissed = 0
    }

    private fun show(
        families: List<Family> = three,
        active: String = "f1",
        error: FamilyAccessFailure? = null,
        reordering: Boolean = false,
        fontScale: Float? = null,
        asSheet: Boolean = false,
    ): Events {
        val events = Events()
        rule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale ?: base.fontScale)) {
                FamilyFinAIAppTheme {
                    if (asSheet) {
                        FamilySelectorSheet(
                            families = families, activeFamilyId = active, error = error, isReordering = reordering,
                            onSelect = { events.selects += it }, onMove = { id, to -> events.moves += id to to },
                            onManage = { events.manage++ }, onCreate = { events.create++ }, onDismiss = { events.dismissed++ },
                        )
                    } else {
                        Surface(modifier = Modifier.fillMaxSize()) {
                            Box {
                                FamilySelectorContent(
                                    families = families, activeFamilyId = active, error = error, isReordering = reordering,
                                    onSelect = { events.selects += it }, onMove = { id, to -> events.moves += id to to },
                                    onManage = { events.manage++ }, onCreate = { events.create++ },
                                )
                            }
                        }
                    }
                }
            }
        }
        return events
    }

    private fun customActionLabels(tag: String): List<String> =
        rule.onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions)
            ?.map { it.label }.orEmpty()

    private fun invokeCustomAction(tag: String, label: String) {
        val action = rule.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsActions.CustomActions]
            .first { it.label == label }.action
        rule.runOnUiThread { action() }
    }

    /** Lo que ocupa una fila más su separación, para arrastrar un número exacto de filas. */
    private fun stepPx(): Float {
        val row = rule.onNodeWithTag("family_row_f1").fetchSemanticsNode().size.height
        val spacing = rule.density.run { 4.dp.toPx() }
        return row + spacing
    }

    private fun drag(tag: String, rows: Float) {
        val step = stepPx()
        val sign = if (rows < 0) -1f else 1f
        rule.onNodeWithTag(tag).performTouchInput {
            down(center)
            moveBy(Offset(0f, sign * SLOP))
            moveBy(Offset(0f, rows * step / 2 - sign * SLOP / 2))
            moveBy(Offset(0f, rows * step / 2 - sign * SLOP / 2))
            up()
        }
    }

    // --- Datos ---

    @Test
    fun eachFamilyShowsNameRoleAndMembers_andTheActiveOneIsSelected() {
        show()

        rule.onNodeWithContentDescription("Familia Plata, Propietario, 4 miembros, seleccionada").assertIsDisplayed()
            .assertIsSelected()
        rule.onNodeWithContentDescription("Casa de Campo, Integrante, 2 miembros").assertIsDisplayed()
        rule.onNodeWithContentDescription("Proyecto Vacaciones, Propietario, 1 miembro").assertIsDisplayed()
        rule.onNodeWithText("Selecciona").assertDoesNotExist()
    }

    @Test
    fun withoutAMemberCountTheDescriptionOmitsIt() {
        show(families = listOf(Family("f1", "Familia Plata", FamilyRole.OWNER)))

        rule.onNodeWithContentDescription("Familia Plata, Propietario, seleccionada").assertIsDisplayed()
    }

    @Test
    fun aSingleFamilyOpensTheSelector_withManageAndCreate_butWithoutHandle() {
        show(families = listOf(plata))

        rule.onNodeWithTag("family_manage").assertIsDisplayed()
        rule.onNodeWithTag("family_selector_create").assertIsDisplayed()
        rule.onNodeWithTag("family_handle_f1").assertDoesNotExist()
        assertEquals(emptyList<String>(), customActionLabels("family_item_f1"))
    }

    @Test
    fun onlyTheActiveFamilyHasTheManageIcon_namedAfterIt() {
        show(active = "f2")

        rule.onAllNodesWithTag("family_manage").assertCountEquals(1)
        rule.onNodeWithContentDescription("Gestionar Casa de Campo").assertIsDisplayed()
    }

    // --- Acciones ---

    @Test
    fun choosingAFamilyAsksToSelectIt() {
        val events = show()

        rule.onNodeWithTag("family_item_f2").performClick()

        assertEquals(listOf("f2"), events.selects)
    }

    @Test
    fun manageAndCreateCallTheirCallbacks() {
        val events = show()

        rule.onNodeWithTag("family_manage").performClick()
        rule.onNodeWithTag("family_selector_create").performClick()

        assertEquals(1, events.manage)
        assertEquals(1, events.create)
        assertEquals(emptyList<String>(), events.selects)
    }

    // --- Reordenar ---

    @Test
    fun draggingTheHandleOneRowDownAsksToMoveTheFamilyToTheNextPosition() {
        val events = show()

        drag("family_handle_f1", rows = 1f)

        assertEquals(listOf("f1" to 1), events.moves)
    }

    @Test
    fun draggingTwoRowsUpMovesItToTheFirstPosition() {
        val events = show()

        drag("family_handle_f3", rows = -2f)

        assertEquals(listOf("f3" to 0), events.moves)
    }

    @Test
    fun draggingBeyondTheEndsClampsToThePositions() {
        val events = show()

        drag("family_handle_f1", rows = 8f)

        assertEquals(listOf("f1" to 2), events.moves)
    }

    @Test
    fun aDragThatReturnsToItsPlaceAsksForNothing() {
        val events = show()

        // El arrastre empieza al superar el umbral (primer movimiento): se baja casi una fila y se vuelve al punto de partida.
        rule.onNodeWithTag("family_handle_f2").performTouchInput {
            down(center)
            moveBy(Offset(0f, SLOP / 2))
            moveBy(Offset(0f, SLOP * 2))
            moveBy(Offset(0f, -SLOP * 2))
            moveBy(Offset(0f, -SLOP / 2))
            up()
        }

        assertEquals(emptyList<Pair<String, Int>>(), events.moves)
    }

    @Test
    fun eachFamilyExposesOnlyTheMoveActionsThatMakeSense() {
        show()

        assertEquals(listOf("Bajar en la lista"), customActionLabels("family_item_f1"))
        assertEquals(listOf("Subir en la lista", "Bajar en la lista"), customActionLabels("family_item_f2"))
        assertEquals(listOf("Subir en la lista"), customActionLabels("family_item_f3"))
    }

    @Test
    fun theMoveActionsAskToMoveOnePosition() {
        val events = show()

        invokeCustomAction("family_item_f2", "Subir en la lista")
        invokeCustomAction("family_item_f2", "Bajar en la lista")

        assertEquals(listOf("f2" to 0, "f2" to 2), events.moves)
    }

    @Test
    fun whileSavingAnOrderNeitherTheHandlesNorTheMoveActionsWork() {
        val events = show(reordering = true)

        drag("family_handle_f1", rows = 1f)

        assertEquals(emptyList<Pair<String, Int>>(), events.moves)
        assertEquals(emptyList<String>(), customActionLabels("family_item_f2"))
    }

    @Test
    fun theHandleIsNotFocusedByTheScreenReader() {
        show()

        // Sin descripción ni acciones propias: el lector usa las acciones de la fila.
        val config = rule.onNodeWithTag("family_handle_f1").fetchSemanticsNode().config
        assertFalse(config.contains(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription))
        assertFalse(config.contains(SemanticsActions.OnClick))
    }

    // --- Error ---

    @Test
    fun aFailedOrderShowsItsMessageInline() {
        show(error = FamilyAccessFailure.Network)

        rule.onNodeWithTag("family_selector_error").assertIsDisplayed()
        rule.onNodeWithText("No se pudo guardar el orden. Se mantiene el anterior; inténtalo de nuevo.").assertIsDisplayed()
    }

    @Test
    fun theMessageTellsOfflineAndChangedFamiliesApart() {
        show(error = FamilyAccessFailure.Offline)
        rule.onNodeWithText("Reordenar las familias requiere conexión. Se mantiene el orden anterior.").assertIsDisplayed()
    }

    @Test
    fun withoutErrorThereIsNoMessage() {
        show()

        rule.onNodeWithTag("family_selector_error").assertDoesNotExist()
    }

    // --- Accesibilidad y tamaño ---

    @Test
    fun touchTargetsAreAtLeast48dp() {
        show()

        rule.onNodeWithTag("family_manage").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        rule.onNodeWithTag("family_handle_f1").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        rule.onNodeWithTag("family_selector_create").assertHeightIsAtLeast(48.dp)
        rule.onNodeWithTag("family_item_f1").assertHeightIsAtLeast(48.dp)
        rule.onNodeWithTag("family_item_f3").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun withTheLargestTextAndALongNameEverythingStaysReachable() {
        val long = Family("f1", "Familia de los González Fernández de la Torre y de los Pérez", FamilyRole.OWNER, "CLP", 12)
        show(families = listOf(long, casa, vacaciones), fontScale = 2f, error = FamilyAccessFailure.Network)

        rule.onNodeWithTag("family_manage").assertIsDisplayed()
        rule.onNodeWithTag("family_handle_f1").assertIsDisplayed()
        rule.onNodeWithTag("family_selector_create").assertIsDisplayed()
        rule.onNodeWithTag("family_selector_error").assertIsDisplayed()
    }

    // --- En la hoja real ---

    @Test
    fun theSheetShowsTheListAndCreateButton() {
        show(asSheet = true)

        rule.onNodeWithTag("family_selector_list").assertIsDisplayed()
        rule.onNodeWithTag("family_selector_create").assertIsDisplayed()
    }

    @Test
    fun draggingAHandleInsideTheSheetMovesTheFamilyWithoutClosingTheSheet() {
        val events = show(asSheet = true)

        drag("family_handle_f1", rows = 1f)
        rule.waitForIdle()

        assertEquals(listOf("f1" to 1), events.moves)
        assertEquals(0, events.dismissed)
        rule.onNodeWithTag("family_selector_list").assertIsDisplayed()
    }

    @Test
    fun aLongDragDownInsideTheSheetDoesNotCloseIt() {
        val events = show(asSheet = true)

        drag("family_handle_f1", rows = 8f)
        rule.waitForIdle()

        assertEquals(0, events.dismissed)
        assertTrue(events.moves.isNotEmpty())
        rule.onNodeWithTag("family_selector_create").assertIsDisplayed()
    }
}
