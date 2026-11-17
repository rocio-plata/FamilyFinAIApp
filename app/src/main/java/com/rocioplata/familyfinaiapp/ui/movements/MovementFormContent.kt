package com.rocioplata.familyfinaiapp.ui.movements

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rocioplata.familyfinaiapp.R
import com.rocioplata.familyfinaiapp.domain.categories.Category
import com.rocioplata.familyfinaiapp.domain.categories.CategoryStatus
import com.rocioplata.familyfinaiapp.domain.categories.CategoryType
import com.rocioplata.familyfinaiapp.domain.categories.Tag
import com.rocioplata.familyfinaiapp.domain.categories.TagStatus
import com.rocioplata.familyfinaiapp.domain.money.Money
import com.rocioplata.familyfinaiapp.domain.movements.AmountError
import com.rocioplata.familyfinaiapp.domain.movements.DateError
import com.rocioplata.familyfinaiapp.domain.movements.MovementType
import com.rocioplata.familyfinaiapp.domain.movements.NoteError
import com.rocioplata.familyfinaiapp.domain.movements.TitleError
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethod
import com.rocioplata.familyfinaiapp.domain.paymentmethods.PaymentMethodStatus
import com.rocioplata.familyfinaiapp.ui.categories.CategoryIcons
import com.rocioplata.familyfinaiapp.ui.theme.FamilyFinAIAppTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Currency
import java.util.Locale

private const val CATEGORY_COLUMNS = 4

/**
 * Formulario "Nuevo movimiento" a pantalla completa (RF-05 a RF-07, RF-16): "X", familia y moneda, tipo, monto,
 * concepto, cuadrícula de categorías con sus etiquetas, medio de pago, fecha con accesos rápidos y selector sin
 * días futuros, observaciones y "Guardar movimiento". Sin estado: recibe [state] y callbacks; la "X" y atrás
 * pasan por [onClose], que decide si pide confirmar el descarte (CA-19).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovementFormContent(
    state: MovementFormUiState,
    familyName: String,
    onClose: () -> Unit,
    onTypeChange: (MovementType) -> Unit,
    onAmountChange: (String) -> Unit,
    onTitleChange: (String) -> Unit,
    onCategorySelect: (String) -> Unit,
    onTagSelect: (String?) -> Unit,
    onPaymentMethodSelect: (String) -> Unit,
    onToday: () -> Unit,
    onYesterday: () -> Unit,
    onDateSelect: (LocalDate) -> Unit,
    onNoteChange: (String) -> Unit,
    onSave: () -> Unit,
    onRetryOptions: () -> Unit,
    onConfirmDiscard: () -> Unit,
    onDismissDiscard: () -> Unit,
    onNoticeConsumed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Siempre activo: mientras se guarda, el ViewModel ignora el cierre y atrás no debe llegar a la pantalla de abajo.
    BackHandler(onBack = onClose)

    val snackbar = remember { SnackbarHostState() }
    val notice = state.notice
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
        modifier = modifier.testTag("movement_form_screen"),
        snackbarHost = { SnackbarHost(snackbar, modifier = Modifier.imePadding()) },
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (state.isEditing) R.string.movement_edit_title else R.string.movement_create_title))
                },
                navigationIcon = {
                    IconButton(onClick = onClose, enabled = !state.isSaving, modifier = Modifier.testTag("movement_form_close")) {
                        Icon(Icons.Filled.Close, stringResource(R.string.movement_form_close_description))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("movement_form_content"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            FamilyLine(familyName, state.currency)
            if (state.isLoadingMovement) {
                LoadingMovement()
            } else {
                if (state.savedPartially) {
                    Hint(stringResource(R.string.movement_form_partial_saved), "movement_form_partial")
                }
                // El tipo de un movimiento no se puede cambiar al editarlo (RF-09).
                TypeSelector(state.type, enabled = !state.isSaving && !state.isEditing, onTypeChange)
                AmountField(state, onAmountChange)
                TitleField(state, onTitleChange)
                CategorySection(state, onCategorySelect, onTagSelect, onRetryOptions)
                PaymentMethodSection(state, onPaymentMethodSelect)
                DateSection(state, onToday, onYesterday, onDateSelect)
                NoteField(state, onNoteChange)
                SaveSection(state, onSave)
            }
        }
    }
    if (state.confirmingDiscard) DiscardDialog(onConfirmDiscard, onDismissDiscard)
}

@Composable
private fun FamilyLine(familyName: String, currency: String?) {
    Row(
        modifier = Modifier.fillMaxWidth().testTag("movement_form_family"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = familyName,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(end = 8.dp),
        )
        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondaryContainer) {
            Text(
                text = currency ?: stringResource(R.string.movement_form_currency_unknown),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp).testTag("movement_form_currency"),
            )
        }
    }
}

@Composable
private fun TypeSelector(type: MovementType, enabled: Boolean, onTypeChange: (MovementType) -> Unit) {
    val options = listOf(MovementType.EXPENSE, MovementType.INCOME)
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == type,
                onClick = { onTypeChange(option) },
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                icon = {
                    SegmentedButtonDefaults.Icon(active = option == type) {
                        Icon(
                            imageVector = if (option == MovementType.EXPENSE) Icons.AutoMirrored.Filled.TrendingDown
                            else Icons.AutoMirrored.Filled.TrendingUp,
                            contentDescription = null,
                            modifier = Modifier.size(SegmentedButtonDefaults.IconSize),
                        )
                    }
                },
                modifier = Modifier.testTag("movement_form_type_${option.name}"),
            ) {
                Text(stringResource(if (option == MovementType.EXPENSE) R.string.movements_type_expense else R.string.movements_type_income))
            }
        }
    }
}

@Composable
private fun AmountField(state: MovementFormUiState, onAmountChange: (String) -> Unit) {
    val label = stringResource(
        if (state.type == MovementType.EXPENSE) R.string.movement_form_amount_expense else R.string.movement_form_amount_income,
    )
    val decimals = state.currency?.let { Money.fractionDigitsOf(it) } ?: 0
    OutlinedTextField(
        value = state.amountText,
        onValueChange = onAmountChange,
        enabled = !state.isSaving && !state.currencyMissing,
        label = { Text(label) },
        prefix = state.currency?.let { code -> { Text(currencySymbol(code)) } },
        textStyle = MaterialTheme.typography.headlineSmall,
        singleLine = true,
        isError = state.errors.amount != null,
        supportingText = state.errors.amount?.let { error -> { Text(stringResource(error.messageRes())) } },
        keyboardOptions = KeyboardOptions(keyboardType = if (decimals > 0) KeyboardType.Decimal else KeyboardType.Number),
        modifier = Modifier.fillMaxWidth().testTag("movement_form_amount"),
    )
}

private fun currencySymbol(code: String): String = Currency.getInstance(code).getSymbol(Locale.getDefault())

private fun AmountError.messageRes() = when (this) {
    AmountError.REQUIRED -> R.string.movement_form_error_amount_required
    AmountError.MUST_BE_POSITIVE -> R.string.movement_form_error_amount_positive
}

@Composable
private fun TitleField(state: MovementFormUiState, onTitleChange: (String) -> Unit) {
    OutlinedTextField(
        value = state.title,
        onValueChange = onTitleChange,
        enabled = !state.isSaving,
        label = { Text(stringResource(R.string.movement_form_title_label)) },
        singleLine = true,
        isError = state.errors.title != null,
        supportingText = state.errors.title?.let { error ->
            { Text(stringResource(if (error == TitleError.REQUIRED) R.string.movement_form_error_title_required else R.string.movement_form_error_title_too_long)) }
        },
        modifier = Modifier.fillMaxWidth().testTag("movement_form_title"),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategorySection(
    state: MovementFormUiState,
    onCategorySelect: (String) -> Unit,
    onTagSelect: (String?) -> Unit,
    onRetryOptions: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("movement_form_categories")) {
        val selectedName = state.categories.firstOrNull { it.id == state.categoryId }?.name
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.movement_form_category_label), style = MaterialTheme.typography.titleSmall)
            if (selectedName != null) {
                Text(selectedName, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
        when {
            state.options == FormOptionsState.Loading -> OptionsLoading()
            state.options == FormOptionsState.Error -> OptionsError(onRetryOptions)
            state.noCategories -> Hint(
                stringResource(
                    if (state.type == MovementType.EXPENSE) R.string.movement_form_no_expense_categories
                    else R.string.movement_form_no_income_categories,
                ),
                "movement_form_no_categories",
            )
            else -> {
                FlowRow(
                    maxItemsInEachRow = CATEGORY_COLUMNS,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().selectableGroup(),
                ) {
                    state.categories.forEach { category ->
                        CategoryTile(
                            category = category,
                            selected = category.id == state.categoryId,
                            enabled = !state.isSaving,
                            onClick = { onCategorySelect(category.id) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    // Completa la última fila para que las casillas tengan siempre el mismo ancho.
                    repeat((CATEGORY_COLUMNS - state.categories.size % CATEGORY_COLUMNS) % CATEGORY_COLUMNS) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        if (state.errors.category) FieldError(stringResource(R.string.movement_form_error_category_required), "movement_form_category_error")
        if (state.tags.isNotEmpty()) {
            Text(stringResource(R.string.movement_form_tags_label), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                state.tags.forEach { tag ->
                    FilterChip(
                        selected = tag.id == state.tagId,
                        onClick = { onTagSelect(tag.id) },
                        enabled = !state.isSaving,
                        label = {
                            val archived = if (tag.status == TagStatus.DEPRECATED) {
                                " (${stringResource(R.string.movement_detail_archived_female)})"
                            } else {
                                ""
                            }
                            Text("#${tag.name}$archived")
                        },
                        modifier = Modifier.testTag("movement_form_tag_${tag.id}"),
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryTile(
    category: Category,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val icon = CategoryIcons.find(category.icon) ?: CategoryIcons.generic
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = modifier
            .heightIn(min = 72.dp)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .testTag("movement_form_category_${category.id}"),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon.icon, contentDescription = null)
            Text(
                text = category.name,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            // Solo aparece al editar un movimiento cuya categoría se archivó después (RF-10).
            if (category.status == CategoryStatus.DEPRECATED) {
                Text(
                    text = stringResource(R.string.movement_detail_archived_female),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("movement_form_category_archived_${category.id}"),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PaymentMethodSection(state: MovementFormUiState, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("movement_form_payment_methods")) {
        Text(stringResource(R.string.movement_form_payment_method_label), style = MaterialTheme.typography.titleSmall)
        when {
            state.options != FormOptionsState.Ready -> Unit // La sección de categorías ya muestra la carga o el error.
            state.noPaymentMethods -> Hint(stringResource(R.string.movement_form_no_payment_methods), "movement_form_no_payment_methods")
            else -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().selectableGroup()) {
                state.paymentMethods.forEach { method ->
                    FilterChip(
                        selected = method.id == state.paymentMethodId,
                        onClick = { onSelect(method.id) },
                        enabled = !state.isSaving,
                        label = {
                            val archived = if (method.status == PaymentMethodStatus.DEPRECATED) {
                                " (${stringResource(R.string.movement_detail_archived_male)})"
                            } else {
                                ""
                            }
                            Text("${method.name}$archived")
                        },
                        leadingIcon = { Icon(Icons.Filled.CreditCard, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.testTag("movement_form_payment_${method.id}"),
                    )
                }
            }
        }
        if (state.errors.paymentMethod) {
            FieldError(stringResource(R.string.movement_form_error_payment_method_required), "movement_form_payment_error")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DateSection(
    state: MovementFormUiState,
    onToday: () -> Unit,
    onYesterday: () -> Unit,
    onDateSelect: (LocalDate) -> Unit,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val yesterday = state.today.minusDays(1)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("movement_form_date_section")) {
        Text(stringResource(R.string.movement_form_date_label), style = MaterialTheme.typography.titleSmall)
        Text(formatDate(state.date), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("movement_form_date"))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.date == state.today,
                onClick = onToday,
                enabled = !state.isSaving,
                label = { Text(stringResource(R.string.movement_form_date_today)) },
                modifier = Modifier.testTag("movement_form_date_today"),
            )
            FilterChip(
                selected = state.date == yesterday,
                onClick = onYesterday,
                enabled = !state.isSaving,
                label = { Text(stringResource(R.string.movement_form_date_yesterday)) },
                modifier = Modifier.testTag("movement_form_date_yesterday"),
            )
            FilterChip(
                selected = state.date != state.today && state.date != yesterday,
                onClick = { picking = true },
                enabled = !state.isSaving,
                label = { Text(stringResource(R.string.movement_form_date_other)) },
                modifier = Modifier.testTag("movement_form_date_other"),
            )
        }
        if (state.errors.date == DateError.FUTURE) {
            FieldError(stringResource(R.string.movement_form_error_date_future), "movement_form_date_error")
        }
    }
    if (picking) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.date.toUtcMillis(),
            selectableDates = NotAfter(state.today),
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { onDateSelect(it.toUtcDate()) }
                        picking = false
                    },
                    modifier = Modifier.testTag("movement_form_date_confirm"),
                ) { Text(stringResource(R.string.movement_form_date_accept)) }
            },
            dismissButton = {
                TextButton(onClick = { picking = false }) { Text(stringResource(R.string.movement_form_date_cancel)) }
            },
        ) {
            DatePicker(state = pickerState, modifier = Modifier.testTag("movement_form_date_picker"))
        }
    }
}

/** El selector de fecha de Material trabaja con la medianoche UTC del día elegido. */
private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toUtcDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

@OptIn(ExperimentalMaterial3Api::class)
internal class NotAfter(private val today: LocalDate) : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean = !utcTimeMillis.toUtcDate().isAfter(today)
    override fun isSelectableYear(year: Int): Boolean = year <= today.year
}

@Composable
private fun NoteField(state: MovementFormUiState, onNoteChange: (String) -> Unit) {
    OutlinedTextField(
        value = state.note,
        onValueChange = onNoteChange,
        enabled = !state.isSaving,
        label = { Text(stringResource(R.string.movement_form_note_label)) },
        minLines = 3,
        isError = state.errors.note != null,
        supportingText = state.errors.note?.let { error ->
            { if (error == NoteError.TOO_LONG) Text(stringResource(R.string.movement_form_error_note_too_long)) }
        },
        modifier = Modifier.fillMaxWidth().testTag("movement_form_note"),
    )
}

@Composable
private fun SaveSection(state: MovementFormUiState, onSave: () -> Unit) {
    if (state.currencyMissing) Hint(stringResource(R.string.movement_form_currency_missing), "movement_form_currency_missing")
    Button(
        onClick = onSave,
        enabled = !state.isSaving && !state.currencyMissing,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("movement_form_save"),
    ) {
        if (state.isSaving) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp).testTag("movement_form_saving"), strokeWidth = 2.dp)
        } else {
            Icon(Icons.Filled.Check, contentDescription = null)
        }
        Text(
            text = stringResource(if (state.isEditing) R.string.movement_form_save_changes else R.string.movement_form_save),
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun DiscardDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("movement_form_discard_dialog"),
        title = { Text(stringResource(R.string.movement_form_discard_title)) },
        text = { Text(stringResource(R.string.movement_form_discard_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("movement_form_discard_confirm")) {
                Text(stringResource(R.string.movement_form_discard_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("movement_form_discard_keep")) {
                Text(stringResource(R.string.movement_form_discard_keep_editing))
            }
        },
    )
}

@Composable
private fun LoadingMovement() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp).testTag("movement_form_loading"),
        contentAlignment = Alignment.Center,
    ) { CircularProgressIndicator() }
}

@Composable
private fun OptionsLoading() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("movement_form_options_loading"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(stringResource(R.string.movement_form_options_loading), modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun OptionsError(onRetry: () -> Unit) {
    Column(modifier = Modifier.testTag("movement_form_options_error")) {
        Text(
            text = stringResource(R.string.movement_form_options_error),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
        TextButton(onClick = onRetry, modifier = Modifier.testTag("movement_form_options_retry")) {
            Text(stringResource(R.string.movements_retry))
        }
    }
}

/** Explica qué falta y dónde crearlo (CA-23). */
@Composable
private fun Hint(text: String, tag: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.testTag(tag),
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp))
    }
}

@Composable
private fun FieldError(text: String, tag: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.testTag(tag),
    )
}

private val previewCategories = listOf(
    Category("c1", CategoryType.EXPENSE, "Comestibles", "shopping_cart", CategoryStatus.ACTIVE, listOf(Tag("t1", "supermercado", TagStatus.ACTIVE, 0))),
    Category("c2", CategoryType.EXPENSE, "Servicios", "bolt", CategoryStatus.ACTIVE, emptyList()),
    Category("c3", CategoryType.EXPENSE, "Restaurantes", "restaurant", CategoryStatus.ACTIVE, emptyList()),
)

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun MovementFormContentPreview() {
    FamilyFinAIAppTheme {
        MovementFormContent(
            state = MovementFormUiState(
                type = MovementType.EXPENSE, amountText = "45900", currency = "CLP", title = "Supermercado Jumbo",
                categories = previewCategories, categoryId = "c1", tags = previewCategories[0].tags, tagId = "t1",
                paymentMethods = listOf(PaymentMethod("m1", "Tarjeta", PaymentMethodStatus.ACTIVE)), paymentMethodId = "m1",
                date = LocalDate.of(2026, 11, 16), today = LocalDate.of(2026, 11, 16), note = "",
                errors = MovementFormErrors(), options = FormOptionsState.Ready, noCategories = false,
                noPaymentMethods = false, isSaving = false, confirmingDiscard = false, notice = null,
            ),
            familyName = "Familia Plata",
            onClose = {}, onTypeChange = {}, onAmountChange = {}, onTitleChange = {}, onCategorySelect = {},
            onTagSelect = {}, onPaymentMethodSelect = {}, onToday = {}, onYesterday = {}, onDateSelect = {},
            onNoteChange = {}, onSave = {}, onRetryOptions = {}, onConfirmDiscard = {}, onDismissDiscard = {},
            onNoticeConsumed = {},
        )
    }
}
