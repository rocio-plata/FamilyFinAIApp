package com.rocioplata.familyfinaiapp.ui.categories

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rocioplata.familyfinaiapp.R
import com.rocioplata.familyfinaiapp.domain.categories.CategoryType
import com.rocioplata.familyfinaiapp.domain.categories.CategoryValidation
import com.rocioplata.familyfinaiapp.ui.theme.FamilyFinAIAppTheme

/**
 * Alta y edición de una categoría (RF-09 a RF-11, RF-14; color en RF-14 y RF-15 de la 008): tipo, nombre con
 * contador, selector de color, selector de icono y
 * «Crear»/«Guardar» en la barra (siempre visible con el teclado abierto). Sin estado. Al salir con
 * nombre o icono sin guardar pide confirmar el descarte (CA-24). Los eventos pendientes de [state] se
 * muestran aquí y se confirman con [onEventConsumed].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryEditContent(
    state: CategoryEditUiState.Form,
    onLeave: () -> Unit,
    onNameChange: (String) -> Unit,
    onIconChange: (String?) -> Unit,
    onTypeChange: (CategoryType) -> Unit,
    onSubmit: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onEventConsumed: () -> Unit,
    onCreated: (String) -> Unit,
    modifier: Modifier = Modifier,
    tagActions: TagActions = TagActions(),
    onColorChange: (String?) -> Unit = {},
) {
    var showDiscard by rememberSaveable { mutableStateOf(false) }
    // Archivar y eliminar se confirman antes de pedirlos (RF-12, RF-13); se conservan al recrear la pantalla.
    var confirmArchive by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val requestLeave = {
        if (state.hasUnsavedChanges) showDiscard = true else onLeave()
    }
    BackHandler(onBack = requestLeave)

    val snackbar = remember { SnackbarHostState() }
    var message by remember { mutableStateOf<String?>(null) }
    val event = state.event
    val failedText = (event as? CategoryEditEvent.Failed)?.failure?.let { stringResource(it.messageRes()) }
    val tagNotFoundText = stringResource(R.string.categories_error_tag_not_found)
    val reorderFailedText = stringResource(R.string.categories_error_reorder_failed)
    val tagReactivatedText = stringResource(R.string.categories_tag_reactivated)
    val tagReactivatedNotMovedText = stringResource(R.string.categories_tag_reactivated_not_moved)
    LaunchedEffect(event) {
        when (event) {
            is CategoryEditEvent.Created -> {
                onEventConsumed()
                onCreated(event.categoryId)
            }
            is CategoryEditEvent.Failed -> {
                onEventConsumed()
                message = failedText
            }
            CategoryEditEvent.TagNotFound -> {
                onEventConsumed()
                message = tagNotFoundText
            }
            CategoryEditEvent.ReorderFailed -> {
                onEventConsumed()
                message = reorderFailedText
            }
            CategoryEditEvent.TagReactivated -> {
                onEventConsumed()
                message = tagReactivatedText
            }
            CategoryEditEvent.TagReactivatedNotMoved -> {
                onEventConsumed()
                message = tagReactivatedNotMovedText
            }
            // Estos eventos se resuelven con su diálogo.
            is CategoryEditEvent.Leave,
            CategoryEditEvent.OfferArchive,
            is CategoryEditEvent.OfferArchiveTag,
            CategoryEditEvent.TagCannotDelete,
            null,
            -> Unit
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
        modifier = modifier.testTag("category_edit_screen"),
        // Con el teclado abierto el aviso no debe quedar tapado por él.
        snackbarHost = { SnackbarHost(snackbar, modifier = Modifier.imePadding()) },
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (state.isCreate) R.string.categories_create_title else R.string.categories_edit_title))
                },
                navigationIcon = {
                    IconButton(onClick = requestLeave, modifier = Modifier.testTag("category_edit_back")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.categories_back_description),
                        )
                    }
                },
                actions = {
                    if (!state.readOnly) SubmitButton(state, onSubmit)
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
                .padding(16.dp)
                .testTag("category_edit_form"),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (state.readOnly) ReadOnlyNotice(archived = state.archived)
            TypeSelector(state, onTypeChange)
            NameField(state, onNameChange, onSubmit)
            ColorSelector(state, onColorChange)
            IconSelector(state, onIconChange)
            // Las etiquetas solo existen cuando la categoría ya está creada (RF-09).
            if (!state.isCreate) TagList(state, tagActions, onEventConsumed)
            if (!state.isCreate && !state.readOnly) {
                ArchiveAndDeleteActions(
                    enabled = !state.isBusy,
                    onArchive = { confirmArchive = true },
                    onDelete = { confirmDelete = true },
                )
            }
        }
    }

    if (confirmArchive) {
        AlertDialog(
            onDismissRequest = { confirmArchive = false },
            title = { Text(stringResource(R.string.categories_dialog_archive_title)) },
            text = { Text(stringResource(R.string.categories_dialog_archive_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmArchive = false
                        onArchive()
                    },
                    enabled = !state.isBusy,
                    modifier = Modifier.testTag("archive_confirm"),
                ) { Text(stringResource(R.string.categories_archive_action)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmArchive = false }, modifier = Modifier.testTag("archive_cancel")) {
                    Text(stringResource(R.string.categories_dialog_cancel))
                }
            },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.categories_dialog_delete_title)) },
            text = { Text(stringResource(R.string.categories_dialog_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                    enabled = !state.isBusy,
                    modifier = Modifier.testTag("delete_confirm"),
                ) { Text(stringResource(R.string.categories_delete_action)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }, modifier = Modifier.testTag("delete_cancel")) {
                    Text(stringResource(R.string.categories_dialog_cancel))
                }
            },
        )
    }
    // Eliminar falló por movimientos asociados: se ofrece archivar la categoría en su lugar (RF-13).
    if (event is CategoryEditEvent.OfferArchive) {
        AlertDialog(
            onDismissRequest = onEventConsumed,
            title = { Text(stringResource(R.string.categories_dialog_has_items_title)) },
            text = { Text(stringResource(R.string.categories_dialog_has_items_offer_archive_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onEventConsumed()
                        onArchive()
                    },
                    modifier = Modifier.testTag("offer_archive_confirm"),
                ) { Text(stringResource(R.string.categories_archive_action)) }
            },
            dismissButton = {
                TextButton(onClick = onEventConsumed, modifier = Modifier.testTag("offer_archive_cancel")) {
                    Text(stringResource(R.string.categories_dialog_cancel))
                }
            },
        )
    }

    if (showDiscard) {
        AlertDialog(
            onDismissRequest = { showDiscard = false },
            title = { Text(stringResource(R.string.categories_dialog_discard_title)) },
            text = { Text(stringResource(R.string.categories_dialog_discard_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscard = false
                        onLeave()
                    },
                    modifier = Modifier.testTag("discard_confirm"),
                ) { Text(stringResource(R.string.categories_dialog_discard_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscard = false }, modifier = Modifier.testTag("discard_keep")) {
                    Text(stringResource(R.string.categories_dialog_discard_keep_editing))
                }
            },
        )
    }
    if (event is CategoryEditEvent.Leave) {
        GoneDialog(
            messageRes = event.failure.messageRes(),
            onDismiss = {
                onEventConsumed()
                onLeave()
            },
        )
    }
}

/** La categoría ya no existe: aviso y salida al listado, sin preguntar por lo escrito. */
@Composable
fun GoneDialog(messageRes: Int, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(stringResource(messageRes)) },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("gone_confirm")) {
                Text(stringResource(R.string.categories_dialog_understood))
            }
        },
    )
}

@Composable
private fun SubmitButton(state: CategoryEditUiState.Form, onSubmit: () -> Unit) {
    Button(
        onClick = onSubmit,
        enabled = state.canSubmit,
        modifier = Modifier.padding(end = 8.dp).testTag("category_edit_submit"),
    ) {
        if (state.isBusy) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp).testTag("category_edit_progress"), strokeWidth = 2.dp)
        } else {
            Text(stringResource(if (state.isCreate) R.string.categories_create_button else R.string.categories_save_button))
        }
    }
}

/** «Archivar» (irreversible, BG-01) y «Eliminar»: solo el Propietario y en una categoría activa. */
@Composable
private fun ArchiveAndDeleteActions(enabled: Boolean, onArchive: () -> Unit, onDelete: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = onArchive,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("category_edit_archive"),
        ) {
            Icon(Icons.Filled.Archive, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.categories_archive_action), modifier = Modifier.padding(start = 8.dp))
        }
        Button(
            onClick = onDelete,
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("category_edit_delete"),
        ) {
            Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.categories_delete_action), modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun ReadOnlyNotice(archived: Boolean) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.fillMaxWidth().testTag("category_edit_read_only"),
    ) {
        Text(
            text = stringResource(
                if (archived) R.string.categories_archived_read_only_notice else R.string.categories_read_only_notice,
            ),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
private fun TypeSelector(state: CategoryEditUiState.Form, onTypeChange: (CategoryType) -> Unit) {
    val types = listOf(
        CategoryType.EXPENSE to R.string.categories_type_expense,
        CategoryType.INCOME to R.string.categories_type_income,
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.categories_type_label), style = MaterialTheme.typography.titleSmall)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            types.forEachIndexed { index, (type, labelRes) ->
                SegmentedButton(
                    selected = state.type == type,
                    onClick = { onTypeChange(type) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = types.size),
                    enabled = state.typeEditable,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("category_type_${type.name.lowercase()}"),
                ) { Text(stringResource(labelRes)) }
            }
        }
        if (!state.isCreate) {
            Text(
                text = stringResource(R.string.categories_type_locked_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NameField(state: CategoryEditUiState.Form, onNameChange: (String) -> Unit, onSubmit: () -> Unit) {
    val max = CategoryValidation.MAX_CATEGORY_NAME_LENGTH
    OutlinedTextField(
        value = state.name,
        onValueChange = onNameChange,
        modifier = Modifier.fillMaxWidth().testTag("category_edit_name"),
        label = { Text(stringResource(R.string.categories_field_name)) },
        singleLine = true,
        readOnly = state.readOnly,
        isError = state.nameError != null,
        supportingText = {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = state.nameError?.message(max).orEmpty(),
                    modifier = Modifier.weight(1f).testTag("category_edit_name_error"),
                )
                Text(stringResource(R.string.categories_name_counter, state.name.length, max))
            }
        },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (state.canSubmit) onSubmit() }),
    )
}

/**
 * Selector de color (RF-15 de la 008): «Automático» (sin color, usa el de respaldo) y los 16 colores a la vista en
 * una cuadrícula (cuatro columnas en un teléfono), con una vista previa del icono en el color efectivo. El elegido se
 * distingue por una marca y un anillo, no solo por el tono, y se anuncia por su nombre como seleccionado.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorSelector(state: CategoryEditUiState.Form, onColorChange: (String?) -> Unit) {
    val effective = state.effectiveColor.displayColor()
    val effectiveName = stringResource(state.effectiveColor.nameRes)
    val icon = CategoryIcons.displayOf(state.icon)
    Column(
        modifier = Modifier.selectableGroup().testTag("category_color_selector"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(effective.softBackground())
                    .testTag("category_color_preview"),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon.icon,
                    contentDescription = stringResource(R.string.categories_color_preview_description, effectiveName),
                    tint = effective,
                )
            }
            Text(
                text = stringResource(R.string.categories_color_section_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.categories_icon_available_count, CategoryColors.options.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val noneDescription = stringResource(R.string.categories_color_none_description)
        FilterChip(
            selected = state.color == null,
            onClick = { onColorChange(null) },
            enabled = !state.readOnly,
            label = { Text(stringResource(R.string.categories_color_none)) },
            leadingIcon = {
                if (state.color == null) Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            },
            modifier = Modifier
                .heightIn(min = 48.dp)
                .semantics { contentDescription = noneDescription }
                .testTag("category_color_none"),
        )
        // Los 16 colores a la vista, sin desplazar: una cuadrícula que ocupa todo el ancho (cuatro columnas en un
        // teléfono) y que con texto grande pasa a menos columnas en lugar de recortar.
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val minCell = SWATCH_MIN_WIDTH * LocalDensity.current.fontScale.coerceIn(1f, 2f)
            val columns = ((maxWidth + SWATCH_SPACING) / (minCell + SWATCH_SPACING)).toInt().coerceAtLeast(1)
            // Un dp menos: al redondear a píxeles, columnas de ancho exacto podrían no caber y la última pasaría a otra fila.
            val cellWidth = (maxWidth - SWATCH_SPACING * (columns - 1)) / columns - 1.dp
            FlowRow(
                maxItemsInEachRow = columns,
                horizontalArrangement = Arrangement.spacedBy(SWATCH_SPACING),
                verticalArrangement = Arrangement.spacedBy(SWATCH_SPACING),
                modifier = Modifier.fillMaxWidth().testTag("category_color_grid"),
            ) {
                CategoryColors.options.forEach { option ->
                    ColorSwatch(
                        option = option,
                        selected = state.color == option.key,
                        enabled = !state.readOnly,
                        onClick = { onColorChange(option.key) },
                        modifier = Modifier.width(cellWidth),
                    )
                }
            }
        }
    }
}

/** Un color del catálogo: círculo con su nombre debajo; al menos 48 dp de alto y de ancho para tocarlo. */
@Composable
private fun ColorSwatch(
    option: CategoryColorOption,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = stringResource(option.nameRes)
    val description = if (selected) stringResource(R.string.categories_icon_selected_description, name) else name
    val color = option.displayColor()
    Column(
        modifier = modifier
            .heightIn(min = 64.dp)
            // En solo lectura los colores se atenúan: no responden y no deben parecer activos.
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .clip(RoundedCornerShape(12.dp))
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = description }
            .testTag("category_color_${option.key}")
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .then(
                    if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier,
                )
                .padding(if (selected) 4.dp else 0.dp)
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = color.readableOn(),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IconSelector(state: CategoryEditUiState.Form, onIconChange: (String?) -> Unit) {
    Column(
        modifier = Modifier.selectableGroup().testTag("category_icon_selector"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.categories_icon_section_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.categories_icon_available_count, CategoryIcons.options.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FilterChip(
            selected = state.icon == null,
            onClick = { onIconChange(null) },
            enabled = !state.readOnly,
            label = { Text(stringResource(R.string.categories_icon_none)) },
            leadingIcon = {
                if (state.icon == null) Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            },
            modifier = Modifier.heightIn(min = 48.dp).testTag("category_icon_none"),
        )
        FlowRow(
            maxItemsInEachRow = ICONS_PER_ROW,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            CategoryIcons.options.forEach { option ->
                IconCell(
                    option = option,
                    tint = state.effectiveColor.displayColor(),
                    selected = state.icon == option.key,
                    enabled = !state.readOnly,
                    onClick = { onIconChange(option.key) },
                    modifier = Modifier.weight(1f),
                )
            }
            // Si la última fila queda incompleta, los huecos mantienen el ancho de las celdas (sin ellos
            // las últimas se estirarían a todo el ancho).
            repeat((ICONS_PER_ROW - CategoryIcons.options.size % ICONS_PER_ROW) % ICONS_PER_ROW) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

/** Celda del catálogo: el elegido se distingue por color y por una marca, y se anuncia como seleccionado. */
@Composable
private fun IconCell(
    option: CategoryIconOption,
    tint: Color,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = stringResource(option.descriptionRes)
    val description = if (selected) stringResource(R.string.categories_icon_selected_description, name) else name
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .aspectRatio(1f)
            // En solo lectura las celdas se atenúan: no responden y no deben parecer activas.
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) tint.softBackground() else colors.surfaceContainerHigh)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = description }
            .testTag("category_icon_${option.key}"),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = option.icon,
            contentDescription = null,
            tint = tint,
        )
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(14.dp),
            )
        }
    }
}

private const val ICONS_PER_ROW = 5
// Con 88 dp caben cuatro por fila en un teléfono (4 × 4 = los 16 colores); con más ancho, más columnas.
private val SWATCH_MIN_WIDTH = 88.dp
private val SWATCH_SPACING = 4.dp
private const val DISABLED_ALPHA = 0.6f

private fun previewForm(
    isCreate: Boolean = false,
    name: String = "Alimentación",
    icon: String? = "shopping_cart",
    readOnly: Boolean = false,
    nameError: NameFieldError? = null,
    busy: Boolean = false,
) = CategoryEditUiState.Form(
    isCreate = isCreate, type = CategoryType.EXPENSE, typeEditable = isCreate && !readOnly, name = name,
    nameError = nameError, icon = icon, readOnly = readOnly, archived = false, tags = emptyList(),
    hasUnsavedChanges = true, isBusy = busy, event = null,
)

@Composable
private fun PreviewHost(state: CategoryEditUiState.Form) {
    FamilyFinAIAppTheme {
        CategoryEditContent(
            state = state, onLeave = {}, onNameChange = {}, onIconChange = {}, onTypeChange = {},
            onSubmit = {}, onArchive = {}, onDelete = {}, onEventConsumed = {}, onCreated = {},
        )
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Edición")
@Composable
private fun CategoryEditPreview() = PreviewHost(previewForm())

@Preview(showBackground = true, showSystemUi = true, name = "Alta con error")
@Composable
private fun CategoryCreatePreview() =
    PreviewHost(previewForm(isCreate = true, name = "", icon = null, nameError = NameFieldError.REQUIRED))

@Preview(showBackground = true, showSystemUi = true, name = "Solo lectura")
@Composable
private fun CategoryReadOnlyPreview() = PreviewHost(previewForm(readOnly = true))
