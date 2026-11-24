package com.rocioplata.familyfinaiapp.ui.family

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rocioplata.familyfinaiapp.R
import com.rocioplata.familyfinaiapp.domain.header.FamilyRole
import com.rocioplata.familyfinaiapp.ui.theme.FamilyFinAIAppTheme

/**
 * Diálogo «Invitar miembro» (RF-14, RF-17): el correo con el error junto al campo, el rol (Integrante preseleccionado) y el
 * aviso de que no se envía ningún correo. Atrás, Cancelar y tocar fuera llaman a [onClose], que con cambios pide la
 * confirmación de descarte. El contenido se desplaza, así que con texto grande o con el teclado abierto nada queda inaccesible.
 */
@Composable
fun InviteMemberDialog(
    form: InviteFormState,
    onEmailChange: (String) -> Unit,
    onRoleChange: (FamilyRole) -> Unit,
    onSubmit: () -> Unit,
    onClose: () -> Unit,
    onConfirmDiscard: () -> Unit,
    onDismissDiscard: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    // Se espera a un fotograma: en la primera composición de la ventana del diálogo el campo aún no está listo.
    LaunchedEffect(Unit) {
        withFrameNanos { }
        focusRequester.requestFocus()
    }
    val submittingDescription = stringResource(R.string.family_invite_submitting)

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.family_invite_dialog_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).testTag("invite_dialog_content"),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = form.email,
                    onValueChange = onEmailChange,
                    label = { Text(stringResource(R.string.family_invite_email_label)) },
                    placeholder = { Text(stringResource(R.string.family_invite_email_placeholder)) },
                    singleLine = true,
                    enabled = !form.isSaving,
                    isError = form.error != null,
                    supportingText = {
                        form.error?.let {
                            Text(text = stringResource(it.messageRes()), modifier = Modifier.testTag("invite_email_error"))
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).testTag("invite_email"),
                )
                Text(
                    text = stringResource(R.string.family_invite_role_label),
                    style = MaterialTheme.typography.labelLarge,
                )
                Column(modifier = Modifier.selectableGroup()) {
                    RoleOption(FamilyRole.MEMBER, R.string.role_member, form, onRoleChange, "invite_role_member")
                    RoleOption(FamilyRole.OWNER, R.string.role_owner, form, onRoleChange, "invite_role_owner")
                }
                Text(
                    text = stringResource(R.string.family_invite_info),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("invite_info"),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onSubmit,
                enabled = !form.isSaving,
                modifier = Modifier.heightIn(min = 48.dp).testTag("invite_submit"),
            ) {
                if (form.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp).semantics { contentDescription = submittingDescription },
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(stringResource(R.string.family_invite_submit))
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onClose,
                enabled = !form.isSaving,
                modifier = Modifier.heightIn(min = 48.dp).testTag("invite_cancel"),
            ) { Text(stringResource(R.string.family_invite_cancel)) }
        },
    )

    if (form.confirmingDiscard) {
        AlertDialog(
            onDismissRequest = onDismissDiscard,
            title = { Text(stringResource(R.string.family_invite_discard_title)) },
            text = { Text(stringResource(R.string.family_invite_discard_message)) },
            confirmButton = {
                TextButton(onClick = onConfirmDiscard, modifier = Modifier.testTag("invite_discard_confirm")) {
                    Text(stringResource(R.string.family_invite_discard_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDiscard, modifier = Modifier.testTag("invite_discard_keep")) {
                    Text(stringResource(R.string.family_invite_discard_keep))
                }
            },
        )
    }
}

@Composable
private fun RoleOption(role: FamilyRole, labelRes: Int, form: InviteFormState, onRoleChange: (FamilyRole) -> Unit, tag: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(
                selected = form.role == role,
                enabled = !form.isSaving,
                role = Role.RadioButton,
                onClick = { onRoleChange(role) },
            )
            .padding(horizontal = 4.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(selected = form.role == role, onClick = null, enabled = !form.isSaving)
        Text(stringResource(labelRes), style = MaterialTheme.typography.bodyLarge)
    }
}

@Preview(showBackground = true)
@Composable
private fun InviteMemberDialogPreview() {
    FamilyFinAIAppTheme {
        InviteMemberDialog(
            form = InviteFormState(email = "hermano@example.com", role = FamilyRole.MEMBER, error = InviteFormError.AlreadyInvited),
            onEmailChange = {}, onRoleChange = {}, onSubmit = {}, onClose = {}, onConfirmDiscard = {}, onDismissDiscard = {},
        )
    }
}
