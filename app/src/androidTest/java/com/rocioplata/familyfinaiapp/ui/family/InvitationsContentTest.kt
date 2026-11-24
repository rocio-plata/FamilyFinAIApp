package com.rocioplata.familyfinaiapp.ui.family

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rocioplata.familyfinaiapp.domain.family.FamilyAccessFailure
import com.rocioplata.familyfinaiapp.domain.header.FamilyRole
import com.rocioplata.familyfinaiapp.ui.theme.FamilyFinAIAppTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InvitationsContentTest {
    @get:Rule
    val rule = createComposeRule()

    private val gomez = ReceivedInvitationRow("i-1", "f-1", "Familia Gómez", "Carolina", FamilyRole.OWNER, Instant.parse("2026-11-29T12:00:00Z"))
    private val depto = ReceivedInvitationRow("i-2", "f-2", "Departamento", null, FamilyRole.MEMBER, Instant.parse("2026-11-30T12:00:00Z"))

    /** Hace de ViewModel: el estado vive fuera de la composición, como en la app. */
    private class Holder(initial: InvitationsUiState) {
        var state by mutableStateOf(initial)
        val accepted = mutableListOf<String>()
        val declined = mutableListOf<String>()
        var confirmed = 0
        var kept = 0
        var retries = 0
        var backs = 0
        var consumed = 0
        val switched = mutableListOf<String>()
    }

    private fun content(vararg rows: ReceivedInvitationRow) = InvitationsUiState(section = ReceivedSection.Content(rows.toList()))

    private fun show(state: InvitationsUiState, fontScale: Float? = null): Holder {
        val holder = Holder(state)
        rule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale ?: base.fontScale)) {
                FamilyFinAIAppTheme { Host(holder) }
            }
        }
        return holder
    }

    @androidx.compose.runtime.Composable
    private fun Host(holder: Holder) {
        InvitationsContent(
            state = holder.state,
            onBack = { holder.backs++ },
            onSwitchFamily = { holder.switched += it },
            onEventConsumed = {
                holder.consumed++
                holder.state = holder.state.copy(event = null)
            },
            actions = ReceivedInvitationActions(
                onAccept = { holder.accepted += it },
                onDecline = { id ->
                    holder.declined += id
                    holder.state = holder.state.copy(pendingDecline = (holder.state.section as ReceivedSection.Content).rows.first { it.id == id })
                },
                onConfirmDecline = { holder.confirmed++ },
                onDismissDecline = {
                    holder.kept++
                    holder.state = holder.state.copy(pendingDecline = null)
                },
                onRetry = { holder.retries++ },
            ),
        )
    }

    private fun tag(value: String) = rule.onNodeWithTag(value, useUnmergedTree = true)

    // --- Tarjeta (RF-18) ---

    @Test
    fun showsTheCardWithFamilyInviterRoleAndExpiry() {
        show(content(gomez, depto))

        rule.onNodeWithText("Invitaciones").assertIsDisplayed()
        tag("invitation_family_i-1").assertTextEquals("Familia Gómez")
        tag("invitation_by_i-1").assertTextEquals("Te invita Carolina")
        tag("invitation_role_i-1").assertIsDisplayed()
        rule.onNodeWithText("Propietario").assertIsDisplayed()
        rule.onNodeWithText("Integrante").assertIsDisplayed()
        assertTrue(rule.onAllNodesWithTag("invitation_expiry_i-1", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        tag("invitation_family_i-2").assertTextEquals("Departamento")
    }

    @Test
    fun theExpiryShowsTheFormattedDate() {
        show(content(gomez))

        tag("invitation_expiry_i-1").assertTextContains("Vence el ", substring = true).assertTextContains("2026", substring = true)
    }

    @Test
    fun anInviterWithoutNameShowsAGenericText() {
        show(content(depto))

        tag("invitation_by_i-2").assertTextEquals("Te invita un miembro de la familia")
    }

    @Test
    fun aBlankInviterNameShowsTheGenericText() {
        show(content(gomez.copy(invitedByName = "  ")))

        tag("invitation_by_i-1").assertTextEquals("Te invita un miembro de la familia")
    }

    @Test
    fun doesNotOfferAnInvitationCodeNorDataTheBackendDoesNotGive() {
        show(content(gomez))

        assertTrue(rule.onAllNodesWithTag("invitation_code", useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        rule.onAllNodesWithText("código", substring = true, ignoreCase = true).assertCountEquals(0)
        rule.onAllNodesWithText("Miembros", substring = true).assertCountEquals(0)
    }

    // --- Estados ---

    @Test
    fun showsTheLoadingIndicator() {
        show(InvitationsUiState())

        tag("invitations_loading").assertIsDisplayed()
    }

    @Test
    fun showsTheEmptyMessage() {
        show(InvitationsUiState(section = ReceivedSection.Empty))

        rule.onNodeWithText("No tienes invitaciones pendientes").assertIsDisplayed()
        rule.onNodeWithText("Cuando alguien te invite a una familia, la verás aquí.").assertIsDisplayed()
    }

    @Test
    fun showsTheErrorWithRetry() {
        val holder = show(InvitationsUiState(section = ReceivedSection.Error(FamilyAccessFailure.Network)))

        tag("invitations_error").assertTextEquals("No se pudo completar la acción. Revisa tu conexión e inténtalo de nuevo.")
        tag("invitations_retry").performClick()
        assertEquals(1, holder.retries)
    }

    @Test
    fun theOfflineErrorHasItsOwnMessage() {
        show(InvitationsUiState(section = ReceivedSection.Error(FamilyAccessFailure.Offline)))

        tag("invitations_error").assertTextEquals("Esta acción requiere conexión. Lo que escribiste se conserva.")
    }

    @Test
    fun refreshingKeepsTheListAndShowsTheIndicator() {
        show(content(gomez).copy(isRefreshing = true))

        tag("invitations_refreshing").assertIsDisplayed()
        tag("invitation_family_i-1").assertIsDisplayed()
    }

    // --- Aceptar y rechazar ---

    @Test
    fun acceptCallsBackWithTheInvitation() {
        val holder = show(content(gomez, depto))

        tag("invitation_accept_i-2").performClick()

        assertEquals(listOf("i-2"), holder.accepted)
    }

    @Test
    fun whileOneIsBeingAnsweredAllTheButtonsAreDisabled() {
        show(content(gomez, depto).copy(busyId = "i-1"))

        tag("invitation_accept_i-1").assertIsNotEnabled()
        tag("invitation_decline_i-1").assertIsNotEnabled()
        tag("invitation_accept_i-2").assertIsNotEnabled()
        tag("invitation_decline_i-2").assertIsNotEnabled()
        rule.onNodeWithContentDescription("Respondiendo la invitación…").assertIsDisplayed()
    }

    @Test
    fun withoutAnOperationTheButtonsAreEnabled() {
        show(content(gomez))

        tag("invitation_accept_i-1").assertIsEnabled()
        tag("invitation_decline_i-1").assertIsEnabled()
    }

    @Test
    fun declineAsksForConfirmationAndWarnsAboutANewInvitation() {
        val holder = show(content(gomez, depto))

        tag("invitation_decline_i-1").performClick()

        rule.onNodeWithText("Rechazar la invitación a Familia Gómez").assertIsDisplayed()
        rule.onNodeWithText("Si cambias de opinión, necesitarás que te envíen una invitación nueva.").assertIsDisplayed()
        assertEquals(0, holder.confirmed)
        tag("invitations_decline_confirm").performClick()
        assertEquals(1, holder.confirmed)
    }

    @Test
    fun cancellingTheDeclineDoesNotConfirm() {
        val holder = show(content(gomez))

        tag("invitation_decline_i-1").performClick()
        tag("invitations_decline_cancel").performClick()

        assertEquals(1, holder.kept)
        assertEquals(0, holder.confirmed)
        assertTrue(rule.onAllNodesWithTag("invitations_decline_dialog").fetchSemanticsNodes().isEmpty())
    }

    // --- Avisos ---

    @Test
    fun theAcceptedNoticeOffersToSwitchToTheFamily() {
        val holder = show(content(depto).copy(event = InvitationsEvent.Accepted("f-1", "Familia Gómez")))

        rule.onNodeWithText("Ahora eres parte de Familia Gómez").assertIsDisplayed()
        rule.onNodeWithText("Cambiar a esta familia").performClick()

        rule.waitUntil(5_000) { holder.switched.isNotEmpty() }
        assertEquals(listOf("f-1"), holder.switched)
        rule.waitUntil(5_000) { holder.consumed == 1 }
    }

    @Test
    fun ignoringTheNoticeDoesNotSwitchTheFamily() {
        val holder = show(content(depto).copy(event = InvitationsEvent.Accepted("f-1", "Familia Gómez")))

        rule.onNodeWithText("Ahora eres parte de Familia Gómez").assertIsDisplayed()
        rule.waitUntil(15_000) { holder.consumed == 1 }

        assertTrue(holder.switched.isEmpty())
    }

    @Test
    fun aFailureShowsItsMessageAndIsConsumed() {
        val holder = show(content(gomez).copy(event = InvitationsEvent.Failed(FamilyAccessFailure.InvitationExpired)))

        rule.onNodeWithText("La invitación venció.").assertIsDisplayed()
        rule.waitUntil(8_000) { holder.consumed == 1 }
    }

    @Test
    fun theDeclinedNoticeIsShown() {
        show(InvitationsUiState(section = ReceivedSection.Empty, event = InvitationsEvent.Declined))

        rule.onNodeWithText("Invitación rechazada").assertIsDisplayed()
    }

    // --- Accesibilidad y navegación ---

    @Test
    fun theButtonsDescribeTheFamilyAndMeetTheTouchTarget() {
        show(content(gomez))

        rule.onNodeWithContentDescription("Aceptar la invitación a Familia Gómez").assertIsDisplayed()
        rule.onNodeWithContentDescription("Rechazar la invitación a Familia Gómez").assertIsDisplayed()
        tag("invitation_accept_i-1").assertTouchHeightIsEqualTo(48.dp)
        tag("invitation_decline_i-1").assertTouchHeightIsEqualTo(48.dp)
        tag("family_route_back").assertTouchHeightIsEqualTo(48.dp)
    }

    @Test
    fun theBackArrowAndTheBackGestureClose() {
        val holder = show(content(gomez))

        tag("family_route_back").performClick()
        assertEquals(1, holder.backs)
        pressBack()
        rule.waitForIdle()
        assertEquals(2, holder.backs)
    }

    @Test
    fun withTheLargestFontEverythingStaysReachable() {
        show(content(gomez.copy(familyName = "Familia Gómez de la Costa y Asociados del Sur"), depto), fontScale = 2f)

        tag("invitation_accept_i-1").performScrollTo().assertIsDisplayed()
        tag("invitation_decline_i-1").performScrollTo().assertIsDisplayed()
        tag("invitation_accept_i-2").performScrollTo().assertIsDisplayed()
        tag("invitation_decline_i-2").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theListSurvivesRecreation() {
        val restoration = StateRestorationTester(rule)
        val holder = Holder(content(gomez, depto).copy(pendingDecline = depto))
        restoration.setContent { FamilyFinAIAppTheme { Host(holder) } }

        restoration.emulateSavedInstanceStateRestore()

        tag("invitation_family_i-1").assertIsDisplayed()
        tag("invitation_family_i-2").assertIsDisplayed()
        rule.onNodeWithText("Rechazar la invitación a Departamento").assertIsDisplayed()
    }
}
