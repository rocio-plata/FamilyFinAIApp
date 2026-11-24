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
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.assertHeightIsAtLeast
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
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rocioplata.familyfinaiapp.domain.family.FamilyAccessFailure
import com.rocioplata.familyfinaiapp.domain.header.FamilyRole
import com.rocioplata.familyfinaiapp.ui.theme.FamilyFinAIAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FamilyManagementContentTest {
    @get:Rule
    val rule = createComposeRule()

    private class Holder(initial: FamilyManagementUiState) {
        var state by mutableStateOf(initial)
        var backs = 0
        var retries = 0
        val roleRequests = mutableListOf<String>()
        val removeRequests = mutableListOf<String>()
        var leaves = 0
        var confirms = 0
        var dismisses = 0
        var consumed = 0
    }

    private val valentina = MemberRow("u-1", "Valentina Plata", FamilyRole.OWNER, isYou = true, email = "valentina@example.com", newRole = FamilyRole.MEMBER, canChangeRole = true, canRemove = false)
    private val carlos = MemberRow("u-2", "Carlos Mendoza", FamilyRole.OWNER, isYou = false, email = "carlos@example.com", newRole = FamilyRole.MEMBER, canChangeRole = true, canRemove = true)
    private val sofia = MemberRow("u-3", "Sofía Silva", FamilyRole.MEMBER, isYou = false, email = "sofia@example.com", newRole = FamilyRole.OWNER, canChangeRole = true, canRemove = true)
    private val sinNombre = MemberRow("u-4", null, FamilyRole.MEMBER, isYou = false, email = null, newRole = FamilyRole.OWNER, canChangeRole = true, canRemove = true)

    private fun owner(
        rows: List<MemberRow> = listOf(valentina, carlos, sofia),
        leave: LeaveState = LeaveState.Allowed,
        members: MembersSection = MembersSection.Content(rows, isStale = false, isRefreshing = false),
        pending: FamilyPendingAction? = null,
        busy: Boolean = false,
        event: FamilyManagementEvent? = null,
    ) = FamilyManagementUiState(
        family = FamilyInfo("Familia Plata", "CLP", FamilyRole.OWNER, memberCount = rows.size),
        isOwner = true, members = members, leave = leave, pendingAction = pending, isBusy = busy, event = event,
    )

    /** Un Integrante: la misma lista, sin correos ni acciones (RF-07, RF-08). */
    private fun member(leave: LeaveState = LeaveState.Allowed) = FamilyManagementUiState(
        family = FamilyInfo("Depto", "CLP", FamilyRole.MEMBER, memberCount = 2),
        isOwner = false,
        members = MembersSection.Content(
            listOf(
                carlos.copy(email = null, canChangeRole = false, canRemove = false),
                sofia.copy(isYou = true, email = null, canChangeRole = false, canRemove = false),
            ),
            isStale = false, isRefreshing = false,
        ),
        leave = leave,
    )

    private fun show(state: FamilyManagementUiState, fontScale: Float? = null): Holder {
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
        FamilyManagementContent(
            state = holder.state,
            onBack = { holder.backs++ },
            onRetry = { holder.retries++ },
            onChangeRole = { holder.roleRequests += it },
            onRemove = { holder.removeRequests += it },
            onLeave = { holder.leaves++ },
            onConfirmAction = { holder.confirms++ },
            onDismissAction = { holder.dismisses++ },
            onEventConsumed = { holder.consumed++; holder.state = holder.state.copy(event = null) },
        )
    }

    private fun tag(tag: String) = rule.onNodeWithTag(tag, useUnmergedTree = true)

    // --- Datos ---

    @Test
    fun showsTheNameTheCurrencyAndTheMemberCount() {
        show(owner())

        tag("family_manage_name").assertTextEquals("Familia Plata")
        tag("family_manage_currency").assertIsDisplayed()
        rule.onNodeWithContentDescription("Moneda: CLP").assertIsDisplayed()
        rule.onNodeWithContentDescription("3 miembros").assertIsDisplayed()
    }

    @Test
    fun aSingleMemberUsesTheSingular_andAMissingCurrencySaysSo() {
        val state = owner(rows = listOf(valentina)).let { it.copy(family = it.family!!.copy(currency = null, memberCount = 1)) }
        show(state)

        rule.onNodeWithContentDescription("1 miembro").assertIsDisplayed()
        rule.onNodeWithContentDescription("Moneda sin definir").assertIsDisplayed()
    }

    @Test
    fun theMembersShowNameRoleAndYou_withAGenericNameWhenThereIsNone() {
        show(owner(rows = listOf(valentina, carlos, sofia, sinNombre)))

        tag("member_name_u-1").assertTextEquals("Valentina Plata (Tú)")
        tag("member_name_u-2").assertTextEquals("Carlos Mendoza")
        tag("member_name_u-4").assertTextEquals("Miembro de la familia")
        tag("member_role_u-1").assertIsDisplayed()
        // Dos Propietarios (Valentina y Carlos) y dos Integrantes (Sofía y quien no tiene nombre).
        rule.onAllNodesWithText("Propietario", useUnmergedTree = true).assertCountEquals(2)
        rule.onAllNodesWithText("Integrante", useUnmergedTree = true).assertCountEquals(2)
    }

    @Test
    fun theOwnerSeesTheEmails_andAMemberWithoutEmailShowsNone() {
        show(owner(rows = listOf(valentina, carlos, sinNombre)))

        tag("member_email_u-1").assertTextEquals("valentina@example.com")
        tag("member_email_u-2").assertTextEquals("carlos@example.com")
        rule.onAllNodesWithTag("member_email_u-4", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun theRolesAreExplained() {
        show(owner())

        tag("family_manage_roles_info").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Tipos de roles").assertExists()
    }

    // --- Solo lectura (RF-08) ---

    @Test
    fun aMemberSeesTheDataWithoutEmailsNorActions_andCanOnlyLeave() {
        show(member())

        tag("member_name_u-2").assertTextEquals("Carlos Mendoza")
        rule.onAllNodesWithTag("member_email_u-2", useUnmergedTree = true).assertCountEquals(0)
        rule.onAllNodesWithTag("member_change_u-2", useUnmergedTree = true).assertCountEquals(0)
        rule.onAllNodesWithTag("member_remove_u-2", useUnmergedTree = true).assertCountEquals(0)
        tag("family_manage_members_subtitle").assertTextEquals("Solo los Propietarios pueden gestionar roles y acceso.")
        tag("family_manage_leave").performScrollTo().assertIsEnabled()
    }

    @Test
    fun theOwnerSeesItsOwnSubtitle() {
        show(owner())

        tag("family_manage_members_subtitle").assertTextEquals("Como Propietario puedes gestionar roles y acceso.")
    }

    // --- Acciones por miembro ---

    @Test
    fun eachRowOffersOnlyWhatItAllows() {
        show(owner(rows = listOf(valentina, carlos, sofia).map { if (it.userId == "u-1") it.copy(canChangeRole = false) else it }))

        rule.onAllNodesWithTag("member_change_u-1", useUnmergedTree = true).assertCountEquals(0)
        rule.onAllNodesWithTag("member_remove_u-1", useUnmergedTree = true).assertCountEquals(0)
        tag("member_change_u-2").assertIsDisplayed()
        tag("member_remove_u-2").assertIsDisplayed()
    }

    @Test
    fun theActionsAskForTheirMember() {
        val holder = show(owner())

        tag("member_change_u-3").performScrollTo().performClick()
        tag("member_remove_u-2").performScrollTo().performClick()

        assertEquals(listOf("u-3"), holder.roleRequests)
        assertEquals(listOf("u-2"), holder.removeRequests)
    }

    @Test
    fun theActionsNameTheirMemberForTheScreenReader() {
        show(owner())

        rule.onNodeWithContentDescription("Cambiar el rol de Carlos Mendoza").assertExists()
        rule.onNodeWithContentDescription("Quitar a Sofía Silva").assertExists()
    }

    @Test
    fun whileBusyTheActionsAreDisabled_andThereIsProgress() {
        show(owner(busy = true))

        tag("member_change_u-2").assertIsNotEnabled()
        tag("member_remove_u-2").assertIsNotEnabled()
        tag("family_manage_leave").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithContentDescription("Guardando el cambio…").assertIsDisplayed()
    }

    // --- Salir ---

    @Test
    fun leavingAsksToLeave() {
        val holder = show(owner())

        tag("family_manage_leave").performScrollTo().assertIsEnabled().performClick()

        assertEquals(1, holder.leaves)
    }

    private fun assertLeaveDisabledWith(leave: LeaveState, reason: String) {
        show(owner(leave = leave))

        tag("family_manage_leave").performScrollTo().assertIsNotEnabled()
        tag("family_manage_leave_reason").assertTextEquals(reason)
    }

    @Test
    fun withASingleFamilyLeavingIsDisabledWithItsReason() =
        assertLeaveDisabledWith(LeaveState.OnlyFamily, "No puedes salir de tu única familia. Crea otra o acepta una invitación antes.")

    @Test
    fun asTheOnlyOwnerLeavingIsDisabledWithItsReason() =
        assertLeaveDisabledWith(LeaveState.OnlyOwner, "Eres el único Propietario. Nombra a otro Propietario antes de salir.")

    @Test
    fun withoutTheMemberListAnOwnerNeedsConnectionToLeave() =
        assertLeaveDisabledWith(LeaveState.NeedsConnection, "Requiere conexión para comprobar si eres el único Propietario.")

    @Test
    fun whenLeavingIsAllowedThereIsNoReason() {
        show(owner())

        rule.onAllNodesWithTag("family_manage_leave_reason", useUnmergedTree = true).assertCountEquals(0)
    }

    // --- Secciones ---

    @Test
    fun whileTheMembersLoad_theRestOfTheScreenStaysVisible() {
        show(owner(members = MembersSection.Loading))

        rule.onNodeWithContentDescription("Cargando miembros…").assertIsDisplayed()
        tag("family_manage_name").assertIsDisplayed()
        tag("family_manage_leave").performScrollTo().assertExists()
    }

    @Test
    fun aMembersErrorOffersRetry_withoutHidingTheRest() {
        val holder = show(owner(members = MembersSection.Error))

        rule.onNodeWithText("No se pudieron cargar los miembros.").assertIsDisplayed()
        tag("family_manage_name").assertIsDisplayed()
        tag("family_manage_members_retry").performClick()

        assertEquals(1, holder.retries)
    }

    @Test
    fun staleMembersShowTheOutdatedWarning_andFreshOnesDoNot() {
        show(owner(members = MembersSection.Content(listOf(valentina), isStale = true, isRefreshing = false)))
        tag("family_manage_stale").assertIsDisplayed()
    }

    @Test
    fun freshMembersShowNoWarning() {
        show(owner())

        rule.onAllNodesWithTag("family_manage_stale", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun withoutFamilyDataOnlyAProgressIsShown() {
        show(FamilyManagementUiState(family = null))

        rule.onAllNodesWithTag("family_manage_name", useUnmergedTree = true).assertCountEquals(0)
        rule.onAllNodesWithTag("family_manage_leave", useUnmergedTree = true).assertCountEquals(0)
    }

    // --- Confirmaciones ---

    @Test
    fun theConfirmationsSayWhatWillHappen() {
        val cases = listOf(
            FamilyPendingAction.ChangeRole("u-3", "Sofía Silva", FamilyRole.OWNER, isYou = false) to
                "Sofía Silva pasará a ser Propietario: podrá invitar, quitar miembros y cambiar la moneda.",
            FamilyPendingAction.ChangeRole("u-2", "Carlos Mendoza", FamilyRole.MEMBER, isYou = false) to
                "Carlos Mendoza pasará a ser Integrante y dejará de poder gestionar la familia.",
            FamilyPendingAction.ChangeRole("u-1", "Valentina Plata", FamilyRole.MEMBER, isYou = true) to
                "Pasarás a ser Integrante y dejarás de poder gestionar la familia. Para volver a serlo, tendrá que cambiártelo otro Propietario.",
            FamilyPendingAction.Remove("u-3", "Sofía Silva") to "Perderá el acceso a la familia. Los movimientos que registró se conservan.",
            FamilyPendingAction.Leave("Familia Plata") to "Perderás el acceso a esta familia. Los movimientos que registraste se conservan en ella.",
        )
        val holder = Holder(owner())
        rule.setContent { FamilyFinAIAppTheme { Host(holder) } }

        cases.forEach { (action, message) ->
            holder.state = owner(pending = action)
            rule.waitForIdle()
            rule.onNodeWithText(message).assertIsDisplayed()
            tag("family_manage_confirm").assertIsDisplayed()
            holder.state = owner()
            rule.waitForIdle()
        }
    }

    @Test
    fun theRemoveAndLeaveConfirmationsNameWhatTheyAffect() {
        val holder = Holder(owner(pending = FamilyPendingAction.Remove("u-3", "Sofía Silva")))
        rule.setContent { FamilyFinAIAppTheme { Host(holder) } }
        rule.onNodeWithText("Quitar a Sofía Silva").assertIsDisplayed()

        holder.state = owner(pending = FamilyPendingAction.Leave("Familia Plata"))
        rule.waitForIdle()
        rule.onNodeWithText("Salir de Familia Plata").assertIsDisplayed()

        holder.state = owner(pending = FamilyPendingAction.Remove("u-4", null))
        rule.waitForIdle()
        rule.onNodeWithText("Quitar a Miembro de la familia").assertIsDisplayed()
    }

    @Test
    fun confirmingAndCancellingCallTheirCallbacks() {
        val holder = show(owner(pending = FamilyPendingAction.Remove("u-3", "Sofía Silva")))

        tag("family_manage_cancel").performClick()
        tag("family_manage_confirm").performClick()

        assertEquals(1, holder.dismisses)
        assertEquals(1, holder.confirms)
    }

    @Test
    fun theConfirmationSurvivesRecreation() {
        val holder = Holder(owner(pending = FamilyPendingAction.Leave("Familia Plata")))
        val restoration = StateRestorationTester(rule)
        restoration.setContent { FamilyFinAIAppTheme { Host(holder) } }

        restoration.emulateSavedInstanceStateRestore()

        rule.onNodeWithText("Salir de Familia Plata").assertIsDisplayed()
        tag("family_manage_name").assertTextEquals("Familia Plata")
    }

    // --- Avisos ---

    @Test
    fun aFailureIsShownOnceAndConfirmed() {
        val holder = show(owner(event = FamilyManagementEvent.Failed(FamilyAccessFailure.LastOwner)))

        rule.onNodeWithText("La familia debe tener al menos un Propietario. Nombra a otro antes.").assertIsDisplayed()
        // Se confirma cuando el aviso deja de mostrarse (no antes: confirmarlo lo quitaría del estado).
        rule.waitUntil(timeoutMillis = 15_000) { holder.consumed == 1 }
        assertEquals(1, holder.consumed)
    }

    // --- Navegación, accesibilidad y tamaño ---

    @Test
    fun theBackArrowAsksToGoBack() {
        val holder = show(owner())

        tag("family_manage_back").performClick()

        assertEquals(1, holder.backs)
    }

    @Test
    fun touchTargetsAreAtLeast48dp() {
        show(owner(members = MembersSection.Content(listOf(carlos), isStale = false, isRefreshing = false)))

        tag("family_manage_back").assertTouchHeightIsEqualTo(48.dp)
        tag("member_change_u-2").assertHeightIsAtLeast(48.dp)
        tag("member_remove_u-2").assertHeightIsAtLeast(48.dp)
        tag("family_manage_leave").performScrollTo().assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun aMembersRetryIsAtLeast48dp() {
        show(owner(members = MembersSection.Error))

        tag("family_manage_members_retry").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun withTheLargestTextAndALongNameEverythingIsReachable() {
        val longName = MemberRow(
            "u-9", "Alejandra Fernanda de los Ángeles González Fernández de la Torre", FamilyRole.MEMBER, false,
            "alejandra.fernanda.gonzalez.fernandez.de.la.torre@example.com", FamilyRole.OWNER, true, true,
        )
        show(owner(rows = listOf(valentina, longName), leave = LeaveState.OnlyOwner), fontScale = 2f)

        tag("member_change_u-9").performScrollTo().assertIsDisplayed()
        tag("member_remove_u-9").performScrollTo().assertIsDisplayed()
        tag("family_manage_leave").performScrollTo().assertIsDisplayed()
        tag("family_manage_leave_reason").performScrollTo().assertIsDisplayed()
    }
}
