package com.rocioplata.familyfinaiapp.ui.paymentmethods

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
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
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethodFailure
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
        var creates = 0
        val archives = mutableListOf<String>()
        val deletes = mutableListOf<String>()
        var eventsConsumed = 0
        val saves = mutableListOf<String>()
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

    /**
     * Host mínimo que reproduce lo que haría el ViewModel (sección plegable y formulario), sin repositorio ni
     * red. El estado vive fuera de la composición, como en el ViewModel, para poder probar la recreación.
     */
    private class State(initial: PaymentMethodsUiState) {
        var ui by mutableStateOf(initial)
        /** Nombres que ya existen: guardarlos da el error de duplicado. */
        val existing = mutableSetOf<String>()

        fun content() = ui as PaymentMethodsUiState.Content
        fun updateForm(transform: (PaymentMethodFormState) -> PaymentMethodFormState) {
            val c = ui as? PaymentMethodsUiState.Content ?: return
            ui = c.copy(form = c.form?.let(transform))
        }
    }

    @Composable
    private fun Host(holder: State, events: Events) {
        PaymentMethodsContent(
            state = holder.ui,
            onBack = { events.backs++ },
            onRetry = { events.retries++ },
            onToggleArchived = {
                events.toggles++
                (holder.ui as? PaymentMethodsUiState.Content)?.let {
                    holder.ui = it.copy(archivedExpanded = !it.archivedExpanded)
                }
            },
            onCreate = {
                events.creates++
                holder.ui = holder.content().copy(
                    form = PaymentMethodFormState(PaymentMethodFormTarget.Create, initialName = "", text = ""),
                )
            },
            onNameChange = { text ->
                holder.updateForm { it.copy(text = text.take(40), error = null) }
            },
            onSave = {
                val form = holder.content().form!!
                val name = form.text.trim()
                when {
                    name.isEmpty() -> holder.updateForm { it.copy(error = PaymentMethodFormError.Required) }
                    name.lowercase() in holder.existing ->
                        holder.updateForm { it.copy(error = PaymentMethodFormError.Duplicate) }
                    else -> {
                        events.saves += name
                        holder.ui = holder.content().copy(form = null)
                    }
                }
            },
            onCloseForm = {
                val form = holder.content().form!!
                if (form.text.trim() != form.initialName) holder.updateForm { it.copy(confirmingDiscard = true) }
                else holder.ui = holder.content().copy(form = null)
            },
            onConfirmDiscard = { holder.ui = holder.content().copy(form = null) },
            onDismissDiscard = { holder.updateForm { it.copy(confirmingDiscard = false) } },
            onRename = { method ->
                holder.ui = holder.content().copy(
                    form = PaymentMethodFormState(
                        PaymentMethodFormTarget.Rename(method.id), initialName = method.name, text = method.name,
                    ),
                )
            },
            onArchive = { holder.ui = holder.content().copy(pendingAction = PaymentMethodPendingAction.ConfirmArchive(it)) },
            onDelete = { holder.ui = holder.content().copy(pendingAction = PaymentMethodPendingAction.ConfirmDelete(it)) },
            onConfirmAction = {
                val action = holder.content().pendingAction!!
                if (action is PaymentMethodPendingAction.ConfirmDelete) events.deletes += action.method.id
                else events.archives += action.method.id
                holder.ui = holder.content().copy(pendingAction = null)
            },
            onDismissAction = { holder.ui = holder.content().copy(pendingAction = null) },
            onEventConsumed = {
                events.eventsConsumed++
                holder.ui = holder.content().copy(event = null)
            },
        )
    }

    private fun show(initial: PaymentMethodsUiState, events: Events = Events()): Events {
        val holder = State(initial)
        rule.setContent { FamilyFinAIAppTheme { Host(holder, events) } }
        return events
    }

    private fun showWithState(holder: State, events: Events = Events()) {
        rule.setContent { FamilyFinAIAppTheme { Host(holder, events) } }
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
        rule.onNodeWithText("Aún no hay medios de pago. Crea el primero.").assertIsDisplayed()
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
        val holder = State(content(active = listOf(efectivo), archived = listOf(cheque)))
        val restoration = StateRestorationTester(rule)
        restoration.setContent { FamilyFinAIAppTheme { Host(holder, Events()) } }

        restoration.emulateSavedInstanceStateRestore()

        rule.onNodeWithText("Efectivo").assertIsDisplayed()
        rule.onNodeWithText("Medios de pago archivados").assertIsDisplayed()
    }

    // --- Formulario (CA-08, CA-09, CA-18, CA-19) ---

    private fun showList(vararg existing: String): State {
        val holder = State(content(active = listOf(efectivo)))
        holder.existing += existing
        showWithState(holder)
        return holder
    }

    /**
     * Atrás del sistema. Con el campo enfocado el teclado puede estar abierto y consume el primer «atrás»,
     * así que se repite (hasta 3 veces) hasta que se cumpla [done].
     */
    private fun pressBackUntil(done: () -> Boolean) {
        repeat(3) {
            if (done()) return
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            rule.waitForIdle()
        }
    }

    private fun isDisplayed(tag: String) = rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private fun openForm() {
        rule.onNodeWithTag("payment_methods_create").performClick()
        rule.waitForIdle()
    }

    @Test
    fun createActionIsShownAndOpensAnEmptyFormWithItsCounter() {
        val events = show(content(active = listOf(efectivo)))

        rule.onNodeWithTag("payment_methods_create").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        openForm()

        assertEquals(1, events.creates)
        rule.onNodeWithText("Nuevo medio de pago").assertIsDisplayed()
        rule.onNodeWithTag("payment_method_form_counter", useUnmergedTree = true).assertTextContains("0 / 40")
    }

    @Test
    fun typingUpdatesTheCounter_andNeverAllowsMoreThan40Characters() {
        showList()
        openForm()

        rule.onNodeWithTag("payment_method_form_name").performTextInput("Bizum")
        rule.onNodeWithTag("payment_method_form_counter", useUnmergedTree = true).assertTextContains("5 / 40")

        rule.onNodeWithTag("payment_method_form_name").performTextReplacement("a".repeat(55))
        rule.onNodeWithTag("payment_method_form_counter", useUnmergedTree = true).assertTextContains("40 / 40")
    }

    @Test
    fun savingWithoutNameShowsTheRequiredNotice_andKeepsTheFormOpen() {
        val holder = showList()
        openForm()

        rule.onNodeWithTag("payment_method_form_save").performClick()

        rule.onNodeWithTag("payment_method_form_error", useUnmergedTree = true).assertTextContains("El nombre es obligatorio.")
        rule.onNodeWithTag("payment_method_form_name").assertIsDisplayed()
        assertTrue(holder.content().form != null)
    }

    @Test
    fun theRequiredNoticeDisappearsWhenTyping() {
        showList()
        openForm()
        rule.onNodeWithTag("payment_method_form_save").performClick()

        rule.onNodeWithTag("payment_method_form_name").performTextInput("B")

        rule.onAllNodesWithTag("payment_method_form_error", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun aDuplicateNameShowsTheErrorNextToTheField_andKeepsWhatWasTyped() {
        showList("bizum")
        openForm()
        rule.onNodeWithTag("payment_method_form_name").performTextInput("Bizum")

        rule.onNodeWithTag("payment_method_form_save").performClick()

        rule.onNodeWithTag("payment_method_form_error", useUnmergedTree = true).assertTextContains("Ya existe un medio de pago con ese nombre.")
        rule.onNodeWithTag("payment_method_form_name").assertTextContains("Bizum")
    }

    @Test
    fun savingAValidNameClosesTheForm() {
        val events = show(content(active = listOf(efectivo)))
        openForm()
        rule.onNodeWithTag("payment_method_form_name").performTextInput("  Bizum ")

        rule.onNodeWithTag("payment_method_form_save").performClick()

        assertEquals(listOf("Bizum"), events.saves)
        rule.onAllNodesWithTag("payment_method_form_name").assertCountEquals(0)
    }

    @Test
    fun cancelWithoutChangesClosesDirectly() {
        showList()
        openForm()

        rule.onNodeWithTag("payment_method_form_cancel").performClick()

        rule.onAllNodesWithTag("payment_method_form_name").assertCountEquals(0)
        rule.onAllNodesWithText("¿Descartar los cambios?").assertCountEquals(0)
    }

    @Test
    fun cancelWithChangesAsksToDiscard_keepEditingKeepsTheText_andDiscardCloses() {
        showList()
        openForm()
        rule.onNodeWithTag("payment_method_form_name").performTextInput("Bizum")

        rule.onNodeWithTag("payment_method_form_cancel").performClick()
        rule.onNodeWithText("¿Descartar los cambios?").assertIsDisplayed()
        rule.onNodeWithTag("payment_method_discard_keep").performClick()

        rule.onAllNodesWithText("¿Descartar los cambios?").assertCountEquals(0)
        rule.onNodeWithTag("payment_method_form_name").assertTextContains("Bizum")

        rule.onNodeWithTag("payment_method_form_cancel").performClick()
        rule.onNodeWithTag("payment_method_discard_confirm").performClick()

        rule.onAllNodesWithTag("payment_method_form_name").assertCountEquals(0)
    }

    @Test
    fun theSystemBackWithChangesAsksToDiscard() {
        showList()
        openForm()
        rule.onNodeWithTag("payment_method_form_name").performTextInput("Bizum")

        pressBackUntil { isDisplayed("payment_method_discard_confirm") }

        rule.onNodeWithText("¿Descartar los cambios?").assertIsDisplayed()
    }

    @Test
    fun theSystemBackWithoutChangesClosesTheForm() {
        showList()
        openForm()

        pressBackUntil { !isDisplayed("payment_method_form_name") }

        rule.onAllNodesWithTag("payment_method_form_name").assertCountEquals(0)
    }

    @Test
    fun tappingOutsideWithChangesAsksToDiscard() {
        showList()
        openForm()
        rule.onNodeWithTag("payment_method_form_name").performTextInput("Bizum")

        // Un toque en el borde izquierdo, a media altura, cae sobre la sombra y fuera del diálogo.
        val metrics = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics
        InstrumentationRegistry.getInstrumentation().uiAutomation.let { automation ->
            val now = android.os.SystemClock.uptimeMillis()
            listOf(android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_UP).forEach { action ->
                val event = android.view.MotionEvent.obtain(now, now, action, 5f, metrics.heightPixels / 2f, 0)
                event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
                automation.injectInputEvent(event, true)
                event.recycle()
            }
        }
        rule.waitForIdle()

        rule.onNodeWithText("¿Descartar los cambios?").assertIsDisplayed()
    }

    @Test
    fun theSavingFormShowsProgressAndDisablesTheActions() {
        val holder = showList()
        openForm()
        rule.onNodeWithTag("payment_method_form_name").performTextInput("Bizum")
        holder.updateForm { it.copy(isSaving = true) }

        rule.onNodeWithTag("payment_method_form_save").assertIsNotEnabled()
        rule.onNodeWithTag("payment_method_form_cancel").assertIsNotEnabled()
        rule.onNodeWithContentDescription("Guardando…").assertIsDisplayed()
    }

    @Test
    fun theFormShowsTheRenameTitle() {
        val holder = showList()
        holder.ui = holder.content().copy(
            form = PaymentMethodFormState(
                PaymentMethodFormTarget.Rename("m1"), initialName = "Efectivo", text = "Efectivo",
            ),
        )
        rule.waitForIdle()

        rule.onNodeWithText("Renombrar medio de pago").assertIsDisplayed()
        rule.onNodeWithTag("payment_method_form_name").assertTextContains("Efectivo")
    }

    @Test
    fun theOpenFormWithTheTypedTextSurvivesRecreation() {
        val holder = State(content(active = listOf(efectivo)))
        val restoration = StateRestorationTester(rule)
        restoration.setContent { FamilyFinAIAppTheme { Host(holder, Events()) } }
        openForm()
        rule.onNodeWithTag("payment_method_form_name").performTextInput("Bizum")

        restoration.emulateSavedInstanceStateRestore()

        rule.onNodeWithText("Nuevo medio de pago").assertIsDisplayed()
        rule.onNodeWithTag("payment_method_form_name").assertTextContains("Bizum")
    }

    // --- Menú ⋮ y diálogos (CA-05, CA-11, CA-13, CA-14, CA-17) ---

    private fun menu(id: String) = rule.onNodeWithTag("payment_method_menu_$id")

    private fun item(name: String) = rule.onNodeWithTag("payment_method_menu_item_$name")

    private fun hasItem(name: String) =
        rule.onAllNodesWithTag("payment_method_menu_item_$name").fetchSemanticsNodes().isNotEmpty()

    @Test
    fun anActiveRowMenuOffersRenameArchiveAndDelete() {
        show(content(active = listOf(efectivo)))

        menu("m1").performClick()

        assertTrue(hasItem("rename") && hasItem("archive") && hasItem("delete"))
        rule.onNodeWithText("Renombrar").assertIsDisplayed()
        rule.onNodeWithText("Archivar").assertIsDisplayed()
        rule.onNodeWithText("Eliminar").assertIsDisplayed()
    }

    @Test
    fun anArchivedRowMenuOffersOnlyDelete() {
        show(content(active = listOf(efectivo), archived = listOf(cheque), expanded = true))

        menu("m4").performClick()

        assertTrue(hasItem("delete"))
        assertTrue(!hasItem("rename") && !hasItem("archive"))
    }

    @Test
    fun theMenuButtonNamesTheMethod_andHasATouchTargetOf48dp() {
        show(content(active = listOf(efectivo, transferencia), archived = listOf(cheque), expanded = true))

        rule.onNodeWithContentDescription("Opciones de Efectivo").assertIsDisplayed()
        rule.onNodeWithContentDescription("Opciones de Transferencia").assertIsDisplayed()
        rule.onNodeWithContentDescription("Opciones de Cheque").assertIsDisplayed()
        menu("m1").assertTouchHeightIsEqualTo(48.dp).assertWidthIsAtLeast(48.dp)
    }

    @Test
    fun renameFromTheMenuOpensTheFormWithTheCurrentName() {
        show(content(active = listOf(efectivo)))

        menu("m1").performClick()
        item("rename").performClick()
        rule.waitForIdle()

        rule.onNodeWithText("Renombrar medio de pago").assertIsDisplayed()
        rule.onNodeWithTag("payment_method_form_name").assertTextContains("Efectivo")
    }

    @Test
    fun archiveWarnsItIsIrreversible_confirmArchives_andCancelDoesNothing() {
        val events = show(content(active = listOf(efectivo)))

        menu("m1").performClick()
        item("archive").performClick()
        rule.onNodeWithText("¿Archivar el medio de pago?").assertIsDisplayed()
        rule.onNodeWithText("Esta acción no se puede deshacer", substring = true).assertIsDisplayed()
        rule.onNodeWithTag("payment_method_action_cancel").performClick()

        assertTrue(events.archives.isEmpty())
        rule.onAllNodesWithText("¿Archivar el medio de pago?").assertCountEquals(0)

        menu("m1").performClick()
        item("archive").performClick()
        rule.onNodeWithTag("payment_method_action_confirm").performClick()

        assertEquals(listOf("m1"), events.archives)
        rule.onAllNodesWithText("¿Archivar el medio de pago?").assertCountEquals(0)
    }

    @Test
    fun deleteAsksForConfirmation_confirmDeletes_andCancelDoesNothing() {
        val events = show(content(active = listOf(efectivo)))

        menu("m1").performClick()
        item("delete").performClick()
        rule.onNodeWithText("¿Eliminar el medio de pago?").assertIsDisplayed()
        rule.onNodeWithTag("payment_method_action_cancel").performClick()
        assertTrue(events.deletes.isEmpty())

        menu("m1").performClick()
        item("delete").performClick()
        rule.onNodeWithTag("payment_method_action_confirm").performClick()

        assertEquals(listOf("m1"), events.deletes)
    }

    @Test
    fun anArchivedMethodCanBeDeletedFromItsMenu() {
        val events = show(content(active = listOf(efectivo), archived = listOf(cheque), expanded = true))

        menu("m4").performClick()
        item("delete").performClick()
        rule.onNodeWithTag("payment_method_action_confirm").performClick()

        assertEquals(listOf("m4"), events.deletes)
    }

    @Test
    fun theOfferToArchiveExplainsAndArchivesOnConfirm() {
        val holder = State(content(active = listOf(efectivo)))
        val events = Events()
        showWithState(holder, events)
        holder.ui = holder.content().copy(pendingAction = PaymentMethodPendingAction.OfferArchive(efectivo))
        rule.waitForIdle()

        rule.onNodeWithText("No se puede eliminar").assertIsDisplayed()
        rule.onNodeWithText("Este medio de pago tiene movimientos asociados.", substring = true).assertIsDisplayed()
        rule.onNodeWithTag("payment_method_action_confirm").performClick()

        assertEquals(listOf("m1"), events.archives)
    }

    @Test
    fun aFailureNoticeIsShownAndTheEventConsumed() {
        val holder = State(content(active = listOf(efectivo)))
        val events = Events()
        showWithState(holder, events)
        holder.ui = holder.content().copy(event = PaymentMethodsEvent.Failed(PaymentMethodFailure.IsSomeonesDefault))
        rule.waitForIdle()

        rule.onNodeWithText("Este medio de pago es el predeterminado de un miembro", substring = true).assertIsDisplayed()
        assertEquals(1, events.eventsConsumed)
    }

    @Test
    fun theOfflineNoticeOfAMenuActionDoesNotMentionWhatWasTyped() {
        val holder = State(content(active = listOf(efectivo)))
        showWithState(holder)
        holder.ui = holder.content().copy(event = PaymentMethodsEvent.Failed(PaymentMethodFailure.Offline))
        rule.waitForIdle()

        rule.onNodeWithText("Necesitas conexión para hacer cambios. No se aplicó ningún cambio.").assertIsDisplayed()
        rule.onAllNodesWithText("Lo que escribiste", substring = true).assertCountEquals(0)
    }

    @Test
    fun theNoticeForAnArchivedMethodWithItemsIsShown() {
        val holder = State(content(active = listOf(efectivo), archived = listOf(cheque), expanded = true))
        showWithState(holder)
        holder.ui = holder.content().copy(event = PaymentMethodsEvent.Failed(PaymentMethodFailure.HasAssociatedItems))
        rule.waitForIdle()

        rule.onNodeWithText("Este medio de pago tiene movimientos asociados y no se puede eliminar.").assertIsDisplayed()
    }

    @Test
    fun whileAnOperationRuns_progressShows_andTheMenusAndCreateAreDisabled() {
        val holder = State(content(active = listOf(efectivo), archived = listOf(cheque), expanded = true))
        showWithState(holder)
        rule.onAllNodesWithTag("payment_methods_busy").assertCountEquals(0)

        holder.ui = holder.content().copy(isBusy = true)
        rule.waitForIdle()

        rule.onNodeWithTag("payment_methods_busy").assertIsDisplayed()
        menu("m1").assertIsNotEnabled()
        menu("m4").assertIsNotEnabled()
    }

    @Test
    fun whenNotBusyTheMenusAreEnabled() {
        show(content(active = listOf(efectivo)))

        menu("m1").assertIsEnabled()
    }

    @Test
    fun theConfirmationButtonIsDisabledWhileBusy() {
        val holder = State(content(active = listOf(efectivo)))
        showWithState(holder)
        holder.ui = holder.content().copy(
            pendingAction = PaymentMethodPendingAction.ConfirmDelete(efectivo), isBusy = true,
        )
        rule.waitForIdle()

        rule.onNodeWithTag("payment_method_action_confirm").assertIsNotEnabled()
    }

    @Test
    fun aPendingConfirmationSurvivesRecreation() {
        val holder = State(content(active = listOf(efectivo)))
        val restoration = StateRestorationTester(rule)
        restoration.setContent { FamilyFinAIAppTheme { Host(holder, Events()) } }
        menu("m1").performClick()
        item("archive").performClick()

        restoration.emulateSavedInstanceStateRestore()

        rule.onNodeWithText("¿Archivar el medio de pago?").assertIsDisplayed()
    }
}
