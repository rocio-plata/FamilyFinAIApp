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
        val currencyChanges = mutableListOf<String>()
        var currencyRetries = 0
        var invites = 0
        val reinvites = mutableListOf<String>()
        val revokes = mutableListOf<String>()
        val removals = mutableListOf<String>()
        var invitationRetries = 0
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
            onChangeCurrency = { holder.currencyChanges += it },
            onRetryCurrency = { holder.currencyRetries++ },
            invitations = InvitationActions(
                onInvite = { holder.invites++ },
                onReinvite = { holder.reinvites += it },
                onRevoke = { holder.revokes += it },
                onRemoveExpired = { holder.removals += it },
                onRetry = { holder.invitationRetries++ },
            ),
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

    // --- Moneda (T-24) ---

    private fun unlocked(current: String = "CLP") =
        CurrencySection.Content(current, listOf("CLP", "USD", "EUR"), isLocked = false, lockReasons = emptySet())

    private fun lockedBy(vararg reasons: com.rocioplata.familyfinaiapp.domain.family.CurrencyLockReason) =
        CurrencySection.Content("CLP", listOf("CLP", "USD", "EUR"), isLocked = true, lockReasons = reasons.toSet())

    @Test
    fun theOwnerSeesTheCurrencyWithAWayToChangeIt() {
        show(owner().copy(currency = unlocked()))

        tag("family_currency_section").assertIsDisplayed()
        tag("family_currency_current").assertTextEquals("Peso chileno (CLP)")
        tag("family_currency_change").assertIsEnabled()
        tag("family_currency_hint").assertTextEquals("Puedes cambiarla mientras no haya movimientos ni presupuestos activos.")
    }

    @Test
    fun aMemberDoesNotSeeTheCurrencySection() {
        show(member())

        rule.onAllNodesWithTag("family_currency_section", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun theMenuListsTheThreeCurrencies_andChoosingOneAsksForIt() {
        val holder = show(owner().copy(currency = unlocked()))

        tag("family_currency_change").performClick()
        // El peso aparece dos veces: como moneda actual y como opción del menú.
        rule.onAllNodesWithText("Peso chileno (CLP)", useUnmergedTree = true).assertCountEquals(2)
        rule.onNodeWithText("Dólar estadounidense (USD)").assertIsDisplayed()
        rule.onNodeWithText("Euro (EUR)").assertIsDisplayed()
        tag("family_currency_option_USD").performClick()

        assertEquals(listOf("USD"), holder.currencyChanges)
    }

    @Test
    fun theCurrentCurrencyIsShownByName_andTheChangeButtonSaysWhichIsTheCurrent() {
        show(owner().copy(currency = unlocked("USD")))

        tag("family_currency_current").assertTextEquals("Dólar estadounidense (USD)")
        rule.onNodeWithContentDescription("Cambiar la moneda de la familia, ahora Dólar estadounidense (USD)").assertExists()
    }

    private fun assertLocked(section: CurrencySection, text: String) {
        show(owner().copy(currency = section))

        tag("family_currency_locked").assertTextEquals(text)
        rule.onAllNodesWithTag("family_currency_change", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun lockedByMovementsShowsItsReason() = assertLocked(
        lockedBy(com.rocioplata.familyfinaiapp.domain.family.CurrencyLockReason.MOVEMENTS),
        "No puedes cambiar la moneda porque la familia tiene movimientos registrados.",
    )

    @Test
    fun lockedByBudgetsShowsItsReason() = assertLocked(
        lockedBy(com.rocioplata.familyfinaiapp.domain.family.CurrencyLockReason.BUDGETS),
        "No puedes cambiar la moneda porque hay presupuestos activos.",
    )

    @Test
    fun lockedByBothShowsBothReasons() = assertLocked(
        lockedBy(*com.rocioplata.familyfinaiapp.domain.family.CurrencyLockReason.entries.toTypedArray()),
        "No puedes cambiar la moneda porque la familia tiene movimientos registrados y presupuestos activos.",
    )

    @Test
    fun lockedWithAnUnknownReasonStillSaysItIsLocked() = assertLocked(
        lockedBy(),
        "No puedes cambiar la moneda en este momento.",
    )

    @Test
    fun whileTheCurrencyIsChecked_thereIsProgress_andTheRestStaysVisible() {
        show(owner().copy(currency = CurrencySection.Loading))

        rule.onNodeWithContentDescription("Comprobando si se puede cambiar la moneda…").assertIsDisplayed()
        tag("member_row_u-2").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aCurrencyErrorOffersRetry_withoutHidingTheMembers() {
        val holder = show(owner().copy(currency = CurrencySection.Error))

        rule.onNodeWithText("No se pudo comprobar si se puede cambiar la moneda.").assertIsDisplayed()
        tag("family_currency_retry").performClick()

        assertEquals(1, holder.currencyRetries)
        tag("member_row_u-2").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun whileBusyTheCurrencyCannotBeChanged() {
        show(owner(busy = true).copy(currency = unlocked()))

        tag("family_currency_change").assertIsNotEnabled()
    }

    @Test
    fun theCurrencyControlsAreAtLeast48dp_andReachableWithTheLargestText() {
        show(owner().copy(currency = unlocked()), fontScale = 2f)

        tag("family_currency_change").performScrollTo().assertHeightIsAtLeast(48.dp).assertIsDisplayed()
        tag("family_currency_hint").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theRetryOfTheCurrencyIsAtLeast48dp() {
        show(owner().copy(currency = CurrencySection.Error))

        tag("family_currency_retry").assertHeightIsAtLeast(48.dp)
    }

    // --- Invitaciones enviadas (T-26) ---

    private val expiresAt = java.time.Instant.parse("2026-11-29T12:00:00Z")
    private val expiredAt = java.time.Instant.parse("2026-11-15T12:00:00Z")
    private val vigente = SentInvitationRow("i-1", "hermano@example.com", FamilyRole.MEMBER, expiresAt, isExpired = false)
    private val vigenteOwner = SentInvitationRow("i-2", "tia@example.com", FamilyRole.OWNER, expiresAt, isExpired = false)
    private val vencida = SentInvitationRow("i-3", "primo@example.com", FamilyRole.MEMBER, expiredAt, isExpired = true)

    private fun withInvitations(vararg rows: SentInvitationRow, busy: Boolean = false) =
        owner(busy = busy).copy(invitations = InvitationsSection.Content(rows.toList(), rows.count { !it.isExpired }))

    private fun dateOf(instant: java.time.Instant) =
        java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)
            .withLocale(java.util.Locale.getDefault()).withZone(java.time.ZoneId.systemDefault()).format(instant)

    @Test
    fun theInvitationsShowEmailRoleCountAndExpiry() {
        show(withInvitations(vigente, vigenteOwner))

        tag("family_invitations_count").assertIsDisplayed()
        rule.onNodeWithContentDescription("2 pendientes").assertExists()
        tag("invitation_email_i-1").assertTextEquals("hermano@example.com")
        tag("invitation_date_i-1").assertTextEquals("Vence el ${dateOf(expiresAt)}")
        tag("invitation_email_i-2").assertTextEquals("tia@example.com")
    }

    @Test
    fun theCountUsesTheSingular_andExpiredOnesDoNotCount() {
        show(withInvitations(vigente, vencida))

        rule.onNodeWithContentDescription("1 pendiente").assertExists()
    }

    @Test
    fun anExpiredInvitationIsMarked_withItsOwnActions() {
        val holder = show(withInvitations(vencida))

        tag("invitation_expired_i-3").assertIsDisplayed()
        tag("invitation_date_i-3").assertTextEquals("Venció el ${dateOf(expiredAt)}")
        rule.onAllNodesWithTag("invitation_revoke_i-3", useUnmergedTree = true).assertCountEquals(0)
        tag("invitation_reinvite_i-3").performScrollTo().performClick()
        tag("invitation_remove_i-3").performScrollTo().performClick()

        assertEquals(listOf("i-3"), holder.reinvites)
        assertEquals(listOf("i-3"), holder.removals)
    }

    @Test
    fun aValidInvitationCanOnlyBeRevoked() {
        val holder = show(withInvitations(vigente))

        rule.onAllNodesWithTag("invitation_reinvite_i-1", useUnmergedTree = true).assertCountEquals(0)
        rule.onAllNodesWithTag("invitation_remove_i-1", useUnmergedTree = true).assertCountEquals(0)
        tag("invitation_expired_i-1").assertDoesNotExistInTree()
        tag("invitation_revoke_i-1").performScrollTo().performClick()

        assertEquals(listOf("i-1"), holder.revokes)
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertDoesNotExistInTree() = assertDoesNotExist()

    @Test
    fun theInvitationActionsNameTheirEmailForTheScreenReader() {
        show(withInvitations(vigente, vencida))

        rule.onNodeWithContentDescription("Revocar la invitación a hermano@example.com").assertExists()
        rule.onNodeWithContentDescription("Volver a invitar a primo@example.com").assertExists()
        rule.onNodeWithContentDescription("Quitar la invitación vencida a primo@example.com").assertExists()
    }

    @Test
    fun withoutInvitationsThereIsAnEmptyMessage_andInvitingIsAvailable() {
        val holder = show(withInvitations())

        tag("family_invitations_empty").assertTextEquals("No hay invitaciones pendientes.")
        tag("family_invite_button").performScrollTo().assertIsEnabled().performClick()

        assertEquals(1, holder.invites)
    }

    @Test
    fun whileTheInvitationsLoad_invitingIsNotAvailable() {
        show(owner().copy(invitations = InvitationsSection.Loading))

        rule.onNodeWithContentDescription("Cargando invitaciones…").assertExists()
        tag("family_invite_button").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun anInvitationsErrorOffersRetry_andSaysWhenItIsTheConnection() {
        val holder = show(owner().copy(invitations = InvitationsSection.Error(FamilyAccessFailure.Offline)))

        rule.onNodeWithText("Las invitaciones requieren conexión.").performScrollTo().assertIsDisplayed()
        tag("family_invite_button").performScrollTo().assertIsNotEnabled()
        tag("family_invitations_retry").performClick()

        assertEquals(1, holder.invitationRetries)
        tag("member_row_u-2").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun anotherInvitationsErrorUsesTheGenericText() {
        show(owner().copy(invitations = InvitationsSection.Error(FamilyAccessFailure.Network)))

        rule.onNodeWithText("No se pudieron cargar las invitaciones.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aMemberDoesNotSeeTheInvitations() {
        show(member())

        rule.onAllNodesWithTag("family_invitations_section", useUnmergedTree = true).assertCountEquals(0)
        rule.onAllNodesWithTag("family_invite_button", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun whileBusyTheInvitationActionsAreDisabled() {
        show(withInvitations(vigente, vencida, busy = true))

        tag("invitation_revoke_i-1").performScrollTo().assertIsNotEnabled()
        tag("invitation_reinvite_i-3").performScrollTo().assertIsNotEnabled()
        tag("invitation_remove_i-3").performScrollTo().assertIsNotEnabled()
        tag("family_invite_button").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun revokingAsksForConfirmationNamingTheEmail() {
        val holder = show(withInvitations(vigente).copy(pendingAction = FamilyPendingAction.RevokeInvitation("i-1", "hermano@example.com")))

        rule.onNodeWithText("Revocar la invitación").assertIsDisplayed()
        rule.onNodeWithText("La invitación a hermano@example.com dejará de valer. Podrás volver a invitar a esa persona cuando quieras.")
            .assertIsDisplayed()
        tag("family_manage_confirm").performClick()
        assertEquals(1, holder.confirms)
    }

    @Test
    fun anInvitationSentShowsTheNoEmailNotice() {
        show(withInvitations(vigente).copy(event = FamilyManagementEvent.InvitationSent("nuevo@example.com")))

        rule.onNodeWithText(
            "Invitación enviada a nuevo@example.com. No se envía ningún correo: la verá al entrar en la app con ese correo y vence en 7 días.",
        ).assertIsDisplayed()
    }

    @Test
    fun theOpenInviteFormIsShownOverTheScreen() {
        show(withInvitations().copy(inviteForm = InviteFormState(email = "x@y.cl", role = FamilyRole.MEMBER)))

        rule.onNodeWithTag("invite_email").assertIsDisplayed()
        // «Invitar miembro» es el botón de la pantalla y el título del diálogo.
        rule.onAllNodesWithText("Invitar miembro", useUnmergedTree = true).assertCountEquals(2)
    }

    @Test
    fun theInvitationControlsAreAtLeast48dp_andReachableWithTheLargestText() {
        show(withInvitations(vigente, vencida), fontScale = 2f)

        tag("invitation_revoke_i-1").performScrollTo().assertHeightIsAtLeast(48.dp).assertIsDisplayed()
        tag("invitation_reinvite_i-3").performScrollTo().assertHeightIsAtLeast(48.dp).assertIsDisplayed()
        tag("invitation_remove_i-3").performScrollTo().assertHeightIsAtLeast(48.dp).assertIsDisplayed()
        tag("family_invite_button").performScrollTo().assertHeightIsAtLeast(48.dp).assertIsDisplayed()
    }
}
