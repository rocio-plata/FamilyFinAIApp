package com.rocioplata.familyfinaiapp.ui.movements

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rocioplata.familyfinaiapp.R
import com.rocioplata.familyfinaiapp.domain.header.FamilyRole
import com.rocioplata.familyfinaiapp.domain.money.Money
import com.rocioplata.familyfinaiapp.domain.movements.MovementType
import com.rocioplata.familyfinaiapp.ui.categories.CategoryIcons
import com.rocioplata.familyfinaiapp.ui.theme.Expense
import com.rocioplata.familyfinaiapp.ui.theme.FamilyFinAIAppTheme
import com.rocioplata.familyfinaiapp.ui.theme.Income
import java.time.LocalDate

/**
 * Detalle de un movimiento a pantalla completa (RF-08, RF-11, RF-15): flecha, monto con signo y moneda,
 * concepto, medio de pago, categoría, etiqueta, fecha, quién lo registró (iniciales, nombre y rol; nunca el
 * correo), observaciones y las acciones "Editar movimiento" y "Eliminar movimiento" con confirmación. Sin
 * estado: recibe [state] y callbacks. Lo archivado se indica (CA-10) y un movimiento que ya no existe se
 * informa con "Volver al listado" (CA-22).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovementDetailContent(
    state: MovementDetailUiState,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    onCancelDelete: () -> Unit,
    onNoticeConsumed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Siempre activo: mientras se elimina, el ViewModel ignora el cierre y atrás no debe llegar a la pantalla de abajo.
    BackHandler(onBack = onBack)

    val snackbar = remember { SnackbarHostState() }
    val notice = (state as? MovementDetailUiState.Content)?.notice
    val noticeText = notice?.let { stringResource(it.messageRes()) }
    var message by remember { mutableStateOf<String?>(null) }
    // Consumir el aviso cambia la clave del primer efecto: el aviso se muestra aparte para no cancelarse a sí mismo.
    LaunchedEffect(notice) {
        if (notice != null) {
            onNoticeConsumed()
            message = noticeText
        }
    }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            message = null
        }
    }

    Scaffold(
        modifier = modifier.testTag("movement_detail_screen"),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.movement_detail_title)) },
                navigationIcon = {
                    val deleting = (state as? MovementDetailUiState.Content)?.isDeleting == true
                    IconButton(onClick = onBack, enabled = !deleting, modifier = Modifier.testTag("movement_detail_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.movement_back_description))
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (state) {
                MovementDetailUiState.Loading -> LoadingState()
                MovementDetailUiState.Gone -> GoneState(onBack)
                is MovementDetailUiState.Content -> ContentState(state, onEdit, onDelete)
            }
        }
    }
    if ((state as? MovementDetailUiState.Content)?.confirmingDelete == true) {
        DeleteDialog(isDeleting = state.isDeleting, onConfirm = onConfirmDelete, onCancel = onCancelDelete)
    }
}

@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize().testTag("movement_detail_loading"), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun GoneState(onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp).testTag("movement_detail_gone"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.movement_detail_gone),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onBack, modifier = Modifier.padding(top = 16.dp).testTag("movement_detail_gone_back")) {
            Text(stringResource(R.string.movement_detail_gone_back))
        }
    }
}

@Composable
private fun ContentState(content: MovementDetailUiState.Content, onEdit: () -> Unit, onDelete: () -> Unit) {
    val busy = content.isDeleting
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("movement_detail_content"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        HeroCard(content)
        DataCard(content)
        RecordedByCard(content.recordedBy)
        content.note?.let { NoteCard(it) }
        FilledTonalButton(
            onClick = onEdit,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("movement_detail_edit"),
        ) {
            Icon(Icons.Filled.Edit, contentDescription = null)
            Text(stringResource(R.string.movement_detail_edit), Modifier.padding(start = 8.dp), fontWeight = FontWeight.SemiBold)
        }
        Button(
            onClick = onDelete,
            enabled = !busy,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("movement_detail_delete"),
        ) {
            Icon(Icons.Filled.Delete, contentDescription = null)
            Text(stringResource(R.string.movement_detail_delete), Modifier.padding(start = 8.dp), fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun HeroCard(content: MovementDetailUiState.Content) {
    val amountText = content.amount.formattedFor(content.type)
    val typeText = stringResource(
        if (content.type == MovementType.EXPENSE) R.string.movements_type_expense else R.string.movements_type_income,
    )
    val icon = CategoryIcons.find(content.categoryIcon) ?: CategoryIcons.generic
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().testTag("movement_detail_hero"),
    ) {
        Column(
            modifier = Modifier.padding(20.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) { Icon(icon.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer) }
            // El tipo va también en el texto accesible: gasto e ingreso no dependen solo del color ni del signo.
            Text(
                text = amountText,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = if (content.type == MovementType.EXPENSE) Expense else Income,
                modifier = Modifier
                    .semantics { contentDescription = "$typeText, $amountText" }
                    .testTag("movement_detail_amount"),
            )
            Text(
                text = content.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("movement_detail_title"),
            )
            content.paymentMethod?.let { method ->
                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondaryContainer) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            .semantics(mergeDescendants = true) {}.testTag("movement_detail_payment_method"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.CreditCard, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(
                            text = itemText(method, R.string.movement_detail_archived_male),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DataCard(content: MovementDetailUiState.Content) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DataRow(
                label = stringResource(R.string.movement_form_category_label),
                value = itemText(content.category, R.string.movement_detail_archived_female),
                tag = "movement_detail_category",
            )
            content.tag?.let { tag ->
                DataRow(
                    label = stringResource(R.string.movement_detail_tag_label),
                    value = itemText(tag, R.string.movement_detail_archived_female, prefix = "#"),
                    tag = "movement_detail_tag",
                )
            }
            DataRow(
                label = stringResource(R.string.movement_form_date_label),
                value = formatDate(content.date),
                tag = "movement_detail_date",
            )
            content.paymentMethod?.let { method ->
                DataRow(
                    label = stringResource(R.string.movement_form_payment_method_label),
                    value = itemText(method, R.string.movement_detail_archived_male),
                    tag = "movement_detail_payment_row",
                )
            }
        }
    }
}

/** El nombre del elemento, con la indicación de archivado, o "No disponible" si ya no se encuentra (CA-10). */
@Composable
private fun itemText(item: ResolvedItem, archivedRes: Int, prefix: String = ""): String {
    val name = item.name ?: return stringResource(R.string.movement_detail_item_unavailable)
    val text = "$prefix$name"
    return if (item.isArchived) "$text (${stringResource(archivedRes)})" else text
}

@Composable
private fun DataRow(label: String, value: String, tag: String) {
    Row(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.testTag(tag),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f).padding(start = 16.dp),
        )
    }
}

/** Quién lo registró: iniciales, nombre y rol; el propio usuario lleva "Tú". Nunca el correo (RF-15). */
@Composable
private fun RecordedByCard(label: MemberLabel) {
    val context = LocalContext.current
    val name = if (label is MemberLabel.You) label.displayName else label.text(context::getString)
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth().semantics(mergeDescendants = true) {}.testTag("movement_detail_recorded_by"),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.movement_detail_recorded_by),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (label is MemberLabel.Unavailable) {
                Text(stringResource(R.string.movements_recorded_by_unavailable), style = MaterialTheme.typography.bodyLarge)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        val initials = label.initials
                        if (initials != null) {
                            Text(initials, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Icon(Icons.Outlined.Person, null, tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        if (name != null) {
                            Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (label is MemberLabel.You) Chip(stringResource(R.string.movements_recorded_by_you), "movement_detail_you")
                            label.role?.let { Chip(stringResource(it.labelRes()), "movement_detail_role") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Chip(text: String, tag: String) {
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp).testTag(tag),
        )
    }
}

@Composable
private fun NoteCard(note: String) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth().semantics(mergeDescendants = true) {}.testTag("movement_detail_note"),
        ) {
            Text(
                text = stringResource(R.string.movement_detail_note_label),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(note, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun DeleteDialog(isDeleting: Boolean, onConfirm: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = { if (!isDeleting) onCancel() },
        modifier = Modifier.testTag("movement_detail_delete_dialog"),
        title = { Text(stringResource(R.string.movement_detail_delete_title)) },
        text = { Text(stringResource(R.string.movement_detail_delete_message)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !isDeleting,
                modifier = Modifier.testTag("movement_detail_delete_confirm"),
            ) {
                if (isDeleting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp).testTag("movement_detail_deleting"), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.movement_detail_delete_confirm), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, enabled = !isDeleting, modifier = Modifier.testTag("movement_detail_delete_cancel")) {
                Text(stringResource(R.string.movement_detail_delete_cancel))
            }
        },
    )
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun MovementDetailContentPreview() {
    FamilyFinAIAppTheme {
        MovementDetailContent(
            state = MovementDetailUiState.Content(
                id = "m-1", type = MovementType.EXPENSE, amount = Money(38_450, "CLP"), title = "Supermercado Jumbo",
                note = "Compra semanal de víveres para la casa.", date = LocalDate.of(2026, 11, 12),
                category = ResolvedItem("Comestibles", false), categoryIcon = "shopping_cart",
                tag = ResolvedItem("supermercado", false), paymentMethod = ResolvedItem("Tarjeta", false),
                recordedBy = MemberLabel.You("Valentina Plata", FamilyRole.OWNER),
                confirmingDelete = false, isDeleting = false, notice = null,
            ),
            onBack = {}, onEdit = {}, onDelete = {}, onConfirmDelete = {}, onCancelDelete = {}, onNoticeConsumed = {},
        )
    }
}
