package com.rocioplata.familyfinaiapp.ui.paymentmethods

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethod
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethodStatus
import com.rocioplata.familyfinaiapp.ui.theme.FamilyFinAIAppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PaymentMethodsContentTest {
    @get:Rule
    val rule = createComposeRule()

    /** Lo que la pantalla le pidió al ViewModel. */
    private class Events {
        var backs = 0
        var retries = 0
        var toggles = 0
    }

    private fun method(id: String, name: String, status: PaymentMethodStatus = PaymentMethodStatus.ACTIVE) =
        PaymentMethod(id, name, status)

    private val efectivo = method("m1", "Efectivo")
    private val transferencia = method("m2", "Transferencia")
    private val credito = method("m3", "Tarjeta de Crédito")
    private val cheque = method("m4", "Cheque", PaymentMethodStatus.DEPRECATED)
    private val vieja = method("m5", "Tarjeta vieja", PaymentMethodStatus.DEPRECATED)

    private fun content(
        active: List<PaymentMethod> = emptyList(),
        archived: List<PaymentMethod> = emptyList(),
        expanded: Boolean = false,
        isStale: Boolean = false,
    ) = PaymentMethodsUiState.Content(
        active = active, archived = archived, archivedExpanded = expanded, isStale = isStale, isRefreshing = false,
    )

    /** Host mínimo que reproduce lo que haría el ViewModel con la sección plegable, sin repositorio ni red. */
    @Composable
    private fun Host(initial: PaymentMethodsUiState, events: Events) {
        var state by remember { mutableStateOf(initial) }
        PaymentMethodsContent(
            state = state,
            onBack = { events.backs++ },
            onRetry = { events.retries++ },
            onToggleArchived = {
                events.toggles++
                (state as? PaymentMethodsUiState.Content)?.let { state = it.copy(archivedExpanded = !it.archivedExpanded) }
            },
        )
    }

    private fun show(initial: PaymentMethodsUiState, events: Events = Events()): Events {
        rule.setContent { FamilyFinAIAppTheme { Host(initial, events) } }
        return events
    }

    // --- Listado (CA-03) ---

    @Test
    fun showsTheTitleAndTheActiveMethodsInTheOrderReceived() {
        show(content(active = listOf(transferencia, efectivo, credito)))

        rule.onNodeWithTag("payment_methods_screen").assertIsDisplayed()
        rule.onNodeWithText("Medios de pago").assertIsDisplayed()
        listOf("Transferencia", "Efectivo", "Tarjeta de Crédito").forEach {
            rule.onNodeWithText(it).assertIsDisplayed()
        }
        val tops = listOf("m2", "m1", "m3").map {
            rule.onNodeWithTag("payment_method_row_$it").fetchSemanticsNode().positionInRoot.y
        }
        assertTrue("Las filas deben seguir el orden recibido", tops == tops.sorted())
        rule.onAllNodesWithText("Archivado").assertCountEquals(0)
    }

    @Test
    fun longNamesAreTruncatedWithoutBreakingTheRow() {
        val long = method("m9", "Tarjeta de Crédito Premium Platino Internacional con un nombre extraordinariamente largo")
        show(content(active = listOf(long, efectivo)))

        rule.onNodeWithTag("payment_method_row_m9").assertIsDisplayed()
        rule.onNodeWithTag("payment_method_row_m1").assertIsDisplayed()
    }

    @Test
    fun thePaymentMethodsBackArrowReturnsAndHasATouchTargetOfAtLeast48dp() {
        val events = show(content(active = listOf(efectivo)))

        rule.onNodeWithTag("payment_methods_back").assertTouchHeightIsEqualTo(48.dp)
        rule.onNodeWithContentDescription("Volver").performClick()

        assertEquals(1, events.backs)
    }

    // --- Archivados (CA-04) ---

    @Test
    fun archivedSectionStartsCollapsedWithItsCountAndHidesTheRows() {
        show(content(active = listOf(efectivo), archived = listOf(cheque, vieja)))

        rule.onNodeWithText("Medios de pago archivados").assertIsDisplayed()
        rule.onNodeWithTag("payment_methods_archived_header", useUnmergedTree = true)
            .assert(hasAnyDescendant(hasText("2")))
        rule.onAllNodesWithText("Cheque").assertCountEquals(0)
        rule.onAllNodesWithText("Archivado").assertCountEquals(0)
    }

    @Test
    fun expandingTheArchivedSectionShowsEachRowMarkedAsArchivedAndCollapsingHidesThem() {
        val events = show(content(active = listOf(efectivo), archived = listOf(cheque, vieja)))

        rule.onNodeWithTag("payment_methods_archived_header").performClick()

        assertEquals(1, events.toggles)
        rule.onNodeWithText("Cheque").assertIsDisplayed()
        rule.onNodeWithText("Tarjeta vieja").assertIsDisplayed()
        rule.onAllNodesWithText("Archivado").assertCountEquals(2)

        rule.onNodeWithTag("payment_methods_archived_header").performClick()

        rule.onAllNodesWithText("Cheque").assertCountEquals(0)
    }

    @Test
    fun withoutArchivedMethodsTheSectionIsNotShown() {
        show(content(active = listOf(efectivo)))

        rule.onAllNodesWithTag("payment_methods_archived_header").assertCountEquals(0)
        rule.onAllNodesWithText("Medios de pago archivados").assertCountEquals(0)
    }

    @Test
    fun theArchivedHeaderHasATouchTargetOfAtLeast48dp() {
        show(content(active = listOf(efectivo), archived = listOf(cheque)))

        rule.onNodeWithTag("payment_methods_archived_header").assertTouchHeightIsEqualTo(48.dp)
    }

    // --- Estados ---

    @Test
    fun loadingStateShowsTheProgressAndNoList() {
        show(PaymentMethodsUiState.Loading)

        rule.onNodeWithTag("payment_methods_loading").assertIsDisplayed()
        rule.onNodeWithText("Cargando medios de pago…").assertIsDisplayed()
        rule.onAllNodesWithTag("payment_methods_list").assertCountEquals(0)
    }

    @Test
    fun errorStateShowsTheMessageAndRetryAsksToReload() {
        val events = show(PaymentMethodsUiState.Error)

        rule.onNodeWithTag("payment_methods_error").assertIsDisplayed()
        rule.onNodeWithText("No se pudieron cargar los medios de pago.").assertIsDisplayed()
        rule.onNodeWithTag("payment_methods_retry").performClick()

        assertEquals(1, events.retries)
    }

    @Test
    fun emptyStateShowsTheMessage_andTheArchivedStillShow() {
        show(content(active = emptyList(), archived = listOf(cheque)))

        rule.onNodeWithTag("payment_methods_empty").assertIsDisplayed()
        rule.onNodeWithText("Aún no hay medios de pago.").assertIsDisplayed()
        rule.onNodeWithTag("payment_methods_archived_header").assertIsDisplayed()
    }

    @Test
    fun withActiveMethodsThereIsNoEmptyMessage() {
        show(content(active = listOf(efectivo)))

        rule.onAllNodesWithTag("payment_methods_empty").assertCountEquals(0)
    }

    // --- Datos antiguos (CA-15) ---

    @Test
    fun staleDataShowsTheBannerAndFreshDataDoesNot() {
        show(content(active = listOf(efectivo), isStale = true))
        rule.onNodeWithText("Los datos podrían estar desactualizados. Conéctate para actualizarlos.").assertIsDisplayed()
    }

    @Test
    fun freshDataShowsNoBanner() {
        show(content(active = listOf(efectivo), isStale = false))
        rule.onAllNodesWithText("Los datos podrían estar desactualizados. Conéctate para actualizarlos.")
            .assertCountEquals(0)
    }

    // --- Recreación ---

    @Test
    fun theListSurvivesRecreation() {
        val restoration = StateRestorationTester(rule)
        restoration.setContent {
            FamilyFinAIAppTheme { Host(content(active = listOf(efectivo), archived = listOf(cheque)), Events()) }
        }

        restoration.emulateSavedInstanceStateRestore()

        rule.onNodeWithText("Efectivo").assertIsDisplayed()
        rule.onNodeWithText("Medios de pago archivados").assertIsDisplayed()
    }
}
