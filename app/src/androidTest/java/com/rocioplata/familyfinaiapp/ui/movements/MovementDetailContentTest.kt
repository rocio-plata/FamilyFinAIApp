package com.rocioplata.familyfinaiapp.ui.movements

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rocioplata.familyfinaiapp.domain.header.FamilyRole
import com.rocioplata.familyfinaiapp.domain.money.Money
import com.rocioplata.familyfinaiapp.domain.movements.MovementFailure
import com.rocioplata.familyfinaiapp.domain.movements.MovementType
import com.rocioplata.familyfinaiapp.ui.theme.FamilyFinAIAppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class MovementDetailContentTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val date = LocalDate.of(2026, 11, 12)

    private fun content(
        type: MovementType = MovementType.EXPENSE,
        note: String? = "Compra semanal",
        category: ResolvedItem = ResolvedItem("Comestibles", false),
        tag: ResolvedItem? = ResolvedItem("supermercado", false),
        method: ResolvedItem? = ResolvedItem("Tarjeta", false),
        recordedBy: MemberLabel = MemberLabel.You("Valentina Plata", FamilyRole.OWNER),
        title: String = "Supermercado Jumbo",
        confirmingDelete: Boolean = false,
        isDeleting: Boolean = false,
        notice: MovementFailure? = null,
    ) = MovementDetailUiState.Content(
        id = "m-1", type = type, amount = Money(45_900, "CLP"), title = title, note = note, date = date,
        category = category, categoryIcon = "shopping_cart", tag = tag, paymentMethod = method,
        recordedBy = recordedBy, confirmingDelete = confirmingDelete, isDeleting = isDeleting, notice = notice,
    )

    private class Calls {
        var back = 0
        var edit = 0
        var delete = 0
        var confirm = 0
        var cancel = 0
        var noticeConsumed = 0
    }

    private fun show(state: MovementDetailUiState, calls: Calls = Calls()): Calls {
        composeTestRule.setContent {
            FamilyFinAIAppTheme {
                MovementDetailContent(
                    state = state,
                    onBack = { calls.back++ },
                    onEdit = { calls.edit++ },
                    onDelete = { calls.delete++ },
                    onConfirmDelete = { calls.confirm++ },
                    onCancelDelete = { calls.cancel++ },
                    onNoticeConsumed = { calls.noticeConsumed++ },
                )
            }
        }
        return calls
    }

    private fun tag(tag: String): SemanticsNodeInteraction = composeTestRule.onNodeWithTag(tag).performScrollTo()

    private fun SemanticsNodeInteraction.assertDescriptionContains(text: String) =
        assert(hasContentDescription(text, substring = true))

    @Test
    fun showsAllTheDataOfTheMovement() {
        show(state = content())

        composeTestRule.onNodeWithText("Movimiento").assertIsDisplayed()
        composeTestRule.onNodeWithTag("movement_detail_back").assertIsDisplayed()
        composeTestRule.onNodeWithTag("movement_detail_amount")
            .assertTextContains(Money(45_900, "CLP").formattedFor(MovementType.EXPENSE))
        composeTestRule.onNodeWithTag("movement_detail_title").assertTextContains("Supermercado Jumbo")
        composeTestRule.onNodeWithTag("movement_detail_payment_method").assertTextContains("Tarjeta")
        tag("movement_detail_category").assertTextContains("Comestibles")
        tag("movement_detail_tag").assertTextContains("#supermercado")
        tag("movement_detail_date").assertTextContains(formatDate(date))
        tag("movement_detail_payment_row").assertTextContains("Tarjeta")
        tag("movement_detail_note").assertTextContains("Compra semanal")
        tag("movement_detail_edit").assertIsDisplayed()
        tag("movement_detail_delete").assertIsDisplayed()
    }

    @Test
    fun expenseAndIncomeAreTellApartWithoutColor_bySignAndByAccessibleType() {
        show(state = content(type = MovementType.EXPENSE))
        composeTestRule.onNodeWithTag("movement_detail_amount")
            .assertTextContains("-", substring = true)
            .assertDescriptionContains("Gasto")
    }

    @Test
    fun anIncomeHasPlusSignAndItsType() {
        show(state = content(type = MovementType.INCOME))

        composeTestRule.onNodeWithTag("movement_detail_amount")
            .assertTextContains("+", substring = true)
            .assertDescriptionContains("Ingreso")
    }

    @Test
    fun archivedElementsAreIndicated() {
        show(
            state = content(
                category = ResolvedItem("Vieja", true), tag = ResolvedItem("antigua", true),
                method = ResolvedItem("Cuenta", true),
            ),
        )

        tag("movement_detail_category").assertTextContains("Vieja (archivada)")
        tag("movement_detail_tag").assertTextContains("#antigua (archivada)")
        tag("movement_detail_payment_row").assertTextContains("Cuenta (archivado)")
        composeTestRule.onNodeWithTag("movement_detail_payment_method").assertTextContains("Cuenta (archivado)")
    }

    @Test
    fun elementsThatAreNotFoundShowAsUnavailable() {
        show(state = content(category = ResolvedItem(null, false), method = ResolvedItem(null, false)))

        tag("movement_detail_category").assertTextContains("No disponible")
        tag("movement_detail_payment_row").assertTextContains("No disponible")
    }

    @Test
    fun withoutTagAndWithoutNoteTheirSectionsAreOmitted() {
        show(state = content(tag = null, note = null))

        composeTestRule.onAllNodesWithTag("movement_detail_tag").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("movement_detail_note").assertCountEquals(0)
        tag("movement_detail_edit").assertIsDisplayed()
    }

    @Test
    fun withoutPaymentMethodItsRowIsOmitted() {
        show(state = content(method = null))

        composeTestRule.onAllNodesWithTag("movement_detail_payment_method").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("movement_detail_payment_row").assertCountEquals(0)
    }

    @Test
    fun youShowsNameInitialsRoleAndTheYouMark() {
        show(state = content(recordedBy = MemberLabel.You("Valentina Plata", FamilyRole.OWNER)))

        tag("movement_detail_recorded_by")
            .assertTextContains("Valentina Plata").assertTextContains("VP")
            .assertTextContains("Tú").assertTextContains("Propietario")
    }

    @Test
    fun anotherMemberShowsNameAndRoleButNotTheYouMark() {
        show(state = content(recordedBy = MemberLabel.Named("Carlos Mendoza", FamilyRole.MEMBER)))

        tag("movement_detail_recorded_by")
            .assertTextContains("Carlos Mendoza").assertTextContains("CM").assertTextContains("Integrante")
            .assert(!hasText("Tú"))
    }

    @Test
    fun aFormerMemberShowsFormerMemberWithoutRole() {
        show(state = content(recordedBy = MemberLabel.FormerMember))

        tag("movement_detail_recorded_by").assertTextContains("Ex-miembro")
            .assert(!hasText("Propietario")).assert(!hasText("Integrante"))
    }

    @Test
    fun aMemberWithoutANameShowsTheGenericTextAndRole() {
        show(state = content(recordedBy = MemberLabel.NoName(FamilyRole.MEMBER)))

        tag("movement_detail_recorded_by").assertTextContains("Miembro de la familia").assertTextContains("Integrante")
    }

    @Test
    fun whenTheMembersAreUnavailableItSaysSoAndTheRestIsStillVisible() {
        show(state = content(recordedBy = MemberLabel.Unavailable))

        tag("movement_detail_recorded_by").assertTextContains("No disponible")
        composeTestRule.onNodeWithTag("movement_detail_title").assertIsDisplayed()
    }

    @Test
    fun neverShowsAnEmail() {
        show(state = content())

        composeTestRule.onAllNodesWithText("@", substring = true).assertCountEquals(0)
    }

    @Test
    fun aVeryLongTitleDoesNotBreakTheScreen() {
        show(state = content(title = "Supermercado ".repeat(30)))

        composeTestRule.onNodeWithTag("movement_detail_hero").assertIsDisplayed()
        tag("movement_detail_delete").assertIsDisplayed()
    }

    @Test
    fun editReportsTheClick() {
        val calls = show(state = content())

        tag("movement_detail_edit").performClick()

        assertEquals(1, calls.edit)
    }

    @Test
    fun theBackArrowAndBackReportTheCloseRequest() {
        val calls = show(state = content())

        composeTestRule.onNodeWithTag("movement_detail_back").performClick()
        pressBack()

        assertEquals(2, calls.back)
    }

    @Test
    fun deleteReportsTheRequestAndDoesNotDeleteByItself() {
        val calls = show(state = content())

        tag("movement_detail_delete").performClick()

        assertEquals(1, calls.delete)
        assertEquals(0, calls.confirm)
        composeTestRule.onAllNodesWithTag("movement_detail_delete_dialog").assertCountEquals(0)
    }

    @Test
    fun theConfirmationDialogOffersConfirmAndCancel() {
        val calls = show(state = content(confirmingDelete = true))

        composeTestRule.onNodeWithTag("movement_detail_delete_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithText("¿Eliminar el movimiento?").assertIsDisplayed()
        composeTestRule.onNodeWithTag("movement_detail_delete_cancel").performClick()
        composeTestRule.onNodeWithTag("movement_detail_delete_confirm").performClick()

        assertEquals(1, calls.cancel)
        assertEquals(1, calls.confirm)
    }

    @Test
    fun backWithTheDialogOpenCancelsTheDeletion() {
        val calls = show(state = content(confirmingDelete = true))

        pressBack()

        assertEquals(1, calls.cancel)
        assertEquals(0, calls.back)
    }

    @Test
    fun whileDeletingShowsProgressAndDisablesTheActions() {
        val calls = show(state = content(confirmingDelete = true, isDeleting = true))

        composeTestRule.onNodeWithTag("movement_detail_deleting").assertIsDisplayed()
        composeTestRule.onNodeWithTag("movement_detail_delete_confirm").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("movement_detail_delete_cancel").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("movement_detail_back").assertIsNotEnabled()
        assertEquals(0, calls.confirm)
    }

    @Test
    fun whileDeletingEditAndDeleteAreDisabled() {
        show(state = content(isDeleting = true))

        tag("movement_detail_edit").assertIsNotEnabled()
        tag("movement_detail_delete").assertIsNotEnabled()
    }

    @Test
    fun whenIdleEditAndDeleteAreEnabled() {
        show(state = content())

        tag("movement_detail_edit").assertIsEnabled()
        tag("movement_detail_delete").assertIsEnabled()
    }

    @Test
    fun aFailureNoticeIsShownAndConsumed() {
        val calls = show(state = content(notice = MovementFailure.Offline))
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(
            "Necesitas conexión para hacer cambios. Lo que escribiste se conserva.",
        ).assertIsDisplayed()
        assertTrue(calls.noticeConsumed >= 1)
    }

    @Test
    fun aMovementThatNoLongerExistsIsReportedWithAWayBack() {
        val calls = show(state = MovementDetailUiState.Gone)

        composeTestRule.onNodeWithTag("movement_detail_gone").assertIsDisplayed()
        composeTestRule.onNodeWithText("Este movimiento ya no existe.").assertIsDisplayed()
        composeTestRule.onNodeWithTag("movement_detail_gone_back").performClick()

        assertEquals(1, calls.back)
        composeTestRule.onAllNodesWithTag("movement_detail_edit").assertCountEquals(0)
    }

    @Test
    fun loadingShowsProgress() {
        show(state = MovementDetailUiState.Loading)

        composeTestRule.onNodeWithTag("movement_detail_loading").assertIsDisplayed()
    }

    @Test
    fun theActionsAreAtLeast48dpTall() {
        show(state = content())

        tag("movement_detail_edit").assertHeightIsAtLeast(48.dp)
        tag("movement_detail_delete").assertHeightIsAtLeast(48.dp)
        composeTestRule.onNodeWithTag("movement_detail_back").assertHeightIsAtLeast(40.dp)
    }
}
