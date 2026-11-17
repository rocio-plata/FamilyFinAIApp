package com.rocioplata.familyfinaiapp.ui.movements

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rocioplata.familyfinaiapp.domain.categories.Category
import com.rocioplata.familyfinaiapp.domain.categories.CategoryStatus
import com.rocioplata.familyfinaiapp.domain.categories.CategoryType
import com.rocioplata.familyfinaiapp.domain.categories.Tag
import com.rocioplata.familyfinaiapp.domain.categories.TagStatus
import com.rocioplata.familyfinaiapp.domain.movements.AmountError
import com.rocioplata.familyfinaiapp.domain.movements.DateError
import com.rocioplata.familyfinaiapp.domain.movements.MovementFailure
import com.rocioplata.familyfinaiapp.domain.movements.MovementType
import com.rocioplata.familyfinaiapp.domain.movements.TitleError
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethod
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethodStatus
import com.rocioplata.familyfinaiapp.ui.theme.FamilyFinAIAppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class MovementFormContentTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val today = LocalDate.of(2026, 11, 16)
    private val food = Category(
        "c-food", CategoryType.EXPENSE, "Comestibles", "shopping_cart", CategoryStatus.ACTIVE,
        listOf(Tag("t-1", "supermercado", TagStatus.ACTIVE, 0), Tag("t-2", "feria", TagStatus.ACTIVE, 1)),
    )
    private val services = Category("c-serv", CategoryType.EXPENSE, "Servicios", "bolt", CategoryStatus.ACTIVE, emptyList())
    private val card = PaymentMethod("m-card", "Tarjeta", PaymentMethodStatus.ACTIVE)
    private val cash = PaymentMethod("m-cash", "Efectivo", PaymentMethodStatus.ACTIVE)

    private fun state(
        type: MovementType = MovementType.EXPENSE,
        amount: String = "",
        currency: String? = "CLP",
        title: String = "",
        categories: List<Category> = listOf(food, services),
        categoryId: String? = null,
        tags: List<Tag> = emptyList(),
        tagId: String? = null,
        methods: List<PaymentMethod> = listOf(card, cash),
        methodId: String? = null,
        date: LocalDate = today,
        note: String = "",
        errors: MovementFormErrors = MovementFormErrors(),
        options: FormOptionsState = FormOptionsState.Ready,
        noCategories: Boolean = false,
        noPaymentMethods: Boolean = false,
        isSaving: Boolean = false,
        confirmingDiscard: Boolean = false,
        notice: MovementFailure? = null,
        isEditing: Boolean = false,
        isLoadingMovement: Boolean = false,
        savedPartially: Boolean = false,
    ) = MovementFormUiState(
        type, amount, currency, title, categories, categoryId, tags, tagId, methods, methodId, date, today, note,
        errors, options, noCategories, noPaymentMethods, isSaving, confirmingDiscard, notice,
        isEditing, isLoadingMovement, savedPartially,
    )

    private class Calls {
        val types = mutableListOf<MovementType>()
        val amounts = mutableListOf<String>()
        val titles = mutableListOf<String>()
        val categories = mutableListOf<String>()
        val tags = mutableListOf<String?>()
        val methods = mutableListOf<String>()
        val dates = mutableListOf<LocalDate>()
        var today = 0
        var yesterday = 0
        var save = 0
        var close = 0
        var retry = 0
        var confirmDiscard = 0
        var dismissDiscard = 0
        var noticeConsumed = 0
    }

    private fun show(state: MovementFormUiState, calls: Calls = Calls(), family: String = "Familia Plata"): Calls {
        composeTestRule.setContent {
            FamilyFinAIAppTheme {
                MovementFormContent(
                    state = state, familyName = family,
                    onClose = { calls.close++ },
                    onTypeChange = { calls.types += it },
                    onAmountChange = { calls.amounts += it },
                    onTitleChange = { calls.titles += it },
                    onCategorySelect = { calls.categories += it },
                    onTagSelect = { calls.tags += it },
                    onPaymentMethodSelect = { calls.methods += it },
                    onToday = { calls.today++ },
                    onYesterday = { calls.yesterday++ },
                    onDateSelect = { calls.dates += it },
                    onNoteChange = {},
                    onSave = { calls.save++ },
                    onRetryOptions = { calls.retry++ },
                    onConfirmDiscard = { calls.confirmDiscard++ },
                    onDismissDiscard = { calls.dismissDiscard++ },
                    onNoticeConsumed = { calls.noticeConsumed++ },
                )
            }
        }
        return calls
    }

    private fun tag(tag: String): SemanticsNodeInteraction = composeTestRule.onNodeWithTag(tag).performScrollTo()

    @Test
    fun showsTheFormWithFamilyAndCurrency() {
        show(state())

        composeTestRule.onNodeWithTag("movement_form_family").assertIsDisplayed()
        composeTestRule.onNodeWithText("Familia Plata").assertIsDisplayed()
        composeTestRule.onNodeWithTag("movement_form_currency").assertTextEquals("CLP")
        composeTestRule.onNodeWithText("Nuevo movimiento").assertIsDisplayed()
        composeTestRule.onNodeWithTag("movement_form_close").assertIsDisplayed()
        composeTestRule.onNodeWithTag("movement_form_type_EXPENSE").assertIsSelected()
        composeTestRule.onNodeWithTag("movement_form_type_INCOME").assertIsNotSelected()
        composeTestRule.onNodeWithTag("movement_form_amount").assertIsDisplayed()
        composeTestRule.onNodeWithTag("movement_form_title").assertIsDisplayed()
        tag("movement_form_category_c-food").assertIsDisplayed()
        tag("movement_form_category_c-serv").assertIsDisplayed()
        tag("movement_form_payment_m-card").assertIsDisplayed()
        tag("movement_form_date_today").assertIsDisplayed()
        tag("movement_form_note").assertIsDisplayed()
        tag("movement_form_save").assertIsDisplayed()
    }

    @Test
    fun theAmountLabelFollowsTheType() {
        show(state(type = MovementType.INCOME))

        composeTestRule.onNodeWithText("Monto del ingreso").assertIsDisplayed()
    }

    @Test
    fun changingTheTypeReportsIt() {
        val calls = show(state())

        composeTestRule.onNodeWithTag("movement_form_type_INCOME").performClick()

        assertEquals(listOf(MovementType.INCOME), calls.types)
    }

    @Test
    fun typingReportsTheTextOfAmountAndTitle() {
        val calls = show(state())

        composeTestRule.onNodeWithTag("movement_form_amount").performTextInput("45900")
        composeTestRule.onNodeWithTag("movement_form_title").performTextInput("Super")

        // El estado de este test no cambia, así que el campo vuelve a vaciarse después de cada tecleo.
        assertEquals("45900", calls.amounts.first())
        assertEquals("Super", calls.titles.first())
    }

    @Test
    fun choosingACategoryReportsItAndShowsItSelected_withItsTags() {
        val calls = show(state(categoryId = "c-food", tags = food.tags, tagId = "t-2"))

        tag("movement_form_category_c-food").assertIsSelected()
        tag("movement_form_category_c-serv").assertIsNotSelected().performClick()
        tag("movement_form_tag_t-1").assertIsDisplayed().assertIsNotSelected()
        tag("movement_form_tag_t-2").assertIsSelected()
        assertEquals(listOf("c-serv"), calls.categories)
    }

    @Test
    fun tappingATagAndTheChosenTagBothReportTheTag_soTheViewModelCanClearIt() {
        val calls = show(state(categoryId = "c-food", tags = food.tags, tagId = "t-2"))

        tag("movement_form_tag_t-1").performClick()
        tag("movement_form_tag_t-2").performClick()

        assertEquals(listOf<String?>("t-1", "t-2"), calls.tags)
    }

    @Test
    fun withoutACategoryThereAreNoTags() {
        show(state(categoryId = null, tags = emptyList()))

        composeTestRule.onAllNodesWithTag("movement_form_tag_t-1").assertCountEquals(0)
    }

    @Test
    fun choosingAPaymentMethodReportsItAndShowsTheProposedOneSelected() {
        val calls = show(state(methodId = "m-card"))

        tag("movement_form_payment_m-card").assertIsSelected()
        tag("movement_form_payment_m-cash").performClick()

        assertEquals(listOf("m-cash"), calls.methods)
    }

    @Test
    fun showsAWarningNextToEachInvalidField() {
        show(
            state(
                errors = MovementFormErrors(
                    amount = AmountError.REQUIRED, title = TitleError.REQUIRED, category = true,
                    paymentMethod = true, date = DateError.FUTURE,
                ),
            ),
        )

        composeTestRule.onNodeWithText("Escribe el monto.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Escribe un concepto.").assertIsDisplayed()
        tag("movement_form_category_error").assertIsDisplayed()
        tag("movement_form_payment_error").assertIsDisplayed()
        tag("movement_form_date_error").assertIsDisplayed()
    }

    @Test
    fun anAmountOfZeroHasItsOwnWarning() {
        show(state(amount = "0", errors = MovementFormErrors(amount = AmountError.MUST_BE_POSITIVE)))

        composeTestRule.onNodeWithText("El monto debe ser mayor que cero.").assertIsDisplayed()
    }

    @Test
    fun theQuickDatesReportTheirAction_andTheSelectedOneIsMarked() {
        val calls = show(state(date = today.minusDays(1)))

        tag("movement_form_date_yesterday").assertIsSelected()
        tag("movement_form_date_today").assertIsNotSelected().performClick()
        tag("movement_form_date_yesterday").performClick()

        assertEquals(1, calls.today)
        assertEquals(1, calls.yesterday)
    }

    @Test
    fun otherDateOpensThePickerAndConfirmingReportsTheDay() {
        val calls = show(state(date = today.minusDays(5)))

        tag("movement_form_date_other").assertIsSelected().performClick()
        composeTestRule.onNodeWithTag("movement_form_date_picker").assertIsDisplayed()
        composeTestRule.onNodeWithTag("movement_form_date_confirm").performClick()

        assertEquals(listOf(today.minusDays(5)), calls.dates)
        composeTestRule.onAllNodesWithTag("movement_form_date_picker").assertCountEquals(0)
    }

    @Test
    fun whileSavingShowsProgressAndDisablesEverything() {
        val calls = show(state(isSaving = true))

        tag("movement_form_saving").assertIsDisplayed()
        tag("movement_form_save").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("movement_form_close").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("movement_form_amount").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("movement_form_title").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("movement_form_type_INCOME").assertIsNotEnabled()
        tag("movement_form_category_c-food").assertIsNotEnabled()
        tag("movement_form_payment_m-card").assertIsNotEnabled()
        tag("movement_form_date_today").assertIsNotEnabled()
        // Atrás se entrega al ViewModel, que lo ignora mientras guarda; la pantalla no se cierra sola.
        pressBack()
        assertEquals(1, calls.close)
        composeTestRule.onNodeWithTag("movement_form_screen").assertIsDisplayed()
    }

    @Test
    fun saveReportsTheClick() {
        val calls = show(state())

        tag("movement_form_save").assertIsEnabled().performClick()

        assertEquals(1, calls.save)
    }

    @Test
    fun theCloseButtonAndBackBothReportTheCloseRequest() {
        val calls = show(state())

        composeTestRule.onNodeWithTag("movement_form_close").performClick()
        pressBack()

        assertEquals(2, calls.close)
    }

    @Test
    fun discardDialogOffersConfirmAndKeepEditing() {
        val calls = show(state(amount = "100", confirmingDiscard = true))

        composeTestRule.onNodeWithTag("movement_form_discard_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("movement_form_discard_keep").performClick()
        composeTestRule.onNodeWithTag("movement_form_discard_confirm").performClick()

        assertEquals(1, calls.dismissDiscard)
        assertEquals(1, calls.confirmDiscard)
    }

    @Test
    fun backWithTheDiscardDialogOpenKeepsEditing() {
        val calls = show(state(confirmingDiscard = true))

        pressBack()

        assertEquals(1, calls.dismissDiscard)
        assertEquals(0, calls.close)
    }

    @Test
    fun withoutCategoriesOfTheTypeExplainsWhereToCreateThem() {
        show(state(categories = emptyList(), noCategories = true))
        tag("movement_form_no_categories").assertIsDisplayed().assertTextContains("Categorías", substring = true)
    }

    @Test
    fun withoutIncomeCategoriesTheMessageIsAboutIncome() {
        show(state(type = MovementType.INCOME, categories = emptyList(), noCategories = true))

        tag("movement_form_no_categories").assertTextContains("ingresos", substring = true)
    }

    @Test
    fun withoutPaymentMethodsExplainsWhereToCreateThem() {
        show(state(methods = emptyList(), noPaymentMethods = true))

        tag("movement_form_no_payment_methods").assertIsDisplayed().assertTextContains("Medios de pago", substring = true)
    }

    @Test
    fun whileTheOptionsLoadShowsProgress() {
        show(state(options = FormOptionsState.Loading, categories = emptyList(), methods = emptyList()))
        composeTestRule.onNodeWithTag("movement_form_options_loading").assertIsDisplayed()
    }

    @Test
    fun optionsErrorOffersRetry() {
        val calls = show(state(options = FormOptionsState.Error, categories = emptyList(), methods = emptyList()))

        composeTestRule.onNodeWithTag("movement_form_options_error").assertIsDisplayed()
        composeTestRule.onNodeWithTag("movement_form_options_retry").performClick()

        assertEquals(1, calls.retry)
    }

    @Test
    fun withoutACurrencyExplainsItAndDisablesSave() {
        show(state(currency = null))

        tag("movement_form_currency_missing").assertIsDisplayed()
        tag("movement_form_save").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("movement_form_amount").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("movement_form_currency").assertTextEquals("Sin moneda")
    }

    @Test
    fun aFailureNoticeIsShownAndConsumed() {
        val calls = show(state(notice = MovementFailure.Offline))
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(
            "Necesitas conexión para hacer cambios. Lo que escribiste se conserva.",
        ).assertIsDisplayed()
        assertTrue(calls.noticeConsumed >= 1)
    }

    @Test
    fun aHalfFilledFormSurvivesRecreation() {
        val restoration = StateRestorationTester(composeTestRule)
        restoration.setContent {
            var amount by rememberSaveable { mutableStateOf("") }
            var title by rememberSaveable { mutableStateOf("") }
            FamilyFinAIAppTheme {
                MovementFormContent(
                    state = state(amount = amount, title = title),
                    familyName = "Familia Plata", onClose = {}, onTypeChange = {},
                    onAmountChange = { amount = it }, onTitleChange = { title = it }, onCategorySelect = {},
                    onTagSelect = {}, onPaymentMethodSelect = {}, onToday = {}, onYesterday = {}, onDateSelect = {},
                    onNoteChange = {}, onSave = {}, onRetryOptions = {}, onConfirmDiscard = {},
                    onDismissDiscard = {}, onNoticeConsumed = {},
                )
            }
        }
        composeTestRule.onNodeWithTag("movement_form_amount").performTextInput("45900")
        composeTestRule.onNodeWithTag("movement_form_title").performTextInput("Super")

        restoration.emulateSavedInstanceStateRestore()

        composeTestRule.onNodeWithTag("movement_form_amount").assertTextContains("45900")
        composeTestRule.onNodeWithTag("movement_form_title").assertTextContains("Super")
    }

    // --- Edición (T-21: CA-11, CA-12, CA-13) ---

    private val archivedCategory = Category(
        "c-old", CategoryType.EXPENSE, "Vieja", "bolt", CategoryStatus.DEPRECATED,
        listOf(Tag("t-old", "antigua", TagStatus.DEPRECATED, 0)),
    )
    private val archivedMethod = PaymentMethod("m-old", "Cuenta vieja", PaymentMethodStatus.DEPRECATED)

    @Test
    fun editingShowsItsOwnTitleAndTheDataOfTheMovement() {
        show(
            state(
                isEditing = true, amount = "45900", title = "Supermercado", categoryId = "c-food", tags = food.tags,
                tagId = "t-1", methodId = "m-card", note = "Compra semanal", date = today.minusDays(4),
            ),
        )

        composeTestRule.onNodeWithText("Editar movimiento").assertIsDisplayed()
        composeTestRule.onNodeWithTag("movement_form_amount").assertTextContains("45900")
        composeTestRule.onNodeWithTag("movement_form_title").assertTextContains("Supermercado")
        tag("movement_form_category_c-food").assertIsSelected()
        tag("movement_form_tag_t-1").assertIsSelected()
        tag("movement_form_payment_m-card").assertIsSelected()
        tag("movement_form_note").assertTextContains("Compra semanal")
        tag("movement_form_date_other").assertIsSelected()
        tag("movement_form_save").assertTextContains("Guardar cambios")
    }

    @Test
    fun creatingKeepsItsTitleAndSaveLabel() {
        show(state())

        composeTestRule.onNodeWithText("Nuevo movimiento").assertIsDisplayed()
        tag("movement_form_save").assertTextContains("Guardar movimiento")
    }

    @Test
    fun whenEditingTheTypeSelectorIsDisabledButShowsTheType() {
        val calls = show(state(isEditing = true, type = MovementType.INCOME))

        composeTestRule.onNodeWithTag("movement_form_type_INCOME").assertIsSelected().assertIsNotEnabled()
        composeTestRule.onNodeWithTag("movement_form_type_EXPENSE").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("movement_form_type_EXPENSE").performClick()
        assertTrue(calls.types.isEmpty())
    }

    @Test
    fun whenCreatingTheTypeSelectorIsEnabled() {
        show(state())

        composeTestRule.onNodeWithTag("movement_form_type_EXPENSE").assertIsEnabled()
        composeTestRule.onNodeWithTag("movement_form_type_INCOME").assertIsEnabled()
    }

    @Test
    fun archivedElementsAreIndicated() {
        show(
            state(
                isEditing = true, categories = listOf(archivedCategory, food), categoryId = "c-old",
                tags = archivedCategory.tags, tagId = "t-old", methods = listOf(card, archivedMethod),
                methodId = "m-old",
            ),
        )

        tag("movement_form_category_c-old").assertTextContains("archivada", substring = true)
        tag("movement_form_category_c-food").assert(!hasText("archivada", substring = true))
        tag("movement_form_tag_t-old").assertTextContains("(archivada)", substring = true)
        tag("movement_form_payment_m-old").assertTextContains("(archivado)", substring = true)
        tag("movement_form_payment_m-card").assertIsDisplayed()
    }

    @Test
    fun anArchivedCurrentCategoryCanStillBeKeptAndOthersChosen() {
        val calls = show(
            state(
                isEditing = true, categories = listOf(archivedCategory, food), categoryId = "c-old",
                tags = archivedCategory.tags, tagId = "t-old",
            ),
        )

        tag("movement_form_category_c-old").assertIsSelected()
        tag("movement_form_category_c-food").performClick()

        assertEquals(listOf("c-food"), calls.categories)
    }

    @Test
    fun aPartialSaveShowsWhatWasSavedAndWhatIsPending() {
        show(state(isEditing = true, savedPartially = true, notice = MovementFailure.Network))

        composeTestRule.onNodeWithTag("movement_form_partial").assertIsDisplayed()
            .assertTextContains("Se guardaron los datos", substring = true)
            .assertTextContains("no el cambio de categoría", substring = true)
        tag("movement_form_save").assertIsEnabled()
    }

    @Test
    fun withoutAPartialSaveThereIsNoPartialNotice() {
        show(state(isEditing = true))

        composeTestRule.onAllNodesWithTag("movement_form_partial").assertCountEquals(0)
    }

    @Test
    fun whileTheMovementIsBeingReadShowsProgressAndNoFields() {
        show(state(isEditing = true, isLoadingMovement = true))

        composeTestRule.onNodeWithTag("movement_form_loading").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("movement_form_amount").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("movement_form_save").assertCountEquals(0)
        composeTestRule.onNodeWithTag("movement_form_close").assertIsDisplayed()
    }

    @Test
    fun editingDiscardsWithTheSameDialog() {
        val calls = show(state(isEditing = true, title = "x", confirmingDiscard = true))

        composeTestRule.onNodeWithTag("movement_form_discard_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("movement_form_discard_keep").performClick()
        composeTestRule.onNodeWithTag("movement_form_discard_confirm").performClick()

        assertEquals(1, calls.dismissDiscard)
        assertEquals(1, calls.confirmDiscard)
    }

    @Test
    fun editingWhileSavingDisablesEverything() {
        show(state(isEditing = true, isSaving = true))

        tag("movement_form_save").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("movement_form_amount").assertIsNotEnabled()
        tag("movement_form_saving").assertIsDisplayed()
    }

    @Test
    fun withTheLargestFontTheCategoriesUseTwoColumnsSoNamesAreNotBrokenMidWord() {
        val many = (1..5).map { Category("c-$it", CategoryType.EXPENSE, "Categoría $it", null, CategoryStatus.ACTIVE, emptyList()) }
        composeTestRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = 2f)) {
                FamilyFinAIAppTheme {
                    MovementFormContent(
                        state = state(categories = many), familyName = "Familia", onClose = {}, onTypeChange = {},
                        onAmountChange = {}, onTitleChange = {}, onCategorySelect = {}, onTagSelect = {},
                        onPaymentMethodSelect = {}, onToday = {}, onYesterday = {}, onDateSelect = {}, onNoteChange = {},
                        onSave = {}, onRetryOptions = {}, onConfirmDiscard = {}, onDismissDiscard = {},
                        onNoticeConsumed = {},
                    )
                }
            }
        }

        val tops = (1..5).map { tag("movement_form_category_c-$it").getUnclippedBoundsInRoot().top }
        // Dos por fila: la 1 y la 2 comparten fila, la 3 baja, la 5 queda en la tercera fila.
        assertEquals(tops[0], tops[1])
        assertTrue(tops[2] > tops[0])
        assertEquals(tops[2], tops[3])
        assertTrue(tops[4] > tops[2])
    }
}
