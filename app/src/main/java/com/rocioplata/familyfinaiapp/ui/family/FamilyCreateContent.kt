package com.rocioplata.familyfinaiapp.ui.family

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rocioplata.familyfinaiapp.R
import com.rocioplata.familyfinaiapp.domain.family.FamilyNameRules
import com.rocioplata.familyfinaiapp.ui.theme.FamilyFinAIAppTheme

/**
 * «Crear nueva familia» a pantalla completa (RF-04): solo el nombre, con contador «n / 60» y el error junto al campo, y
 * los textos que dicen quién lo verá y que la familia nace en CLP. Sin estado: lo que ocurre lo decide el ViewModel.
 * Atrás, la flecha y el gesto de atrás llaman a [onClose], que con el nombre escrito pide confirmación (CA-31).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyCreateContent(
    state: FamilyCreateUiState,
    onNameChange: (String) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
    onConfirmDiscard: () -> Unit,
    onDismissDiscard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onClose)
    val focusRequester = remember { FocusRequester() }
    // Se espera a un fotograma: en la primera composición el campo aún no está listo para recibir el foco.
    LaunchedEffect(Unit) {
        withFrameNanos { }
        focusRequester.requestFocus()
    }
    val savingDescription = stringResource(R.string.family_create_saving)

    Scaffold(
        modifier = modifier.testTag("family_create_screen"),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.family_create_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose, modifier = Modifier.testTag("family_create_back")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.family_back_description),
                        )
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button(
                    onClick = onSave,
                    enabled = !state.isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .heightIn(min = 48.dp)
                        .testTag("family_create_save"),
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp).semantics { contentDescription = savingDescription },
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(stringResource(R.string.family_create_save))
                    }
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Groups,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column {
                    Text(
                        text = stringResource(R.string.family_create_intro_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = stringResource(R.string.family_create_intro_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            OutlinedTextField(
                value = state.text,
                onValueChange = onNameChange,
                label = { Text(stringResource(R.string.family_create_name_label)) },
                placeholder = { Text(stringResource(R.string.family_create_name_placeholder)) },
                singleLine = true,
                enabled = !state.isSaving,
                isError = state.error != null,
                supportingText = {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.error?.let {
                            Text(
                                text = stringResource(it.messageRes()),
                                modifier = Modifier.weight(1f).testTag("family_create_error"),
                            )
                        } ?: Text("", modifier = Modifier.weight(1f))
                        Text(
                            text = stringResource(R.string.family_create_counter, state.text.length, FamilyNameRules.MAX_NAME_LENGTH),
                            modifier = Modifier.testTag("family_create_counter"),
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSave() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).testTag("family_create_name"),
            )
            InfoRow(R.string.family_create_info_name, "family_create_info_name")
            InfoRow(R.string.family_create_info_currency, "family_create_info_currency")
        }
    }

    if (state.confirmingDiscard) {
        AlertDialog(
            onDismissRequest = onDismissDiscard,
            title = { Text(stringResource(R.string.family_create_discard_title)) },
            text = { Text(stringResource(R.string.family_create_discard_message)) },
            confirmButton = {
                TextButton(onClick = onConfirmDiscard, modifier = Modifier.testTag("family_create_discard_confirm")) {
                    Text(stringResource(R.string.family_create_discard_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDiscard, modifier = Modifier.testTag("family_create_discard_keep")) {
                    Text(stringResource(R.string.family_create_discard_keep))
                }
            },
        )
    }
}

@Composable
private fun InfoRow(textRes: Int, tag: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(textRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(tag),
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun FamilyCreateContentPreview() {
    FamilyFinAIAppTheme {
        FamilyCreateContent(
            state = FamilyCreateUiState(text = "Casa de Campo", error = FamilyCreateError.Duplicate),
            onNameChange = {}, onSave = {}, onClose = {}, onConfirmDiscard = {}, onDismissDiscard = {},
        )
    }
}
