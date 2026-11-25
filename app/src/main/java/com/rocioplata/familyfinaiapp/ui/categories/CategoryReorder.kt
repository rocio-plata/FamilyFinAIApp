package com.rocioplata.familyfinaiapp.ui.categories

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.rocioplata.familyfinaiapp.R
import com.rocioplata.familyfinaiapp.domain.categories.CategoryType
import com.rocioplata.familyfinaiapp.ui.common.dragShift
import com.rocioplata.familyfinaiapp.ui.common.dragTargetIndex

// Orden de las categorías activas del listado (RF-10 de la 010).

/** Lo que el listado le pide al ViewModel al ordenar; vacío por defecto para las vistas previas. */
data class CategoryReorderActions(
    val onStart: () -> Unit = {},
    val onFinish: () -> Unit = {},
    val onMove: (id: String, toIndex: Int) -> Unit = { _, _ -> },
    val onMoveUp: (String) -> Unit = {},
    val onMoveDown: (String) -> Unit = {},
)

/**
 * Título de la sección de activas con su cantidad y, para el Propietario, «Reordenar» (desactivada si no se puede: con
 * búsqueda, con menos de dos activas o con otra operación en curso) o «Listo» en el modo de orden. El texto puede saltar
 * de línea con fuente grande, para que la acción no quede cortada.
 */
@Composable
internal fun CategorySectionHeader(
    type: CategoryType,
    count: Int,
    canManage: Boolean,
    reorderMode: Boolean,
    canReorder: Boolean,
    actions: CategoryReorderActions,
) {
    Row(
        modifier = Modifier.fillMaxWidth().testTag("categories_section_header"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(
                    if (type == CategoryType.EXPENSE) R.string.categories_section_expense else R.string.categories_section_income,
                ),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = pluralStringResource(R.plurals.categories_active_count, count, count),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when {
            reorderMode -> TextButton(
                onClick = actions.onFinish,
                modifier = Modifier.heightIn(min = 48.dp).testTag("categories_reorder_done"),
            ) { Text(stringResource(R.string.categories_reorder_done)) }
            canManage -> TextButton(
                onClick = actions.onStart,
                enabled = canReorder,
                modifier = Modifier.heightIn(min = 48.dp).testTag("categories_reorder"),
            ) {
                Icon(Icons.Filled.SwapVert, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.categories_reorder), modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
}

/**
 * Activas en el modo de orden: asa de arrastre y botones subir/bajar en cada fila, también como acciones de accesibilidad.
 * Arrastre propio, sin librería (la lista es corta): el desplazamiento vive aquí y solo al soltar se pide el movimiento,
 * que el ViewModel muestra enseguida. Tocar una fila no la abre.
 */
@Composable
internal fun CategoryReorderList(items: List<CategoryItem>, enabled: Boolean, actions: CategoryReorderActions) {
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val rowHeights = remember { mutableStateMapOf<String, Int>() }
    val spacingPx = with(LocalDensity.current) { ROW_SPACING.toPx() }
    val count = items.size
    val dragFrom = items.indexOfFirst { it.category.id == draggingId }
    val step = (draggingId?.let { rowHeights[it] } ?: 0) + spacingPx
    val dragTarget = if (dragFrom >= 0) dragTargetIndex(dragFrom, dragOffset, step, count) else -1

    Column(
        modifier = Modifier.fillMaxWidth().testTag("categories_reorder_list"),
        verticalArrangement = Arrangement.spacedBy(ROW_SPACING),
    ) {
        items.forEachIndexed { index, item ->
            key(item.category.id) {
                val isDragging = draggingId == item.category.id
                ReorderRow(
                    item = item,
                    enabled = enabled,
                    canMoveUp = enabled && index > 0,
                    canMoveDown = enabled && index < count - 1,
                    dragOffset = if (isDragging) dragOffset else 0f,
                    shift = if (dragFrom >= 0 && !isDragging) dragShift(index, dragFrom, dragTarget, step) else 0f,
                    isDragging = isDragging,
                    onHeight = { rowHeights[item.category.id] = it },
                    onMoveUp = { actions.onMoveUp(item.category.id) },
                    onMoveDown = { actions.onMoveDown(item.category.id) },
                    onDragStart = {
                        draggingId = item.category.id
                        dragOffset = 0f
                    },
                    onDragTo = { dragOffset = it },
                    onDragEnd = {
                        // Se recalcula al soltar: el último movimiento puede llegar sin recomponer antes.
                        val target = dragTargetIndex(dragFrom, dragOffset, step, count)
                        val moved = item.category.id
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
    }
}

@Composable
private fun ReorderRow(
    item: CategoryItem,
    enabled: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    dragOffset: Float,
    shift: Float,
    isDragging: Boolean,
    onHeight: (Int) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDragStart: () -> Unit,
    onDragTo: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    val name = item.category.name
    val moveUpLabel = stringResource(R.string.categories_move_up_description, name)
    val moveDownLabel = stringResource(R.string.categories_move_down_description, name)
    val icon = CategoryIcons.displayOf(item.category.icon)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { onHeight(it.height) }
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer { translationY = if (isDragging) dragOffset else shift }
            .semantics {
                customActions = buildList {
                    if (canMoveUp) add(CustomAccessibilityAction(moveUpLabel) { onMoveUp(); true })
                    if (canMoveDown) add(CustomAccessibilityAction(moveDownLabel) { onMoveDown(); true })
                }
            }
            .testTag("category_reorder_row_${item.category.id}"),
        shape = RoundedCornerShape(16.dp),
        color = if (isDragging) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = if (isDragging) 6.dp else 0.dp,
    ) {
        Row(
            modifier = Modifier.heightIn(min = 72.dp).padding(end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (enabled) {
                DragHandle(item.category.id, name, onDragStart, onDragTo, onDragEnd, onDragCancel)
            } else {
                Box(modifier = Modifier.size(48.dp))
            }
            val tone = item.color.displayColor()
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(12.dp),
                color = tone.softBackground(),
                contentColor = tone,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(imageVector = icon.icon, contentDescription = stringResource(icon.descriptionRes))
                }
            }
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = onMoveUp,
                enabled = canMoveUp,
                modifier = Modifier.testTag("category_move_up_${item.category.id}"),
            ) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = moveUpLabel)
            }
            IconButton(
                onClick = onMoveDown,
                enabled = canMoveDown,
                modifier = Modifier.testTag("category_move_down_${item.category.id}"),
            ) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = moveDownLabel)
            }
        }
    }
}

/**
 * Asa de arrastre de 48 dp, como la de los tags: el desplazamiento se mide en coordenadas de ventana porque la fila
 * entera se traslada con el dedo y, en coordenadas locales del asa, cada gesto se cancelaría en parte.
 */
@Composable
private fun DragHandle(
    id: String,
    name: String,
    onDragStart: () -> Unit,
    onDragTo: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    val start by rememberUpdatedState(onDragStart)
    val dragTo by rememberUpdatedState(onDragTo)
    val end by rememberUpdatedState(onDragEnd)
    val cancel by rememberUpdatedState(onDragCancel)
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    Box(
        modifier = Modifier
            .size(48.dp)
            .onGloballyPositioned { coordinates = it }
            .pointerInput(id) {
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
            .testTag("category_handle_$id"),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.DragHandle,
            contentDescription = stringResource(R.string.categories_drag_handle_description, name),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val ROW_SPACING = 8.dp
