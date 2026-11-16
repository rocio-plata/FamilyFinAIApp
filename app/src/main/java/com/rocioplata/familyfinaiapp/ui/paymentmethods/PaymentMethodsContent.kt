package com.rocioplata.familyfinaiapp.ui.paymentmethods

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rocioplata.familyfinaiapp.R
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethod
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethodStatus
import com.rocioplata.familyfinaiapp.ui.main.StaleDataBanner
import com.rocioplata.familyfinaiapp.ui.theme.FamilyFinAIAppTheme

/**
 * Listado de medios de pago de la familia activa (RF-01 a RF-03, RF-11): barra con flecha y título, filas
 * de los activos con su nombre, la sección plegable de archivados con su cantidad y los estados de carga,
 * error y vacío, y el formulario de alta y renombrado (RF-06, RF-07). Sin estado: recibe [state] y callbacks. No depende del rol: todos los miembros ven lo
 * mismo (RF-05).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentMethodsContent(
    state: PaymentMethodsUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onToggleArchived: () -> Unit,
    onCreate: () -> Unit,
    onNameChange: (String) -> Unit,
    onSave: () -> Unit,
    onCloseForm: () -> Unit,
    onConfirmDiscard: () -> Unit,
    onDismissDiscard: () -> Unit,
    onRename: (PaymentMethod) -> Unit,
    onArchive: (PaymentMethod) -> Unit,
    onDelete: (PaymentMethod) -> Unit,
    onConfirmAction: () -> Unit,
    onDismissAction: () -> Unit,
    onEventConsumed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbar = remember { SnackbarHostState() }
    var message by remember { mutableStateOf<String?>(null) }
    val event = (state as? PaymentMethodsUiState.Content)?.event
    val failedText = event?.let { stringResource(it.messageRes()) }
    LaunchedEffect(event) {
        if (event is PaymentMethodsEvent.Failed) {
            onEventConsumed()
            message = failedText
        }
    }
    // El aviso se muestra aparte de la confirmación del evento: consumirlo no debe cancelarlo.
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            message = null
        }
    }

    Scaffold(
        modifier = modifier.testTag("payment_methods_screen"),
        snackbarHost = { SnackbarHost(snackbar, modifier = Modifier.imePadding()) },
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(R.string.payment_methods_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("payment_methods_back")) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.payment_methods_back_description),
                            )
                        }
                    },
                )
                if ((state as? PaymentMethodsUiState.Content)?.isStale == true) StaleDataBanner()
                if ((state as? PaymentMethodsUiState.Content)?.isBusy == true) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().testTag("payment_methods_busy"))
                }
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (state) {
                PaymentMethodsUiState.Loading -> LoadingState()
                PaymentMethodsUiState.Error -> ErrorState(onRetry)
                is PaymentMethodsUiState.Content ->
                    ContentState(state, onToggleArchived, onCreate, MethodActions(onRename, onArchive, onDelete))
            }
        }
    }
    (state as? PaymentMethodsUiState.Content)?.form?.let { form ->
        PaymentMethodFormDialog(
            form = form,
            onNameChange = onNameChange,
            onSave = onSave,
            onCloseRequest = onCloseForm,
            onConfirmDiscard = onConfirmDiscard,
            onDismissDiscard = onDismissDiscard,
        )
    }
    (state as? PaymentMethodsUiState.Content)?.let { content ->
        content.pendingAction?.let { action ->
            PendingActionDialog(action, enabled = !content.isBusy, onConfirm = onConfirmAction, onDismiss = onDismissAction)
        }
    }
}

/** Las acciones del menú ⋮ de una fila. */
private class MethodActions(
    val onRename: (PaymentMethod) -> Unit,
    val onArchive: (PaymentMethod) -> Unit,
    val onDelete: (PaymentMethod) -> Unit,
)

/**
 * Confirmar archivar (advierte de que no se puede deshacer, RF-08), confirmar eliminar y la oferta de
 * archivar cuando eliminar falla por movimientos asociados (RF-09).
 */
@Composable
private fun PendingActionDialog(
    action: PaymentMethodPendingAction,
    enabled: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val (titleRes, messageRes, confirmRes) = when (action) {
        is PaymentMethodPendingAction.ConfirmArchive -> Triple(
            R.string.payment_methods_dialog_archive_title,
            R.string.payment_methods_dialog_archive_message,
            R.string.payment_methods_action_archive,
        )
        is PaymentMethodPendingAction.ConfirmDelete -> Triple(
            R.string.payment_methods_dialog_delete_title,
            R.string.payment_methods_dialog_delete_message,
            R.string.payment_methods_action_delete,
        )
        is PaymentMethodPendingAction.OfferArchive -> Triple(
            R.string.payment_methods_dialog_offer_archive_title,
            R.string.payment_methods_dialog_offer_archive_message,
            R.string.payment_methods_action_archive,
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = { Text(stringResource(messageRes)) },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = enabled, modifier = Modifier.testTag("payment_method_action_confirm")) {
                Text(stringResource(confirmRes))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("payment_method_action_cancel")) {
                Text(stringResource(R.string.payment_methods_form_cancel))
            }
        },
    )
}

/**
 * Menú ⋮ de una fila (RF-04): en un activo, Renombrar, Archivar y Eliminar; en un archivado, solo
 * Eliminar. La descripción incluye el nombre para que el lector de pantalla distinga las filas.
 */
@Composable
private fun MethodMenu(method: PaymentMethod, enabled: Boolean, actions: MethodActions) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { open = true },
            enabled = enabled,
            modifier = Modifier.size(48.dp).testTag("payment_method_menu_${method.id}"),
        ) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.payment_methods_menu_description, method.name),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (method.status == PaymentMethodStatus.ACTIVE) {
                MethodMenuItem(R.string.payment_methods_action_rename, "rename") {
                    open = false
                    actions.onRename(method)
                }
                MethodMenuItem(R.string.payment_methods_action_archive, "archive") {
                    open = false
                    actions.onArchive(method)
                }
            }
            MethodMenuItem(R.string.payment_methods_action_delete, "delete") {
                open = false
                actions.onDelete(method)
            }
        }
    }
}

@Composable
private fun MethodMenuItem(@StringRes labelRes: Int, tag: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(labelRes)) },
        onClick = onClick,
        modifier = Modifier.heightIn(min = 48.dp).testTag("payment_method_menu_item_$tag"),
    )
}

@Composable
private fun LoadingState() {
    Column(
        modifier = Modifier.fillMaxSize().testTag("payment_methods_loading"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Text(
            text = stringResource(R.string.payment_methods_loading),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

@Composable
private fun ErrorState(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp).testTag("payment_methods_error"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.payment_methods_error_load),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
        )
        TextButton(onClick = onRetry, modifier = Modifier.padding(top = 8.dp).testTag("payment_methods_retry")) {
            Text(stringResource(R.string.payment_methods_retry))
        }
    }
}

@Composable
private fun ContentState(
    state: PaymentMethodsUiState.Content,
    onToggleArchived: () -> Unit,
    onCreate: () -> Unit,
    actions: MethodActions,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("payment_methods_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "create") {
            Button(
                onClick = onCreate,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("payment_methods_create"),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(stringResource(R.string.payment_methods_create_action), modifier = Modifier.padding(start = 8.dp))
            }
        }
        if (state.isEmpty) item(key = "empty") { EmptyState() }
        items(state.active, key = { it.id }) { method -> PaymentMethodRow(method, !state.isBusy, actions) }
        if (state.archived.isNotEmpty()) {
            item(key = "archived_header") {
                ArchivedHeader(count = state.archived.size, expanded = state.archivedExpanded, onClick = onToggleArchived)
            }
            if (state.archivedExpanded) {
                items(state.archived, key = { "archived_${it.id}" }) { method -> ArchivedRow(method, !state.isBusy, actions) }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp).testTag("payment_methods_empty"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.payment_methods_empty), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun PaymentMethodRow(method: PaymentMethod, menuEnabled: Boolean, actions: MethodActions) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("payment_method_row_${method.id}"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Row(
            modifier = Modifier.heightIn(min = 72.dp).padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            MethodIcon()
            Text(
                text = method.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            MethodMenu(method, menuEnabled, actions)
        }
    }
}

/** Cabecera plegable de «Medios de pago archivados» con su cantidad (RF-03). */
@Composable
private fun ArchivedHeader(count: Int, expanded: Boolean, onClick: () -> Unit) {
    val description = pluralStringResource(R.plurals.payment_methods_archived_section_description, count, count)
    val actionLabel = stringResource(
        if (expanded) R.string.payment_methods_archived_collapse_description
        else R.string.payment_methods_archived_expand_description,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClickLabel = actionLabel, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(vertical = 8.dp)
            .testTag("payment_methods_archived_header"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.payment_methods_archived_section_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        Text(text = count.toString(), style = MaterialTheme.typography.labelLarge)
        Icon(
            imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
        )
    }
}

/** Fila de un medio archivado: atenuada y con el texto «Archivado» (no solo el color). */
@Composable
private fun ArchivedRow(method: PaymentMethod, menuEnabled: Boolean, actions: MethodActions) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("payment_method_row_${method.id}"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.heightIn(min = 72.dp).padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            MethodIcon(modifier = Modifier.alpha(DIMMED_ALPHA))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = method.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = DIMMED_ALPHA),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.payment_methods_status_archived),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            MethodMenu(method, menuEnabled, actions)
        }
    }
}

// Decorativo: el nombre ya dice de qué se trata, así que no lleva descripción propia.
@Composable
private fun MethodIcon(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.size(48.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(imageVector = Icons.Filled.CreditCard, contentDescription = null)
        }
    }
}

private const val DIMMED_ALPHA = 0.7f

private fun previewContent(
    active: List<PaymentMethod>,
    archived: List<PaymentMethod> = emptyList(),
    expanded: Boolean = false,
    isStale: Boolean = false,
) = PaymentMethodsUiState.Content(
    active = active, archived = archived, archivedExpanded = expanded, isStale = isStale, isRefreshing = false,
)

@Composable
private fun PreviewHost(state: PaymentMethodsUiState) {
    FamilyFinAIAppTheme {
        PaymentMethodsContent(
            state = state, onBack = {}, onRetry = {}, onToggleArchived = {}, onCreate = {}, onNameChange = {},
            onSave = {}, onCloseForm = {}, onConfirmDiscard = {}, onDismissDiscard = {}, onRename = {},
            onArchive = {}, onDelete = {}, onConfirmAction = {}, onDismissAction = {}, onEventConsumed = {},
        )
    }
}

private val previewActive = listOf(
    PaymentMethod("m1", "Efectivo", PaymentMethodStatus.ACTIVE),
    PaymentMethod("m2", "Tarjeta de Crédito con un nombre muy largo que no cabe en la fila", PaymentMethodStatus.ACTIVE),
    PaymentMethod("m3", "Transferencia", PaymentMethodStatus.ACTIVE),
)
private val previewArchived = listOf(PaymentMethod("m4", "Cheque", PaymentMethodStatus.DEPRECATED))

@Preview(showBackground = true, showSystemUi = true, name = "Listado")
@Composable
private fun PaymentMethodsContentPreview() =
    PreviewHost(previewContent(previewActive, previewArchived, expanded = true, isStale = true))

@Preview(showBackground = true, name = "Formulario con error")
@Composable
private fun PaymentMethodFormPreview() = PreviewHost(
    previewContent(previewActive).copy(
        form = PaymentMethodFormState(
            target = PaymentMethodFormTarget.Create, initialName = "", text = "Efectivo",
            error = PaymentMethodFormError.Duplicate,
        ),
    ),
)

@Preview(showBackground = true, name = "Vacío")
@Composable
private fun PaymentMethodsEmptyPreview() = PreviewHost(previewContent(emptyList()))

@Preview(showBackground = true, name = "Cargando")
@Composable
private fun PaymentMethodsLoadingPreview() = PreviewHost(PaymentMethodsUiState.Loading)

@Preview(showBackground = true, name = "Error")
@Composable
private fun PaymentMethodsErrorPreview() = PreviewHost(PaymentMethodsUiState.Error)
