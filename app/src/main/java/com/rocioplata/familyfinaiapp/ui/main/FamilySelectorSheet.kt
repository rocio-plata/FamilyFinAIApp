package com.rocioplata.familyfinaiapp.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.rocioplata.familyfinaiapp.R
import com.rocioplata.familyfinaiapp.domain.family.FamilyAccessFailure
import com.rocioplata.familyfinaiapp.domain.header.Family
import com.rocioplata.familyfinaiapp.domain.header.FamilyRole
import com.rocioplata.familyfinaiapp.ui.common.dragShift
import com.rocioplata.familyfinaiapp.ui.common.dragTargetIndex
import com.rocioplata.familyfinaiapp.ui.theme.FamilyFinAIAppTheme

private val ROW_SPACING = 4.dp
private val MIN_TOUCH_TARGET = 48.dp

/**
 * Selector de familias (RF-01 a RF-03 de la 009): se abre también con una sola familia. Elegir una la activa y cierra la
 * hoja; el icono de gestión de la activa abre «Mi familia»; «Crear nueva familia» abre el alta. Con dos o más familias se
 * pueden reordenar arrastrando el asa o con las acciones de accesibilidad subir/bajar. Atrás o tocar fuera llaman a
 * [onDismiss], que cierra sin cambiar la familia activa.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilySelectorSheet(
    families: List<Family>,
    activeFamilyId: String,
    error: FamilyAccessFailure?,
    isReordering: Boolean,
    onSelect: (String) -> Unit,
    onMove: (familyId: String, targetIndex: Int) -> Unit,
    onManage: () -> Unit,
    onCreate: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = modifier,
    ) {
        FamilySelectorContent(
            families = families,
            activeFamilyId = activeFamilyId,
            error = error,
            isReordering = isReordering,
            onSelect = onSelect,
            onMove = onMove,
            onManage = onManage,
            onCreate = onCreate,
        )
    }
}

@Composable
internal fun FamilySelectorContent(
    families: List<Family>,
    activeFamilyId: String,
    error: FamilyAccessFailure?,
    isReordering: Boolean,
    onSelect: (String) -> Unit,
    onMove: (familyId: String, targetIndex: Int) -> Unit,
    onManage: () -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canReorder = families.size > 1 && !isReordering

    // Arrastre propio, sin librería (la lista es corta), como en las etiquetas de una categoría (RF-17 de la 005): el
    // desplazamiento vive aquí y solo al soltar se pide el movimiento.
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val rowHeights = remember { mutableStateMapOf<String, Int>() }
    val spacingPx = with(LocalDensity.current) { ROW_SPACING.toPx() }
    val dragFrom = families.indexOfFirst { it.id == draggingId }
    val step = (draggingId?.let { rowHeights[it] } ?: 0) + spacingPx
    val dragTarget = if (dragFrom >= 0) dragTargetIndex(dragFrom, dragOffset, step, families.size) else -1

    Column(modifier = modifier.navigationBarsPadding()) {
        Text(
            text = stringResource(R.string.family_selector_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .testTag("family_selector_list"),
            verticalArrangement = Arrangement.spacedBy(ROW_SPACING),
        ) {
            families.forEachIndexed { index, family ->
                key(family.id) {
                    val isDragging = draggingId == family.id
                    FamilyRow(
                        family = family,
                        selected = family.id == activeFamilyId,
                        showHandle = families.size > 1,
                        reorderEnabled = canReorder,
                        dragOffset = if (isDragging) dragOffset else 0f,
                        shift = if (dragFrom >= 0 && !isDragging) dragShift(index, dragFrom, dragTarget, step) else 0f,
                        isDragging = isDragging,
                        canMoveUp = canReorder && index > 0,
                        canMoveDown = canReorder && index < families.lastIndex,
                        onHeight = { rowHeights[family.id] = it },
                        onSelect = { onSelect(family.id) },
                        onManage = onManage,
                        onMoveUp = { onMove(family.id, index - 1) },
                        onMoveDown = { onMove(family.id, index + 1) },
                        onDragStart = {
                            draggingId = family.id
                            dragOffset = 0f
                        },
                        onDragTo = { dragOffset = it },
                        onDragEnd = {
                            // Todo se recalcula al soltar a partir de la propia familia: el último movimiento puede llegar
                            // sin haber recompuesto antes, y `dragFrom` y `step` dependen de que ya se hubiera hecho.
                            val moved = family.id
                            val from = families.indexOfFirst { it.id == moved }
                            val releaseStep = (rowHeights[moved] ?: 0) + spacingPx
                            val target = dragTargetIndex(from, dragOffset, releaseStep, families.size)
                            draggingId = null
                            dragOffset = 0f
                            if (from >= 0 && target != from) onMove(moved, target)
                        },
                        onDragCancel = {
                            draggingId = null
                            dragOffset = 0f
                        },
                    )
                }
            }
        }
        if (error != null) {
            Text(
                text = stringResource(error.messageRes()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .testTag("family_selector_error"),
            )
        }
        Button(
            onClick = onCreate,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .heightIn(min = MIN_TOUCH_TARGET)
                .testTag("family_selector_create"),
        ) {
            Icon(imageVector = Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.family_selector_create))
        }
    }
}

private fun FamilyAccessFailure.messageRes(): Int = when (this) {
    FamilyAccessFailure.Offline -> R.string.family_selector_error_offline
    FamilyAccessFailure.Network -> R.string.family_selector_error_network
    FamilyAccessFailure.InvalidOrder -> R.string.family_selector_error_invalid_order
    else -> R.string.family_selector_error_generic
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FamilyRow(
    family: Family,
    selected: Boolean,
    showHandle: Boolean,
    reorderEnabled: Boolean,
    dragOffset: Float,
    shift: Float,
    isDragging: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onHeight: (Int) -> Unit,
    onSelect: () -> Unit,
    onManage: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDragStart: () -> Unit,
    onDragTo: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    val role = family.role.label()
    val members = family.memberCount?.let { pluralStringResource(R.plurals.family_selector_members, it, it) }
    val selectedLabel = stringResource(R.string.family_selector_selected)
    val description = listOfNotNull(family.name, role, members, selectedLabel.takeIf { selected }).joinToString(", ")
    val moveUpLabel = stringResource(R.string.family_selector_move_up)
    val moveDownLabel = stringResource(R.string.family_selector_move_down)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { onHeight(it.height) }
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer { translationY = if (isDragging) dragOffset else shift }
            .testTag("family_row_${family.id}"),
        shape = RoundedCornerShape(16.dp),
        color = when {
            isDragging -> MaterialTheme.colorScheme.surfaceContainerHigh
            // Un tono claro en el tema claro (primaryContainer es verde oscuro y dejaba el texto ilegible).
            selected -> MaterialTheme.colorScheme.surfaceVariant
            else -> MaterialTheme.colorScheme.surface
        },
        shadowElevation = if (isDragging) 6.dp else 0.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Lo que se anuncia y se elige es el cuerpo de la fila; el icono de gestión y el asa son controles aparte.
            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 64.dp)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
                    .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)
                    .clearAndSetSemantics {
                        contentDescription = description
                        this.selected = selected
                        // Alternativa sin arrastre para lector de pantalla (RF-02); solo las que tienen sentido.
                        customActions = buildList {
                            if (canMoveUp) add(CustomAccessibilityAction(moveUpLabel) { onMoveUp(); true })
                            if (canMoveDown) add(CustomAccessibilityAction(moveDownLabel) { onMoveDown(); true })
                        }
                        testTag = "family_item_${family.id}"
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Groups,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(
                        text = family.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // Con texto grande la insignia y el número de miembros pasan a dos líneas en vez de cortarse.
                    FlowRow(
                        itemVerticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        RoleBadge(family.role, role)
                        if (members != null) {
                            Text(
                                text = members,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                if (selected) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (selected) {
                IconButton(onClick = onManage, modifier = Modifier.size(MIN_TOUCH_TARGET).testTag("family_manage")) {
                    Icon(
                        imageVector = Icons.Outlined.Tune,
                        contentDescription = stringResource(R.string.family_selector_manage_description, family.name),
                    )
                }
            }
            if (showHandle) {
                DragHandle(family, reorderEnabled, onDragStart, onDragTo, onDragEnd, onDragCancel)
            } else {
                Spacer(Modifier.width(8.dp))
            }
        }
    }
}

@Composable
private fun RoleBadge(role: FamilyRole, label: String) {
    val owner = role == FamilyRole.OWNER
    Surface(
        shape = RoundedCornerShape(50),
        color = if (owner) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (owner) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            maxLines = 1,
        )
    }
}

/**
 * Asa de arrastre de 48 dp: arrastrarla en vertical mueve la familia; el movimiento se pide al soltar. El desplazamiento
 * se mide en coordenadas de ventana: la fila entera se traslada con el dedo y, medido en coordenadas locales del propio
 * asa, cada gesto se cancelaría en parte con ese desplazamiento. Consume el gesto, así que la hoja no lo toma como un
 * arrastre para cerrarse. El lector de pantalla no la enfoca: usa las acciones de la fila.
 */
@Composable
private fun DragHandle(
    family: Family,
    enabled: Boolean,
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
            .size(MIN_TOUCH_TARGET)
            .onGloballyPositioned { coordinates = it }
            .pointerInput(family.id, enabled) {
                if (!enabled) return@pointerInput
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
            .clearAndSetSemantics { testTag = "family_handle_${family.id}" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.DragHandle,
            contentDescription = null,
            tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

@Composable
private fun FamilyRole.label(): String = when (this) {
    FamilyRole.OWNER -> stringResource(R.string.role_owner)
    FamilyRole.MEMBER -> stringResource(R.string.role_member)
}

@Preview(showBackground = true)
@Composable
private fun FamilySelectorContentPreview() {
    FamilyFinAIAppTheme {
        FamilySelectorContent(
            families = listOf(
                Family("1", "Familia Plata", FamilyRole.OWNER, memberCount = 4),
                Family("2", "Familia de los González Fernández de la Torre", FamilyRole.MEMBER, memberCount = 2),
                Family("3", "Proyecto Vacaciones", FamilyRole.OWNER, memberCount = 1),
            ),
            activeFamilyId = "1",
            error = FamilyAccessFailure.Network,
            isReordering = false,
            onSelect = {},
            onMove = { _, _ -> },
            onManage = {},
            onCreate = {},
        )
    }
}
