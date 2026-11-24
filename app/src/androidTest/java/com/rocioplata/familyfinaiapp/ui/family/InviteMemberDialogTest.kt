package com.rocioplata.familyfinaiapp.ui.family

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rocioplata.familyfinaiapp.domain.family.FamilyAccessFailure
import com.rocioplata.familyfinaiapp.domain.header.FamilyRole
import com.rocioplata.familyfinaiapp.ui.theme.FamilyFinAIAppTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InviteMemberDialogTest {
    @get:Rule
    val rule = createComposeRule()

    /** Los campos de estos tests toman el foco: el teclado no debe quedar abierto para el test siguiente de la suite. */
    @After
    fun hideKeyboard() = closeSoftKeyboard()

    /** Hace de ViewModel: el estado vive fuera de la composición, como en la app. */
    private class Holder(initial: InviteFormState = InviteFormState(email = "", role = FamilyRole.MEMBER)) {
        var form by mutableStateOf(initial)
        var submits = 0
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
        InviteMemberDialog(
            form = holder.form,
            onEmailChange = { holder.form = holder.form.copy(email = it, error = null) },
            onRoleChange = { holder.form = holder.form.copy(role = it) },
            onSubmit = { holder.submits++ },
            onClose = { holder.closes++ },
            onConfirmDiscard = { holder.confirmed++ },
            onDismissDiscard = { holder.kept++; holder.form = holder.form.copy(confirmingDiscard = false) },
        )
    }

    private fun tag(tag: String) = rule.onNodeWithTag(tag, useUnmergedTree = true)

    /** Con el campo enfocado el teclado puede estar abierto y consumir el primer «atrás»: se repite hasta cumplirse. */
    private fun pressBackUntil(done: () -> Boolean) {
        repeat(3) {
            if (done()) return
            pressBack()
            rule.waitForIdle()
        }
    }

    @Test
    fun showsTheTitleTheEmailFieldTheRolesAndTheNoEmailNotice() {
        show(Holder())

        rule.onNodeWithText("Invitar miembro").assertIsDisplayed()
        tag("invite_email").assertIsDisplayed()
        tag("invite_role_member").assertIsDisplayed()
        tag("invite_role_owner").assertIsDisplayed()
        tag("invite_info").assertTextEquals(
            "No se envía ningún correo: la persona verá la invitación al entrar en la app con ese correo. Vence a los 7 días.",
        )
    }

    @Test
    fun theMemberRoleIsPreselected() {
        show(Holder())

        tag("invite_role_member").assertIsSelected()
        tag("invite_role_owner").assertIsNotSelected()
    }

    @Test
    fun choosingTheOwnerRoleSelectsIt() {
        val holder = Holder()
        show(holder)

        tag("invite_role_owner").performClick()

        assertEquals(FamilyRole.OWNER, holder.form.role)
        tag("invite_role_owner").assertIsSelected()
        tag("invite_role_member").assertIsNotSelected()
    }

    @Test
    fun theEmailFieldHasTheFocusOnOpen() {
        show(Holder())

        tag("invite_email").assertIsFocused()
    }

    @Test
    fun typingFillsTheEmail() {
        val holder = Holder()
        show(holder)

        tag("invite_email").performTextInput("hermano@example.com")

        assertEquals("hermano@example.com", holder.form.email)
    }

    @Test
    fun eachErrorShowsItsMessageNextToTheField_keepingWhatWasTyped() {
        val holder = Holder(InviteFormState(email = "hermano@example.com", role = FamilyRole.MEMBER))
        show(holder)
        val expected = listOf(
            InviteFormError.Required to "Escribe el correo de la persona",
            InviteFormError.InvalidFormat to "El correo no es válido",
            InviteFormError.TooLong to "El correo admite hasta 255 caracteres",
            InviteFormError.AlreadyInvited to "Ya hay una invitación vigente a ese correo. Revócala antes de enviar otra.",
            InviteFormError.AlreadyMember to "Esa persona ya es miembro de la familia",
            InviteFormError.Failure(FamilyAccessFailure.Offline) to "Esta acción requiere conexión. Lo que escribiste se conserva.",
            InviteFormError.Failure(FamilyAccessFailure.Network) to "No se pudo completar la acción. Revisa tu conexión e inténtalo de nuevo.",
        )

        expected.forEach { (error, message) ->
            holder.form = holder.form.copy(error = error)
            rule.waitForIdle()
            tag("invite_email_error").assertTextEquals(message)
            assertEquals("hermano@example.com", holder.form.email)
        }
    }

    @Test
    fun withoutErrorThereIsNoMessage() {
        show(Holder())

        tag("invite_email_error").assertDoesNotExist()
    }

    @Test
    fun theInviteButtonAndTheKeyboardActionAskToSubmit() {
        val holder = Holder(InviteFormState(email = "a@b.cl", role = FamilyRole.MEMBER))
        show(holder)

        tag("invite_email").performImeAction()
        tag("invite_submit").assertIsEnabled().performClick()

        assertEquals(2, holder.submits)
    }

    @Test
    fun whileSavingEverythingIsDisabled_andThereIsProgress() {
        show(Holder(InviteFormState(email = "a@b.cl", role = FamilyRole.MEMBER, isSaving = true)))

        tag("invite_submit").assertIsNotEnabled()
        tag("invite_cancel").assertIsNotEnabled()
        tag("invite_email").assertIsNotEnabled()
        tag("invite_role_owner").assertIsNotEnabled()
        rule.onNodeWithContentDescription("Enviando la invitación…").assertIsDisplayed()
    }

    @Test
    fun cancelAndTheSystemBackAskToClose() {
        val holder = Holder()
        show(holder)

        tag("invite_cancel").performClick()
        assertEquals(1, holder.closes)

        pressBackUntil { holder.closes > 1 }
        assertEquals(2, holder.closes)
    }

    @Test
    fun theDiscardDialogOffersToLeaveOrKeepEditing() {
        val holder = Holder(InviteFormState(email = "a@b.cl", role = FamilyRole.MEMBER, confirmingDiscard = true))
        show(holder)

        rule.onNodeWithText("¿Salir sin invitar?").assertIsDisplayed()
        rule.onNodeWithText("Se perderá lo que escribiste.").assertIsDisplayed()
        tag("invite_discard_keep").performClick()
        assertEquals(1, holder.kept)
        tag("invite_discard_confirm").assertDoesNotExist()
        assertEquals("a@b.cl", holder.form.email)
    }

    @Test
    fun confirmingTheDiscardAsksToLeave() {
        val holder = Holder(InviteFormState(email = "a@b.cl", role = FamilyRole.MEMBER, confirmingDiscard = true))
        show(holder)

        tag("invite_discard_confirm").performClick()

        assertEquals(1, holder.confirmed)
    }

    @Test
    fun theTypedEmailTheRoleTheErrorAndTheDiscardDialogSurviveRecreation() {
        val holder = Holder(
            InviteFormState(
                email = "hermano@example.com", role = FamilyRole.OWNER,
                error = InviteFormError.AlreadyInvited, confirmingDiscard = true,
            ),
        )
        val restoration = StateRestorationTester(rule)
        restoration.setContent { FamilyFinAIAppTheme { Host(holder) } }

        restoration.emulateSavedInstanceStateRestore()

        tag("invite_discard_keep").assertIsDisplayed()
        tag("invite_discard_keep").performClick()
        tag("invite_role_owner").assertIsSelected()
        tag("invite_email_error").assertTextEquals("Ya hay una invitación vigente a ese correo. Revócala antes de enviar otra.")
    }

    @Test
    fun touchTargetsAreAtLeast48dp() {
        show(Holder())

        tag("invite_role_member").assertHeightIsAtLeast(48.dp)
        tag("invite_role_owner").assertHeightIsAtLeast(48.dp)
        tag("invite_submit").assertHeightIsAtLeast(48.dp)
        tag("invite_cancel").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun withTheLargestTextTheContentScrollsAndTheActionsStayReachable() {
        val form = InviteFormState(
            email = "a".repeat(120) + "@example.com", role = FamilyRole.MEMBER,
            error = InviteFormError.Failure(FamilyAccessFailure.Network),
        )
        show(Holder(form), fontScale = 2f)

        tag("invite_info").performScrollTo().assertIsDisplayed()
        tag("invite_role_owner").performScrollTo().assertIsDisplayed()
        tag("invite_email_error").performScrollTo().assertIsDisplayed()
        tag("invite_submit").assertIsDisplayed()
        tag("invite_cancel").assertIsDisplayed()
    }

    @Test
    fun theDiscardDialogIsNotShownByDefault() {
        show(Holder())

        rule.onAllNodesWithTag("invite_discard_confirm", useUnmergedTree = true).assertCountEquals(0)
    }
}
