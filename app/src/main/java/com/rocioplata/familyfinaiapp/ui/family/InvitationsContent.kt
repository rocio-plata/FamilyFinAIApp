package com.rocioplata.familyfinaiapp.ui.family

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rocioplata.familyfinaiapp.R
import com.rocioplata.familyfinaiapp.domain.header.FamilyRole
import com.rocioplata.familyfinaiapp.ui.common.RoleBadge
import com.rocioplata.familyfinaiapp.ui.theme.FamilyFinAIAppTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Acciones de «Invitaciones» que decide el ViewModel. */
data class ReceivedInvitationActions(
    val onAccept: (invitationId: String) -> Unit = {},
    val onDecline: (invitationId: String) -> Unit = {},
    val onConfirmDecline: () -> Unit = {},
    val onDismissDecline: () -> Unit = {},
    val onRetry: () -> Unit = {},
)

/**
 * «Invitaciones» recibidas a pantalla completa (RF-18 a RF-21): por cada una, la familia, quién invita (un texto genérico
 * si no se conoce), el rol ofrecido y cuándo vence, con «Aceptar» y «Rechazar». Sin estado: [onSwitchFamily] la llama el
 * aviso «Cambiar a esta familia» tras aceptar (RF-19).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvitationsContent(
    state: InvitationsUiState,
    onBack: () -> Unit,
    onSwitchFamily: (familyId: String) -> Unit,
    onEventConsumed: () -> Unit,
    modifier: Modifier = Modifier,
    actions: ReceivedInvitationActions = ReceivedInvitationActions(),
) {
    BackHandler(onBack = onBack)
    val snackbar = remember { SnackbarHostState() }
    val event = state.event
    val eventText = when (event) {
        is InvitationsEvent.Accepted -> stringResource(R.string.invitations_accepted, event.familyName)
        InvitationsEvent.Declined -> stringResource(R.string.invitations_declined)
        is InvitationsEvent.Failed -> stringResource(event.failure.messageRes())
        null -> null
    }
    val switchLabel = stringResource(R.string.invitations_switch)
    LaunchedEffect(event) {
        if (eventText != null) {
            if (event is InvitationsEvent.Accepted) {
                val result = snackbar.showSnackbar(eventText, actionLabel = switchLabel, duration = SnackbarDuration.Long)
                if (result == SnackbarResult.ActionPerformed) onSwitchFamily(event.familyId)
            } else {
                snackbar.showSnackbar(eventText)
            }
            onEventConsumed()
        }
    }

    Scaffold(
        modifier = modifier.testTag("invitations_screen"),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.invitations_title), modifier = Modifier.semantics { heading() }) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("family_route_back")) {
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
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            if (state.isRefreshing) {
                val description = stringResource(R.string.invitations_loading)
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = description }.testTag("invitations_refreshing"),
                )
            }
            when (val section = state.section) {
                ReceivedSection.Loading -> {
                    val description = stringResource(R.string.invitations_loading)
                    CenterBox {
                        CircularProgressIndicator(
                            modifier = Modifier.semantics { contentDescription = description }.testTag("invitations_loading"),
                        )
                    }
                }
                is ReceivedSection.Error -> CenterBox {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = stringResource(section.failure.messageRes()),
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.testTag("invitations_error"),
                        )
                        OutlinedButton(
                            onClick = actions.onRetry,
                            modifier = Modifier.heightIn(min = 48.dp).testTag("invitations_retry"),
                        ) { Text(stringResource(R.string.invitations_retry)) }
                    }
                }
                ReceivedSection.Empty -> CenterBox {
                    Column(
                        modifier = Modifier.testTag("invitations_empty"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.MailOutline,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = stringResource(R.string.invitations_empty_title),
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = stringResource(R.string.invitations_empty_message),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                is ReceivedSection.Content -> LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag("invitations_list"),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(section.rows, key = { it.id }) { row ->
                        ReceivedInvitationCard(row, enabled = !state.isBusy, busy = state.busyId == row.id, actions = actions)
                    }
                }
            }
        }
    }

    state.pendingDecline?.let { row ->
        AlertDialog(
            onDismissRequest = actions.onDismissDecline,
            title = { Text(stringResource(R.string.invitations_decline_title, row.familyName)) },
            text = { Text(stringResource(R.string.invitations_decline_message)) },
            confirmButton = {
                TextButton(
                    onClick = actions.onConfirmDecline,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("invitations_decline_confirm"),
                ) { Text(stringResource(R.string.invitations_decline_confirm)) }
            },
            dismissButton = {
                TextButton(
                    onClick = actions.onDismissDecline,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("invitations_decline_cancel"),
                ) { Text(stringResource(R.string.family_manage_dialog_cancel)) }
            },
            modifier = Modifier.testTag("invitations_decline_dialog"),
        )
    }
}

@Composable
private fun CenterBox(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReceivedInvitationCard(
    row: ReceivedInvitationRow,
    enabled: Boolean,
    busy: Boolean,
    actions: ReceivedInvitationActions,
) {
    val roleLabel = stringResource(if (row.role == FamilyRole.OWNER) R.string.role_owner else R.string.role_member)
    val date = remember(row.expiresAt) { expiryFormatter().format(row.expiresAt) }
    val inviter = row.invitedByName?.takeIf { it.isNotBlank() }
    val acceptDescription = stringResource(R.string.invitations_accept_description, row.familyName)
    val declineDescription = stringResource(R.string.invitations_decline_description, row.familyName)
    val busyDescription = stringResource(R.string.invitations_busy)
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("invitation_card_${row.id}"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(
                modifier = Modifier.semantics(mergeDescendants = true) {},
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = row.familyName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("invitation_family_${row.id}"),
                )
                Text(
                    text = if (inviter != null) stringResource(R.string.invitations_invited_by, inviter) else stringResource(R.string.invitations_invited_by_unknown),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("invitation_by_${row.id}"),
                )
                FlowRow(
                    itemVerticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    RoleBadge(row.role, roleLabel, modifier = Modifier.testTag("invitation_role_${row.id}"))
                    Text(
                        text = stringResource(R.string.invitations_expires, date),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("invitation_expiry_${row.id}"),
                    )
                }
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                itemVerticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { actions.onAccept(row.id) },
                    enabled = enabled,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = acceptDescription }
                        .testTag("invitation_accept_${row.id}"),
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp).semantics { contentDescription = busyDescription },
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(stringResource(R.string.invitations_accept))
                    }
                }
                TextButton(
                    onClick = { actions.onDecline(row.id) },
                    enabled = enabled,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = declineDescription }
                        .testTag("invitation_decline_${row.id}"),
                ) { Text(stringResource(R.string.invitations_decline)) }
            }
        }
    }
}

// La fecha con el formato y la zona del dispositivo; `FormatStyle.MEDIUM` da «29 nov 2026» en español.
private fun expiryFormatter(): DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault()).withZone(ZoneId.systemDefault())

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun InvitationsContentPreview() {
    FamilyFinAIAppTheme {
        InvitationsContent(
            state = InvitationsUiState(
                section = ReceivedSection.Content(
                    listOf(
                        ReceivedInvitationRow("i-1", "f-1", "Familia Gómez", "Carolina", FamilyRole.OWNER, Instant.parse("2026-11-29T12:00:00Z")),
                        ReceivedInvitationRow("i-2", "f-2", "Departamento", null, FamilyRole.MEMBER, Instant.parse("2026-11-30T12:00:00Z")),
                    ),
                ),
            ),
            onBack = {},
            onSwitchFamily = {},
            onEventConsumed = {},
        )
    }
}
