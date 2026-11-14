package com.rocioplata.familyfinaiapp.ui.categories

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.rocioplata.familyfinaiapp.R
import com.rocioplata.familyfinaiapp.domain.categories.CategoryType
import com.rocioplata.familyfinaiapp.domain.categories.CategoryValidation
import com.rocioplata.familyfinaiapp.domain.categories.Tag
import com.rocioplata.familyfinaiapp.domain.categories.TagStatus
import com.rocioplata.familyfinaiapp.ui.theme.FamilyFinAIAppTheme
import kotlin.math.roundToInt

/** Lo que la lista de tags le pide al ViewModel (RF-15 a RF-19). */
data class TagActions(
    val onNewNameChange: (String) -> Unit = {},
    val onAdd: () -> Unit = {},
    val onStartRename: (String) -> Unit = {},
    val onRenameTextChange: (String) -> Unit = {},
    val onConfirmRename: () -> Unit = {},
    val onCancelRename: () -> Unit = {},
    val onRemove: (String) -> Unit = {},
    val onArchive: (String) -> Unit = {},
    /** Al soltar un arrastre: mueve el tag a esa posición entre los activos (RF-17). */
    val onMove: (String, Int) -> Unit = { _, _ -> },
    /** Alternativa sin arrastre, para lector de pantalla (RF-17). */
    val onMoveUp: (String) -> Unit = {},
    val onMoveDown: (String) -> Unit = {},
)

/**
 * Sección «Etiquetas» de una categoría ya creada (RF-15 a RF-19): los tags activos y, después, los archivados
 * atenuados y marcados como «Archivado»; campo para añadir; tocar un tag activo lo renombra; quitar pide
 * confirmación y, si tiene movimientos, ofrece archivarlo. En solo lectura no hay acciones. Sin estado:
 * [onEventConsumed] confirma los avisos de [state].
 */
@Composable
fun TagList(
    state: CategoryEditUiState.Form,
    actions: TagActions,
    onEventConsumed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Quitar un tag se confirma antes de pedirlo (RF-18, RF-19); se conserva al recrear la pantalla.
    var pendingRemove by rememberSaveable { mutableStateOf<String?>(null) }
    val activeTags = state.tags.filter { it.status == TagStatus.ACTIVE }
    val activeCount = activeTags.size
    // Con menos de dos activos no hay nada que reordenar; durante una operación no se acepta otro movimiento.
    val canReorder = !state.readOnly && !state.isBusy && activeCount > 1

    // Arrastre propio, sin librería (la lista es corta): el desplazamiento vive aquí y solo al soltar se
    // pide el movimiento, que el ViewModel muestra enseguida (cambio optimista).
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val rowHeights = remember { mutableStateMapOf<String, Int>() }
    val spacingPx = with(LocalDensity.current) { ROW_SPACING.toPx() }
    val dragFrom = activeTags.indexOfFirst { it.id == draggingId }
    val step = (draggingId?.let { rowHeights[it] } ?: 0) + spacingPx
    val dragTarget = if (dragFrom >= 0) dragTargetIndex(dragFrom, dragOffset, step, activeCount) else -1

    Column(modifier = modifier.fillMaxWidth().testTag("tag_list"), verticalArrangement = Arrangement.spacedBy(ROW_SPACING)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.categories_tags_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = pluralStringResource(R.plurals.categories_tags_active_count, activeCount, activeCount),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.tags.isEmpty()) {
            Text(
                text = stringResource(R.string.categories_tags_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("tag_empty"),
            )
        }
        state.tags.forEach { tag ->
            key(tag.id) {
                val index = activeTags.indexOfFirst { it.id == tag.id }
                val isDragging = draggingId == tag.id
                TagRow(
                    tag = tag,
                    canEdit = !state.readOnly,
                    enabled = !state.isBusy,
                    showHandle = canReorder && index >= 0,
                    dragOffset = if (isDragging) dragOffset else 0f,
                    shift = if (dragFrom >= 0 && !isDragging && index >= 0) dragShift(index, dragFrom, dragTarget, step) else 0f,
                    isDragging = isDragging,
                    canMoveUp = canReorder && index > 0,
                    canMoveDown = canReorder && index in 0 until activeCount - 1,
                    onHeight = { rowHeights[tag.id] = it },
                    onRename = { actions.onStartRename(tag.id) },
                    onRemove = { pendingRemove = tag.id },
                    onMoveUp = { actions.onMoveUp(tag.id) },
                    onMoveDown = { actions.onMoveDown(tag.id) },
                    onDragStart = {
                        draggingId = tag.id
                        dragOffset = 0f
                    },
                    onDragTo = { dragOffset = it },
                    onDragEnd = {
                        val target = dragTarget
                        val moved = tag.id
                        draggingId = null
                        dragOffset = 0f
                        if (dragFrom >= 0 && target != dragFrom) actions.onMove(moved, target)
                    },
                    onDragCancel = {
                        draggingId = null
                        dragOffset = 0f
                    },
                )
            }
        }
        if (!state.readOnly) AddTagField(state, actions)
    }

    pendingRemove?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            title = { Text(stringResource(R.string.categories_dialog_tag_remove_title)) },
            text = { Text(stringResource(R.string.categories_dialog_tag_remove_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRemove = null
                        actions.onRemove(id)
                    },
                    enabled = !state.isBusy,
                    modifier = Modifier.testTag("tag_remove_confirm"),
                ) { Text(stringResource(R.string.categories_dialog_tag_remove_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemove = null }, modifier = Modifier.testTag("tag_remove_cancel")) {
                    Text(stringResource(R.string.categories_dialog_cancel))
                }
            },
        )
    }

    state.renaming?.let { draft ->
        AlertDialog(
            onDismissRequest = actions.onCancelRename,
            title = { Text(stringResource(R.string.categories_tag_rename_title)) },
            text = {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = actions.onRenameTextChange,
                    modifier = Modifier.fillMaxWidth().testTag("tag_rename_field"),
                    label = { Text(stringResource(R.string.categories_tag_field_name)) },
                    singleLine = true,
                    isError = draft.error != null,
                    supportingText = {
                        Text(
                            text = draft.error?.message(CategoryValidation.MAX_TAG_NAME_LENGTH, forTag = true).orEmpty(),
                            modifier = Modifier.testTag("tag_rename_error"),
                        )
                    },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (!state.isBusy) actions.onConfirmRename() }),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = actions.onConfirmRename,
                    enabled = !state.isBusy,
                    modifier = Modifier.testTag("tag_rename_confirm"),
                ) { Text(stringResource(R.string.categories_save_button)) }
            },
            dismissButton = {
                TextButton(onClick = actions.onCancelRename, modifier = Modifier.testTag("tag_rename_cancel")) {
                    Text(stringResource(R.string.categories_dialog_cancel))
                }
            },
        )
    }

    // Quitar un tag activo falló por movimientos asociados: se ofrece archivarlo en su lugar (RF-18).
    (state.event as? CategoryEditEvent.OfferArchiveTag)?.let { offer ->
        AlertDialog(
            onDismissRequest = onEventConsumed,
            title = { Text(stringResource(R.string.categories_dialog_tag_has_items_title)) },
            text = { Text(stringResource(R.string.categories_dialog_tag_has_items_offer_archive_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onEventConsumed()
                        actions.onArchive(offer.tagId)
                    },
                    modifier = Modifier.testTag("tag_offer_archive_confirm"),
                ) { Text(stringResource(R.string.categories_dialog_tag_archive_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = onEventConsumed, modifier = Modifier.testTag("tag_offer_archive_cancel")) {
                    Text(stringResource(R.string.categories_dialog_cancel))
                }
            },
        )
    }
    // Un tag archivado con movimientos no se puede eliminar y no admite la oferta de archivar (RF-19).
    if (state.event is CategoryEditEvent.TagCannotDelete) {
        AlertDialog(
            onDismissRequest = onEventConsumed,
            title = { Text(stringResource(R.string.categories_dialog_tag_has_items_title)) },
            text = { Text(stringResource(R.string.categories_dialog_tag_has_items_archived_message)) },
            confirmButton = {
                TextButton(onClick = onEventConsumed, modifier = Modifier.testTag("tag_cannot_delete_confirm")) {
                    Text(stringResource(R.string.categories_dialog_understood))
                }
            },
        )
    }
}

/**
 * Una fila: un tag activo se renombra al tocarlo y, con más de uno, se reordena con el asa de arrastre o con las
 * acciones de accesibilidad «Subir»/«Bajar»; uno archivado se muestra atenuado y marcado (RF-17, RF-19).
 */
@Composable
private fun TagRow(
    tag: Tag,
    canEdit: Boolean,
    enabled: Boolean,
    showHandle: Boolean,
    dragOffset: Float,
    shift: Float,
    isDragging: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onHeight: (Int) -> Unit,
    onRename: () -> Unit,
    onRemove: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDragStart: () -> Unit,
    onDragTo: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    val archived = tag.status != TagStatus.ACTIVE
    val moveUpLabel = stringResource(R.string.categories_tag_move_up)
    val moveDownLabel = stringResource(R.string.categories_tag_move_down)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { onHeight(it.height) }
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer { translationY = if (isDragging) dragOffset else shift }
            .semantics {
                // Alternativa sin arrastre para lector de pantalla (RF-17); solo las que tienen sentido.
                customActions = buildList {
                    if (canMoveUp) add(CustomAccessibilityAction(moveUpLabel) { onMoveUp(); true })
                    if (canMoveDown) add(CustomAccessibilityAction(moveDownLabel) { onMoveDown(); true })
                }
            }
            .testTag("tag_row_${tag.id}"),
        shape = RoundedCornerShape(12.dp),
        color = if (isDragging) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = if (isDragging) 6.dp else 0.dp,
    ) {
        Row(
            modifier = Modifier
                .then(
                    if (canEdit && !archived) {
                        Modifier.clickable(
                            enabled = enabled,
                            onClickLabel = stringResource(R.string.categories_tag_rename_title),
                            role = Role.Button,
                            onClick = onRename,
                        )
                    } else {
                        Modifier
                    },
                )
                .heightIn(min = 48.dp)
                .padding(start = if (showHandle) 0.dp else 16.dp, end = if (canEdit) 4.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (showHandle) DragHandle(tag, onDragStart, onDragTo, onDragEnd, onDragCancel)
            Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(
                    text = tag.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (archived) Modifier.alpha(ARCHIVED_ALPHA) else Modifier,
                )
                if (archived) {
                    Text(
                        text = stringResource(R.string.categories_tag_status_archived),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (canEdit) {
                IconButton(onClick = onRemove, enabled = enabled, modifier = Modifier.testTag("tag_remove_${tag.id}")) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.categories_tag_remove_description, tag.name),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

/**
 * Asa de arrastre de 48 dp: arrastrarla en vertical mueve el tag; el movimiento se pide al soltar. El
 * desplazamiento se mide en coordenadas de ventana: la fila entera se traslada con el dedo y, medido en
 * coordenadas locales del propio asa, cada gesto se cancelaría en parte con ese desplazamiento.
 */
@Composable
private fun DragHandle(
    tag: Tag,
    onDragStart: () -> Unit,
    onDragTo: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    // Los callbacks cambian al recomponer mientras dura el gesto: se lee siempre el último.
    val start by rememberUpdatedState(onDragStart)
    val dragTo by rememberUpdatedState(onDragTo)
    val end by rememberUpdatedState(onDragEnd)
    val cancel by rememberUpdatedState(onDragCancel)
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    Box(
        modifier = Modifier
            .size(48.dp)
            .onGloballyPositioned { coordinates = it }
            .pointerInput(tag.id) {
                var startY = 0f
                fun windowY(local: Offset) = coordinates?.localToWindow(local)?.y ?: 0f
                detectDragGestures(
                    onDragStart = { local ->
                        startY = windowY(local)
                        start()
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        dragTo(windowY(change.position) - startY)
                    },
                    onDragEnd = { end() },
                    onDragCancel = { cancel() },
                )
            }
            .testTag("tag_handle_${tag.id}"),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.DragHandle,
            contentDescription = stringResource(R.string.categories_tag_drag_handle_description, tag.name),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Posición entre los activos a la que corresponde un arrastre de [offsetPx] desde [from], siendo [stepPx] lo
 * que ocupa una fila más su separación. Se ajusta a los límites; sin paso medido no hay movimiento.
 */
internal fun dragTargetIndex(from: Int, offsetPx: Float, stepPx: Float, count: Int): Int {
    if (count <= 0) return 0
    if (stepPx <= 0f) return from.coerceIn(0, count - 1)
    return (from + (offsetPx / stepPx).roundToInt()).coerceIn(0, count - 1)
}

/** Cuánto se aparta (en px) la fila [index] para dejar sitio al tag que se arrastra de [from] a [target]. */
internal fun dragShift(index: Int, from: Int, target: Int, stepPx: Float): Float = when {
    index == from -> 0f
    from < target && index in (from + 1)..target -> -stepPx
    from > target && index in target until from -> stepPx
    else -> 0f
}

@Composable
private fun AddTagField(state: CategoryEditUiState.Form, actions: TagActions) {
    OutlinedTextField(
        value = state.newTagName,
        onValueChange = actions.onNewNameChange,
        modifier = Modifier.fillMaxWidth().testTag("tag_new_field"),
        label = { Text(stringResource(R.string.categories_tag_field_add)) },
        singleLine = true,
        isError = state.tagError != null,
        supportingText = {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = state.tagError?.message(CategoryValidation.MAX_TAG_NAME_LENGTH, forTag = true).orEmpty(),
                    modifier = Modifier.weight(1f).testTag("tag_new_error"),
                )
                Text(
                    stringResource(R.string.categories_name_counter, state.newTagName.length, CategoryValidation.MAX_TAG_NAME_LENGTH),
                )
            }
        },
        trailingIcon = {
            Button(
                onClick = actions.onAdd,
                enabled = !state.isBusy,
                modifier = Modifier.padding(end = 8.dp).testTag("tag_add_button"),
            ) { Text(stringResource(R.string.categories_tag_add_button)) }
        },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (!state.isBusy) actions.onAdd() }),
    )
}

private const val ARCHIVED_ALPHA = 0.6f
private val ROW_SPACING = 12.dp

private fun previewForm(readOnly: Boolean = false, tags: List<Tag>) = CategoryEditUiState.Form(
    isCreate = false, type = CategoryType.EXPENSE, typeEditable = false, name = "Alimentación", nameError = null,
    icon = null, readOnly = readOnly, archived = false, tags = tags, hasUnsavedChanges = false, isBusy = false, event = null,
)

private val previewTags = listOf(
    Tag("t1", "Supermercado", TagStatus.ACTIVE, 0),
    Tag("t2", "Carnicería y verdulería", TagStatus.ACTIVE, 1),
    Tag("t3", "Despensa vieja", TagStatus.DEPRECATED, 2),
)

@Preview(showBackground = true, name = "Etiquetas")
@Composable
private fun TagListPreview() = FamilyFinAIAppTheme {
    TagList(previewForm(tags = previewTags), TagActions(), onEventConsumed = {}, modifier = Modifier.padding(16.dp))
}

@Preview(showBackground = true, name = "Etiquetas (solo lectura)")
@Composable
private fun TagListReadOnlyPreview() = FamilyFinAIAppTheme {
    TagList(previewForm(readOnly = true, tags = previewTags), TagActions(), onEventConsumed = {}, modifier = Modifier.padding(16.dp))
}

@Preview(showBackground = true, name = "Etiquetas (vacío)")
@Composable
private fun TagListEmptyPreview() = FamilyFinAIAppTheme {
    TagList(previewForm(tags = emptyList()), TagActions(), onEventConsumed = {}, modifier = Modifier.padding(16.dp))
}
