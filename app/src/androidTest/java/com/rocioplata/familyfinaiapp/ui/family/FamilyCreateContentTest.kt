package com.rocioplata.familyfinaiapp.ui.family

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.assertTouchWidthIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rocioplata.familyfinaiapp.domain.family.FamilyAccessFailure
import com.rocioplata.familyfinaiapp.ui.theme.FamilyFinAIAppTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FamilyCreateContentTest {
    @get:Rule
    val rule = createComposeRule()

    /** Los campos de estos tests toman el foco: el teclado no debe quedar abierto para el test siguiente de la suite. */
    @After
    fun hideKeyboard() = closeSoftKeyboard()

    /** Hace de ViewModel: el estado vive fuera de la composición, como en la app. */
    private class Holder(initial: FamilyCreateUiState = FamilyCreateUiState()) {
        var state by mutableStateOf(initial)
        var saves = 0
        var closes = 0
        var confirmed = 0
        var kept = 0
    }

    private fun show(holder: Holder, fontScale: Float? = null) {
        rule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale ?: base.fontScale)) {
                FamilyFinAIAppTheme { Host(holder) }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun Host(holder: Holder) {
        FamilyCreateContent(
            state = holder.state,
            onNameChange = { holder.state = holder.state.copy(text = it.take(60), error = null) },
            onSave = { holder.saves++ },
            onClose = { holder.closes++ },
            onConfirmDiscard = { holder.confirmed++ },
            onDismissDiscard = { holder.kept++; holder.state = holder.state.copy(confirmingDiscard = false) },
        )
    }

    /** Con el campo enfocado el teclado puede estar abierto y consumir el primer «atrás»: se repite hasta cumplirse. */
    private fun pressBackUntil(done: () -> Boolean) {
        repeat(3) {
            if (done()) return
            pressBack()
            rule.waitForIdle()
        }
    }

    // --- Contenido ---

    @Test
    fun showsTheTitleTheCounterAndTheTwoInfoTexts() {
        show(Holder())

        rule.onNodeWithText("Nueva familia").assertIsDisplayed()
        rule.onNodeWithTag("family_create_counter", useUnmergedTree = true).assertTextEquals("0 / 60")
        rule.onNodeWithTag("family_create_info_name")
            .assertTextEquals("Este nombre lo verán todas las personas que invites a la familia.")
        rule.onNodeWithTag("family_create_info_currency").assertTextEquals(
            "La familia se crea en pesos chilenos (CLP). Podrás cambiar la moneda en «Mi familia» antes de registrar el primer movimiento.",
        )
        rule.onAllNodesWithTag("family_create_error", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun theNameFieldHasTheFocusOnOpen() {
        show(Holder())

        rule.onNodeWithTag("family_create_name").assertIsFocused()
    }

    @Test
    fun typingUpdatesTheCounter() {
        val holder = Holder()
        show(holder)

        rule.onNodeWithTag("family_create_name").performTextInput("Casa de Campo")

        assertEquals("Casa de Campo", holder.state.text)
        rule.onNodeWithTag("family_create_counter", useUnmergedTree = true).assertTextEquals("13 / 60")
    }

    @Test
    fun theCounterReachesTheLimit() {
        show(Holder(FamilyCreateUiState(text = "a".repeat(60))))

        rule.onNodeWithTag("family_create_counter", useUnmergedTree = true).assertTextEquals("60 / 60")
    }

    @Test
    fun eachErrorShowsItsMessageNextToTheField() {
        val holder = Holder(FamilyCreateUiState(text = "Casa"))
        show(holder)
        val expected = listOf(
            FamilyCreateError.Required to "El nombre es obligatorio",
            FamilyCreateError.TooLong to "El nombre admite hasta 60 caracteres",
            FamilyCreateError.Emoji to "El nombre no puede llevar emojis",
            FamilyCreateError.Duplicate to "Ya perteneces a una familia con ese nombre",
            FamilyCreateError.Failure(FamilyAccessFailure.Offline) to "Esta acción requiere conexión. Lo que escribiste se conserva.",
            FamilyCreateError.Failure(FamilyAccessFailure.Network) to "No se pudo completar la acción. Revisa tu conexión e inténtalo de nuevo.",
            FamilyCreateError.Failure(FamilyAccessFailure.Unknown) to "Algo salió mal. Inténtalo de nuevo.",
        )

        expected.forEach { (error, message) ->
            holder.state = holder.state.copy(error = error)
            rule.waitForIdle()
            rule.onNodeWithTag("family_create_error", useUnmergedTree = true).assertTextEquals(message)
        }
        // El texto escrito se conserva con cada error.
        rule.onNodeWithTag("family_create_name").assert(hasText("Casa"))
    }

    // --- Acciones ---

    @Test
    fun theCreateButtonAsksToSave() {
        val holder = Holder(FamilyCreateUiState(text = "Casa"))
        show(holder)
        // El campo tiene el foco al abrir y su teclado puede tapar el botón en la ventana de pruebas.
        closeSoftKeyboard()
        rule.waitForIdle()

        rule.onNodeWithTag("family_create_save").assertIsEnabled().performClick()

        assertEquals(1, holder.saves)
    }

    @Test
    fun theKeyboardDoneActionAsksToSave() {
        val holder = Holder(FamilyCreateUiState(text = "Casa"))
        show(holder)

        rule.onNodeWithTag("family_create_name").performImeAction()

        assertEquals(1, holder.saves)
    }

    @Test
    fun whileSavingTheButtonAndTheFieldAreDisabled_andThereIsProgress() {
        show(Holder(FamilyCreateUiState(text = "Casa", isSaving = true)))

        rule.onNodeWithTag("family_create_save").assertIsNotEnabled()
        rule.onNodeWithTag("family_create_name").assertIsNotEnabled()
        rule.onNodeWithContentDescription("Creando la familia…").assertIsDisplayed()
        rule.onAllNodesWithTag("family_create_discard_confirm").assertCountEquals(0)
    }

    // --- Salir y descartar ---

    @Test
    fun theBackArrowAsksToClose() {
        val holder = Holder()
        show(holder)

        rule.onNodeWithTag("family_create_back").performClick()

        assertEquals(1, holder.closes)
    }

    @Test
    fun theSystemBackAsksToClose() {
        val holder = Holder()
        show(holder)

        pressBackUntil { holder.closes > 0 }

        assertEquals(1, holder.closes)
    }

    @Test
    fun theDiscardDialogOffersToLeaveOrKeepEditing() {
        val holder = Holder(FamilyCreateUiState(text = "Casa", confirmingDiscard = true))
        show(holder)

        rule.onNodeWithText("¿Salir sin crear la familia?").assertIsDisplayed()
        rule.onNodeWithText("Se perderá el nombre que escribiste.").assertIsDisplayed()

        rule.onNodeWithTag("family_create_discard_keep").performClick()
        assertEquals(1, holder.kept)
        rule.onAllNodesWithTag("family_create_discard_confirm").assertCountEquals(0)
        assertEquals("Casa", holder.state.text)
    }

    @Test
    fun confirmingTheDiscardAsksToLeave() {
        val holder = Holder(FamilyCreateUiState(text = "Casa", confirmingDiscard = true))
        show(holder)

        rule.onNodeWithTag("family_create_discard_confirm").performClick()

        assertEquals(1, holder.confirmed)
    }

    // --- Recrear la pantalla ---

    @Test
    fun theTypedTextTheErrorAndTheDiscardDialogSurviveRecreation() {
        val holder = Holder(FamilyCreateUiState(text = "Casa de Campo", error = FamilyCreateError.Duplicate, confirmingDiscard = true))
        val restoration = StateRestorationTester(rule)
        restoration.setContent { FamilyFinAIAppTheme { Host(holder) } }

        restoration.emulateSavedInstanceStateRestore()

        rule.onNodeWithTag("family_create_discard_keep").assertIsDisplayed()
        rule.onNodeWithTag("family_create_discard_keep").performClick()
        rule.onNodeWithTag("family_create_counter", useUnmergedTree = true).assertTextEquals("13 / 60")
        rule.onNodeWithTag("family_create_error", useUnmergedTree = true).assertTextEquals("Ya perteneces a una familia con ese nombre")
    }

    // --- Accesibilidad y tamaño ---

    @Test
    fun touchTargetsAreAtLeast48dp() {
        show(Holder())

        // El icono mide 40 dp pero el área táctil es de 48 dp.
        rule.onNodeWithTag("family_create_back").assertTouchWidthIsEqualTo(48.dp).assertTouchHeightIsEqualTo(48.dp)
        rule.onNodeWithTag("family_create_save").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun withTheLargestTextEverythingIsStillReachable() {
        show(Holder(FamilyCreateUiState(text = "Casa", error = FamilyCreateError.Failure(FamilyAccessFailure.Network))), fontScale = 2f)

        rule.onNodeWithTag("family_create_save").assertIsDisplayed()
        rule.onNodeWithTag("family_create_error", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("family_create_info_currency").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun onlyTheNameIsAsked_noCurrencyPickerNorBudget() {
        show(Holder())

        rule.onAllNodesWithTag("family_create_currency").assertCountEquals(0)
        rule.onAllNodesWithTag("family_create_budget").assertCountEquals(0)
    }
}
