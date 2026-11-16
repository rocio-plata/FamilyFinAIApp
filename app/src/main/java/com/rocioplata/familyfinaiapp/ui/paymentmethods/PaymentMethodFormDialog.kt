package com.rocioplata.familyfinaiapp.ui.paymentmethods

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.rocioplata.familyfinaiapp.R
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethodValidation

/**
 * Formulario de alta y renombrado (RF-06, RF-07, RF-13): un campo de nombre con contador «n / 40», el
 * error junto al campo y Guardar con progreso. Atrás, tocar fuera y Cancelar llaman a [onCloseRequest]
 * (un único punto de cierre); si hay cambios, [PaymentMethodFormState.confirmingDiscard] muestra la
 * confirmación de descarte.
 */
@Composable
fun PaymentMethodFormDialog(
    form: PaymentMethodFormState,
    onNameChange: (String) -> Unit,
    onSave: () -> Unit,
    onCloseRequest: () -> Unit,
    onConfirmDiscard: () -> Unit,
    onDismissDiscard: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    // Se espera a un fotograma: en la primera composición de la ventana del diálogo el campo aún no está listo.
    LaunchedEffect(Unit) {
        withFrameNanos { }
        focusRequester.requestFocus()
    }
    val savingDescription = stringResource(R.string.payment_methods_form_saving)

    AlertDialog(
        onDismissRequest = onCloseRequest,
        title = {
            Text(
                stringResource(
                    if (form.target is PaymentMethodFormTarget.Create) R.string.payment_methods_form_title_create
                    else R.string.payment_methods_form_title_rename,
                ),
            )
        },
        text = {
            OutlinedTextField(
                value = form.text,
                onValueChange = onNameChange,
                label = { Text(stringResource(R.string.payment_methods_form_name_label)) },
                singleLine = true,
                enabled = !form.isSaving,
                isError = form.error != null,
                supportingText = {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        form.error?.let {
                            Text(
                                text = stringResource(it.messageRes()),
                                modifier = Modifier.weight(1f).testTag("payment_method_form_error"),
                            )
                        } ?: Text("", modifier = Modifier.weight(1f))
                        Text(
                            text = stringResource(
                                R.string.payment_methods_form_counter,
                                form.text.length,
                                PaymentMethodValidation.MAX_NAME_LENGTH,
                            ),
                            modifier = Modifier.testTag("payment_method_form_counter"),
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSave() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).testTag("payment_method_form_name"),
            )
        },
        confirmButton = {
            TextButton(
                onClick = onSave,
                enabled = !form.isSaving,
                modifier = Modifier.testTag("payment_method_form_save"),
            ) {
                if (form.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp).semantics { contentDescription = savingDescription },
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(stringResource(R.string.payment_methods_form_save))
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCloseRequest,
                enabled = !form.isSaving,
                modifier = Modifier.testTag("payment_method_form_cancel"),
            ) { Text(stringResource(R.string.payment_methods_form_cancel)) }
        },
    )

    if (form.confirmingDiscard) {
        AlertDialog(
            onDismissRequest = onDismissDiscard,
            title = { Text(stringResource(R.string.payment_methods_discard_title)) },
            text = {
                Text(stringResource(R.string.payment_methods_discard_message), style = MaterialTheme.typography.bodyMedium)
            },
            confirmButton = {
                TextButton(onClick = onConfirmDiscard, modifier = Modifier.testTag("payment_method_discard_confirm")) {
                    Text(stringResource(R.string.payment_methods_discard_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDiscard, modifier = Modifier.testTag("payment_method_discard_keep")) {
                    Text(stringResource(R.string.payment_methods_discard_keep_editing))
                }
            },
        )
    }
}
