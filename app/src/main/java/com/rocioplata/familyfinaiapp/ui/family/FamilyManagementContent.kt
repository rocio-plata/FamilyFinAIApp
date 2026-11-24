package com.rocioplata.familyfinaiapp.ui.family

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rocioplata.familyfinaiapp.R
import com.rocioplata.familyfinaiapp.domain.family.CurrencyLockReason
import com.rocioplata.familyfinaiapp.domain.header.FamilyRole
import com.rocioplata.familyfinaiapp.ui.common.RoleBadge
import com.rocioplata.familyfinaiapp.ui.main.StaleDataBanner
import com.rocioplata.familyfinaiapp.ui.movements.initialsOf
import com.rocioplata.familyfinaiapp.ui.theme.FamilyFinAIAppTheme
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Lo que la sección de invitaciones enviadas y su formulario piden al ViewModel (RF-14 a RF-17). */
data class InvitationActions(
    val onInvite: () -> Unit = {},
    val onReinvite: (invitationId: String) -> Unit = {},
    val onRevoke: (invitationId: String) -> Unit = {},
    val onRemoveExpired: (invitationId: String) -> Unit = {},
    val onRetry: () -> Unit = {},
    val onEmailChange: (String) -> Unit = {},
    val onRoleChange: (FamilyRole) -> Unit = {},
    val onSubmit: () -> Unit = {},
    val onClose: () -> Unit = {},
    val onConfirmDiscard: () -> Unit = {},
    val onDismissDiscard: () -> Unit = {},
)

/**
 * «Mi familia» (RF-06 a RF-12): datos de la familia, miembros con las acciones que permite el rol, explicación de los roles
 * y «Salir de la familia». Sin estado: lo que ocurre lo decide el ViewModel. El Integrante ve los datos en solo lectura
 * (RF-08); un Propietario con conexión ve además el correo de cada miembro (RF-07).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyManagementContent(
    state: FamilyManagementUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onChangeRole: (userId: String) -> Unit,
    onRemove: (userId: String) -> Unit,
    onLeave: () -> Unit,
    onConfirmAction: () -> Unit,
    onDismissAction: () -> Unit,
    onEventConsumed: () -> Unit,
    modifier: Modifier = Modifier,
    onChangeCurrency: (code: String) -> Unit = {},
    onRetryCurrency: () -> Unit = {},
    invitations: InvitationActions = InvitationActions(),
) {
    val snackbar = remember { SnackbarHostState() }
    val event = state.event
    val eventText = when (event) {
        is FamilyManagementEvent.Failed -> stringResource(event.failure.messageRes())
        is FamilyManagementEvent.InvitationSent -> stringResource(R.string.family_invite_sent, event.email)
        null -> null
    }
    LaunchedEffect(event) {
        if (eventText != null) {
            snackbar.showSnackbar(eventText)
            onEventConsumed()
        }
    }
    val busyDescription = stringResource(R.string.family_manage_busy)

    Scaffold(
        modifier = modifier.testTag("family_manage_screen"),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.family_manage_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("family_manage_back")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.family_back_description),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            if (state.isBusy) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = busyDescription }.testTag("family_manage_busy"),
                )
            }
            val family = state.family
            if (family == null) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    FamilyHeaderCard(family)
                    CurrencyCard(state.currency, enabled = !state.isBusy, onChange = onChangeCurrency, onRetry = onRetryCurrency)
                    MembersSection(state, onRetry, onChangeRole, onRemove)
                    InvitationsSection(state, invitations)
                    RolesInfoCard()
                    LeaveSection(state, onLeave)
                }
            }
        }
    }

    state.pendingAction?.let { action ->
        ActionDialog(action = action, onConfirm = onConfirmAction, onDismiss = onDismissAction)
    }
    state.inviteForm?.let { form ->
        InviteMemberDialog(
            form = form,
            onEmailChange = invitations.onEmailChange,
            onRoleChange = invitations.onRoleChange,
            onSubmit = invitations.onSubmit,
            onClose = invitations.onClose,
            onConfirmDiscard = invitations.onConfirmDiscard,
            onDismissDiscard = invitations.onDismissDiscard,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FamilyHeaderCard(family: FamilyInfo) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = family.name,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() }.testTag("family_manage_name"),
            )
            FlowRow(
                itemVerticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val currencyText = family.currency ?: stringResource(R.string.family_manage_currency_unknown)
                val currencyDescription = family.currency?.let { stringResource(R.string.family_manage_currency_description, it) }
                    ?: currencyText
                Chip(
                    text = currencyText,
                    description = currencyDescription,
                    tag = "family_manage_currency",
                )
                family.memberCount?.let { count ->
                    val text = pluralStringResource(R.plurals.family_selector_members, count, count)
                    Chip(text = text, description = text, tag = "family_manage_count")
                }
            }
        }
    }
}

@Composable
private fun CurrencyCard(section: CurrencySection, enabled: Boolean, onChange: (String) -> Unit, onRetry: () -> Unit) {
    if (section == CurrencySection.Hidden) return
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("family_currency_section"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.family_currency_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            when (section) {
                CurrencySection.Hidden -> Unit
                CurrencySection.Loading -> {
                    val description = stringResource(R.string.family_currency_loading)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp).semantics { contentDescription = description }.testTag("family_currency_loading"),
                            strokeWidth = 2.dp,
                        )
                        Text(description, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                CurrencySection.Error -> Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.testTag("family_currency_error")) {
                    Text(stringResource(R.string.family_currency_error), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp).testTag("family_currency_retry")) {
                        Text(stringResource(R.string.family_currency_retry))
                    }
                }
                is CurrencySection.Content -> CurrencyContent(section, enabled, onChange)
            }
        }
    }
}

@Composable
private fun CurrencyContent(section: CurrencySection.Content, enabled: Boolean, onChange: (String) -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    val currentName = stringResource(currencyNameRes(section.current)).takeIf { section.current in knownCurrencies } ?: section.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = currentName,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f).testTag("family_currency_current"),
        )
        if (!section.isLocked) {
            val description = stringResource(R.string.family_currency_change_description, currentName)
            Box {
                OutlinedButton(
                    onClick = { menuOpen = true },
                    enabled = enabled,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = description }
                        .testTag("family_currency_change"),
                ) {
                    Text(stringResource(R.string.family_currency_change))
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    section.options.forEach { code ->
                        val selected = code == section.current
                        DropdownMenuItem(
                            text = { Text(stringResource(currencyNameRes(code))) },
                            leadingIcon = { if (selected) Icon(Icons.Filled.Check, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onChange(code)
                            },
                            modifier = Modifier.semantics { this.selected = selected }.testTag("family_currency_option_$code"),
                        )
                    }
                }
            }
        }
    }
    val note = when {
        !section.isLocked -> R.string.family_currency_hint
        section.lockReasons == setOf(CurrencyLockReason.MOVEMENTS) -> R.string.family_currency_locked_movements
        section.lockReasons == setOf(CurrencyLockReason.BUDGETS) -> R.string.family_currency_locked_budgets
        section.lockReasons.containsAll(listOf(CurrencyLockReason.MOVEMENTS, CurrencyLockReason.BUDGETS)) -> R.string.family_currency_locked_both
        else -> R.string.family_currency_locked_unknown
    }
    Text(
        text = stringResource(note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag(if (section.isLocked) "family_currency_locked" else "family_currency_hint"),
    )
}

private val knownCurrencies = setOf("CLP", "USD", "EUR")

private fun currencyNameRes(code: String): Int = when (code) {
    "USD" -> R.string.currency_name_usd
    "EUR" -> R.string.currency_name_eur
    else -> R.string.currency_name_clp
}

@Composable
private fun Chip(text: String, description: String, tag: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.semantics { contentDescription = description }.testTag(tag),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
    }
}

@Composable
private fun MembersSection(
    state: FamilyManagementUiState,
    onRetry: () -> Unit,
    onChangeRole: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.family_manage_members_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(
                if (state.isOwner) R.string.family_manage_members_subtitle_owner else R.string.family_manage_members_subtitle_member,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("family_manage_members_subtitle"),
        )
        when (val section = state.members) {
            MembersSection.Loading -> {
                val description = stringResource(R.string.family_manage_members_loading)
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.semantics { contentDescription = description }.testTag("family_manage_members_loading"),
                    )
                }
            }
            MembersSection.Error -> Column(
                modifier = Modifier.fillMaxWidth().testTag("family_manage_members_error"),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(stringResource(R.string.family_manage_members_error), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp).testTag("family_manage_members_retry")) {
                    Text(stringResource(R.string.family_manage_members_retry))
                }
            }
            is MembersSection.Content -> {
                if (section.isStale) StaleDataBanner(modifier = Modifier.testTag("family_manage_stale"))
                section.rows.forEach { row ->
                    MemberCard(row, enabled = !state.isBusy, onChangeRole = { onChangeRole(row.userId) }, onRemove = { onRemove(row.userId) })
                }
            }
        }
    }
}

@Composable
private fun InvitationsSection(state: FamilyManagementUiState, actions: InvitationActions) {
    val section = state.invitations
    if (section == InvitationsSection.Hidden) return
    Column(modifier = Modifier.testTag("family_invitations_section"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.family_invite_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f, fill = false).semantics { heading() },
            )
            if (section is InvitationsSection.Content) {
                val count = pluralStringResource(R.plurals.family_invite_count, section.activeCount, section.activeCount)
                Chip(text = count, description = count, tag = "family_invitations_count")
            }
        }
        when (section) {
            InvitationsSection.Hidden -> Unit
            InvitationsSection.Loading -> {
                val description = stringResource(R.string.family_invite_loading)
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.semantics { contentDescription = description }.testTag("family_invitations_loading"),
                    )
                }
            }
            is InvitationsSection.Error -> Column(
                modifier = Modifier.fillMaxWidth().testTag("family_invitations_error"),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(
                        if (section.failure == com.rocioplata.familyfinaiapp.domain.family.FamilyAccessFailure.Offline) {
                            R.string.family_invite_error_offline
                        } else {
                            R.string.family_invite_error
                        },
                    ),
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = actions.onRetry, modifier = Modifier.heightIn(min = 48.dp).testTag("family_invitations_retry")) {
                    Text(stringResource(R.string.family_invite_retry))
                }
            }
            is InvitationsSection.Content -> {
                if (section.rows.isEmpty()) {
                    Text(
                        text = stringResource(R.string.family_invite_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("family_invitations_empty"),
                    )
                }
                section.rows.forEach { row -> SentInvitationCard(row, enabled = !state.isBusy, actions = actions) }
            }
        }
        Button(
            onClick = actions.onInvite,
            enabled = state.canInvite && !state.isBusy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("family_invite_button"),
        ) {
            Icon(Icons.Outlined.PersonAdd, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.family_invite_button))
        }
    }
}

@Composable
private fun SentInvitationCard(row: SentInvitationRow, enabled: Boolean, actions: InvitationActions) {
    val roleLabel = stringResource(if (row.role == FamilyRole.OWNER) R.string.role_owner else R.string.role_member)
    val date = remember(row.expiresAt) { inviteDateFormatter().format(row.expiresAt) }
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("invitation_row_${row.id}"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(modifier = Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = row.email,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("invitation_email_${row.id}"),
                )
                FlowRow(
                    itemVerticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    RoleBadge(row.role, roleLabel)
                    if (row.isExpired) {
                        Chip(
                            text = stringResource(R.string.family_invite_expired_badge),
                            description = stringResource(R.string.family_invite_expired_badge),
                            tag = "invitation_expired_${row.id}",
                        )
                    }
                    Text(
                        text = stringResource(if (row.isExpired) R.string.family_invite_expired_on else R.string.family_invite_expires, date),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("invitation_date_${row.id}"),
                    )
                }
            }
            HorizontalDivider()
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                if (row.isExpired) {
                    val reinviteDescription = stringResource(R.string.family_invite_reinvite_description, row.email)
                    TextButton(
                        onClick = { actions.onReinvite(row.id) },
                        enabled = enabled,
                        modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = reinviteDescription }.testTag("invitation_reinvite_${row.id}"),
                    ) { Text(stringResource(R.string.family_invite_reinvite)) }
                    val removeDescription = stringResource(R.string.family_invite_remove_description, row.email)
                    TextButton(
                        onClick = { actions.onRemoveExpired(row.id) },
                        enabled = enabled,
                        modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = removeDescription }.testTag("invitation_remove_${row.id}"),
                    ) { Text(stringResource(R.string.family_invite_remove)) }
                } else {
                    Spacer(Modifier.width(1.dp))
                    val revokeDescription = stringResource(R.string.family_invite_revoke_description, row.email)
                    TextButton(
                        onClick = { actions.onRevoke(row.id) },
                        enabled = enabled,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = revokeDescription }.testTag("invitation_revoke_${row.id}"),
                    ) { Text(stringResource(R.string.family_invite_revoke)) }
                }
            }
        }
    }
}

// La fecha con el formato y la zona del dispositivo (RF-15); `FormatStyle.MEDIUM` da «29 nov 2026» en español.
private fun inviteDateFormatter(): DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault()).withZone(ZoneId.systemDefault())

@Composable
private fun MemberCard(row: MemberRow, enabled: Boolean, onChangeRole: () -> Unit, onRemove: () -> Unit) {
    val name = row.displayName?.trim()?.takeIf { it.isNotEmpty() } ?: stringResource(R.string.movements_recorded_by_generic)
    val roleLabel = stringResource(if (row.role == FamilyRole.OWNER) R.string.role_owner else R.string.role_member)
    val you = stringResource(R.string.family_manage_you)
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("member_row_${row.userId}"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Lo que se anuncia de la persona es una sola unidad; las acciones son controles aparte.
            Row(
                modifier = Modifier.semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Avatar(initialsOf(row.displayName))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = if (row.isYou) "$name ($you)" else name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("member_name_${row.userId}"),
                    )
                    RoleBadge(row.role, roleLabel, modifier = Modifier.testTag("member_role_${row.userId}"))
                    row.email?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("member_email_${row.userId}"),
                        )
                    }
                }
            }
            if (row.canChangeRole || row.canRemove) {
                HorizontalDivider()
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    if (row.canChangeRole) {
                        val changeDescription = stringResource(R.string.family_manage_change_role_description, name)
                        TextButton(
                            onClick = onChangeRole,
                            enabled = enabled,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .semantics { contentDescription = changeDescription }
                                .testTag("member_change_${row.userId}"),
                        ) {
                            Icon(Icons.Outlined.SwapHoriz, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.family_manage_change_role))
                        }
                    } else {
                        Spacer(Modifier.width(1.dp))
                    }
                    if (row.canRemove) {
                        val removeDescription = stringResource(R.string.family_manage_remove_description, name)
                        TextButton(
                            onClick = onRemove,
                            enabled = enabled,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .semantics { contentDescription = removeDescription }
                                .testTag("member_remove_${row.userId}"),
                        ) {
                            Icon(Icons.Outlined.PersonRemove, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.family_manage_remove))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Avatar(initials: String?) {
    Box(
        modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (initials != null) {
            Text(text = initials, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
        } else {
            Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

@Composable
private fun RolesInfoCard() {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("family_manage_roles_info"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.family_manage_roles_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.semantics { heading() },
            )
            Text(stringResource(R.string.family_manage_roles_owner), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.family_manage_roles_member), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun LeaveSection(state: FamilyManagementUiState, onLeave: () -> Unit) {
    val reason = when (state.leave) {
        LeaveState.Allowed -> null
        LeaveState.OnlyFamily -> R.string.family_manage_leave_only_family
        LeaveState.OnlyOwner -> R.string.family_manage_leave_only_owner
        LeaveState.NeedsConnection -> R.string.family_manage_leave_needs_connection
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedButton(
            onClick = onLeave,
            enabled = state.leave == LeaveState.Allowed && !state.isBusy,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("family_manage_leave"),
        ) {
            Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.family_manage_leave))
        }
        reason?.let {
            Text(
                text = stringResource(it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp).testTag("family_manage_leave_reason"),
            )
        }
    }
}

@Composable
private fun ActionDialog(action: FamilyPendingAction, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val generic = stringResource(R.string.movements_recorded_by_generic)
    fun name(displayName: String?) = displayName?.trim()?.takeIf { it.isNotEmpty() } ?: generic
    val (title, message, confirm) = when (action) {
        is FamilyPendingAction.ChangeRole -> Triple(
            stringResource(R.string.family_manage_role_title),
            when {
                action.isYou -> stringResource(R.string.family_manage_role_self_to_member)
                action.newRole == FamilyRole.OWNER -> stringResource(R.string.family_manage_role_to_owner, name(action.displayName))
                else -> stringResource(R.string.family_manage_role_to_member, name(action.displayName))
            },
            stringResource(R.string.family_manage_role_confirm),
        )
        is FamilyPendingAction.Remove -> Triple(
            stringResource(R.string.family_manage_remove_title, name(action.displayName)),
            stringResource(R.string.family_manage_remove_message),
            stringResource(R.string.family_manage_remove_confirm),
        )
        is FamilyPendingAction.Leave -> Triple(
            stringResource(R.string.family_manage_leave_title, action.familyName),
            stringResource(R.string.family_manage_leave_message),
            stringResource(R.string.family_manage_leave_confirm),
        )
        is FamilyPendingAction.RevokeInvitation -> Triple(
            stringResource(R.string.family_invite_revoke_title),
            stringResource(R.string.family_invite_revoke_message, action.email),
            stringResource(R.string.family_invite_revoke_confirm),
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("family_manage_confirm")) { Text(confirm) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("family_manage_cancel")) {
                Text(stringResource(R.string.family_manage_dialog_cancel))
            }
        },
    )
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun FamilyManagementContentPreview() {
    FamilyFinAIAppTheme {
        FamilyManagementContent(
            state = FamilyManagementUiState(
                family = FamilyInfo("Familia Plata", "CLP", FamilyRole.OWNER, 3),
                isOwner = true,
                members = MembersSection.Content(
                    rows = listOf(
                        MemberRow("u-1", "Valentina Plata", FamilyRole.OWNER, true, "valentina@example.com", FamilyRole.MEMBER, false, false),
                        MemberRow("u-2", "Carlos Mendoza", FamilyRole.OWNER, false, "carlos@example.com", FamilyRole.MEMBER, true, true),
                        MemberRow("u-3", null, FamilyRole.MEMBER, false, null, FamilyRole.OWNER, true, true),
                    ),
                    isStale = false,
                    isRefreshing = false,
                ),
                leave = LeaveState.OnlyOwner,
            ),
            onBack = {}, onRetry = {}, onChangeRole = {}, onRemove = {}, onLeave = {},
            onConfirmAction = {}, onDismissAction = {}, onEventConsumed = {},
        )
    }
}
