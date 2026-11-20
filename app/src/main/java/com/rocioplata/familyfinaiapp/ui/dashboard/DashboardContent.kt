package com.rocioplata.familyfinaiapp.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rocioplata.familyfinaiapp.R
import com.rocioplata.familyfinaiapp.domain.categories.Category
import com.rocioplata.familyfinaiapp.domain.categories.CategoryStatus
import com.rocioplata.familyfinaiapp.domain.categories.CategoryType
import com.rocioplata.familyfinaiapp.domain.dashboard.CategoryAmount
import com.rocioplata.familyfinaiapp.domain.dashboard.CategoryBreakdown
import com.rocioplata.familyfinaiapp.domain.dashboard.ChangeDirection
import com.rocioplata.familyfinaiapp.domain.dashboard.ChangeOutcome
import com.rocioplata.familyfinaiapp.domain.dashboard.ChartSegment
import com.rocioplata.familyfinaiapp.domain.dashboard.MonthComparison
import com.rocioplata.familyfinaiapp.domain.dashboard.MonthTotals
import com.rocioplata.familyfinaiapp.domain.money.Money
import com.rocioplata.familyfinaiapp.domain.movements.MovementType
import com.rocioplata.familyfinaiapp.ui.categories.CategoryColors
import com.rocioplata.familyfinaiapp.ui.categories.CategoryIcons
import com.rocioplata.familyfinaiapp.ui.categories.displayColor
import com.rocioplata.familyfinaiapp.ui.categories.softBackground
import com.rocioplata.familyfinaiapp.ui.main.StaleDataBanner
import com.rocioplata.familyfinaiapp.ui.movements.formatMonth
import com.rocioplata.familyfinaiapp.ui.movements.formatted
import com.rocioplata.familyfinaiapp.ui.theme.Expense
import com.rocioplata.familyfinaiapp.ui.theme.FamilyFinAIAppTheme
import com.rocioplata.familyfinaiapp.ui.theme.Income
import java.math.BigDecimal
import java.time.YearMonth

/**
 * Cuerpo del dashboard de Inicio, sin estado propio (RF-01 a RF-11): el selector de mes con «Mes actual», la
 * tarjeta de desglose (Gastos e Ingresos, lo disponible, la comparación con el mes anterior y el gráfico circular)
 * y la sección por categoría del tipo activo, con «Gestionar» y una tarjeta por categoría. Los estados de carga y
 * error conservan el selector de mes, para poder cambiar de mes. Solo dibuja el estado recibido y avisa de lo que
 * toca la persona: [onCategoryClick] con el id de la categoría activa y el tipo activo, [onManageCategories] y
 * [onRetry]. Deja espacio al final para que el botón «+» no tape la última fila.
 */
@Composable
fun DashboardContent(
    state: DashboardUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onCurrentMonth: () -> Unit,
    onSelectType: (MovementType) -> Unit,
    modifier: Modifier = Modifier,
    onCategoryClick: (categoryId: String, type: MovementType) -> Unit = { _, _ -> },
    onManageCategories: () -> Unit = {},
    onRetry: () -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxWidth().testTag("dashboard_content"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        MonthSelector(state, onPreviousMonth, onNextMonth, onCurrentMonth)
        when (val content = state.content) {
            DashboardContentState.Loading -> LoadingState()
            DashboardContentState.Error -> ErrorState(onRetry)
            is DashboardContentState.Content -> {
                // Aviso de datos de más de 24 h y barra mientras se actualiza (CA-16).
                if (content.isStale) StaleDataBanner(modifier = Modifier.clip(RoundedCornerShape(12.dp)).testTag("dashboard_stale"))
                if (content.isRefreshing) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().testTag("dashboard_refreshing"))
                }
                BreakdownCard(state.activeType, content, onSelectType)
                if (content.isMonthEmpty) EmptyMonthMessage()
                CategoriesSection(state.activeType, content.breakdown, onCategoryClick, onManageCategories)
            }
        }
        Spacer(modifier = Modifier.height(BOTTOM_SPACE_FOR_FAB))
    }
}

@Composable
private fun LoadingState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp).testTag("dashboard_loading"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CircularProgressIndicator()
        Text(
            text = stringResource(R.string.dashboard_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun ErrorState(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp).testTag("dashboard_error"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.dashboard_error),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
        )
        TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp).testTag("dashboard_retry")) {
            Text(stringResource(R.string.dashboard_retry))
        }
    }
}

/** Mes sin movimientos (CA-15): los totales y las categorías se ven en cero y esto invita a registrar uno. */
@Composable
private fun EmptyMonthMessage() {
    Text(
        text = stringResource(R.string.dashboard_empty_month),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().testTag("dashboard_empty_month"),
    )
}

/**
 * Sección por categoría del tipo activo (RF-07 a RF-11): título, cuántas se muestran, «Gestionar» y una tarjeta por
 * categoría. Con [breakdown] nulo las categorías aún no tienen datos; sin tarjetas se explica y «Gestionar» sigue.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoriesSection(
    type: MovementType,
    breakdown: CategoryBreakdown?,
    onCategoryClick: (String, MovementType) -> Unit,
    onManage: () -> Unit,
) {
    val manageDescription = stringResource(R.string.categories_home_manage_description)
    Column(modifier = Modifier.fillMaxWidth().testTag("dashboard_categories"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            // Con texto grande el contador pasa a la línea de abajo en lugar de partirse.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(type.categoriesTitleRes()),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.semantics { heading() }.testTag("dashboard_categories_title"),
                )
                if (breakdown != null && breakdown.count > 0) {
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Text(
                            text = pluralStringResource(R.plurals.dashboard_categories_count, breakdown.count, breakdown.count),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp).testTag("dashboard_categories_count"),
                        )
                    }
                }
            }
            TextButton(
                onClick = onManage,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("dashboard_manage_categories")
                    .semantics { contentDescription = manageDescription },
            ) {
                Text(stringResource(R.string.categories_home_manage))
            }
        }
        when {
            breakdown == null -> Text(
                text = stringResource(R.string.dashboard_categories_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("dashboard_categories_loading"),
            )
            breakdown.entries.isEmpty() -> Text(
                text = stringResource(type.noCategoriesRes()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("dashboard_categories_empty"),
            )
            else -> CategoryGrid(type, breakdown.entries, onCategoryClick)
        }
    }
}

/**
 * Cuadrícula adaptativa: tantas columnas como caben con un ancho mínimo por tarjeta (tres en un teléfono normal);
 * con texto grande el ancho mínimo crece y quedan menos columnas, hasta una sola, para que nada se corte. Va dentro de
 * un contenedor que se desplaza, así que no usa una cuadrícula perezosa.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryGrid(type: MovementType, entries: List<CategoryAmount>, onCategoryClick: (String, MovementType) -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val minCell = CATEGORY_MIN_CELL_WIDTH * LocalDensity.current.fontScale.coerceIn(1f, 2f)
        val columns = ((maxWidth + CATEGORY_GRID_SPACING) / (minCell + CATEGORY_GRID_SPACING)).toInt().coerceAtLeast(1)
        // Un dp menos: al redondear a píxeles, columnas de ancho exacto podrían no caber y la última pasaría a otra fila.
        val cellWidth = (maxWidth - CATEGORY_GRID_SPACING * (columns - 1)) / columns - 1.dp
        FlowRow(
            maxItemsInEachRow = columns,
            horizontalArrangement = Arrangement.spacedBy(CATEGORY_GRID_SPACING),
            verticalArrangement = Arrangement.spacedBy(CATEGORY_GRID_SPACING),
            modifier = Modifier.fillMaxWidth().testTag("dashboard_category_grid"),
        ) {
            entries.forEach { entry ->
                CategoryCard(entry, type, onCategoryClick, Modifier.width(cellWidth).fillMaxRowHeight())
            }
        }
    }
}

/**
 * Tarjeta de una categoría (RF-07, RF-08, RF-11): icono en su color, nombre en hasta dos líneas y monto. Las de monto
 * cero se atenúan; las archivadas llevan «Archivada» y no se pueden tocar; las activas abren «Nuevo movimiento» con
 * el tipo y la categoría. Es una sola unidad para el lector de pantalla.
 */
@Composable
private fun CategoryCard(
    entry: CategoryAmount,
    type: MovementType,
    onCategoryClick: (String, MovementType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val category = entry.category
    val tone = entry.displayColor()
    val icon = CategoryIcons.displayOf(category.icon)
    val formatted = entry.amount.formatted()
    val description = stringResource(
        if (entry.isArchived) R.string.dashboard_category_card_archived_description else R.string.dashboard_category_card_description,
        category.name,
        formatted,
    )
    val action = stringResource(R.string.dashboard_category_card_action, category.name)
    // La etiqueta y la acción van antes de `clearAndSetSemantics`: lo que se declara después se descarta.
    val tagged = modifier.testTag("dashboard_category_${category.id}")
    val interactive = if (entry.isArchived) {
        tagged
    } else {
        tagged.clickable(onClickLabel = action, role = Role.Button) { onCategoryClick(category.id, type) }
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (entry.isArchived) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = interactive.clearAndSetSemantics { contentDescription = description },
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 12.dp)
                .alpha(if (entry.hasAmount) 1f else DIMMED_ALPHA),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(shape = RoundedCornerShape(12.dp), color = tone.softBackground(), modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = icon.icon,
                    contentDescription = null,
                    tint = tone,
                    modifier = Modifier.padding(8.dp),
                )
            }
            Text(
                text = category.name,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            if (entry.isArchived) {
                Text(
                    text = stringResource(R.string.dashboard_category_archived),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("dashboard_category_archived"),
                )
            }
            val amountStyle = MaterialTheme.typography.titleSmall
            // La altura de la fila de tarjetas se calcula con medidas intrínsecas y un texto de tamaño automático se mide más
            // pequeño de lo que luego se dibuja (con texto grande, la escala no lineal de las fuentes hace que la altura de
            // línea en dp quede por debajo de la del texto). Se reserva algo más que una línea y el texto no hereda un
            // límite de altura, para que no se recorte.
            val lineHeight = with(LocalDensity.current) { amountStyle.lineHeight.toDp() }
            Box(modifier = Modifier.fillMaxWidth().heightIn(min = lineHeight * AMOUNT_LINE_RESERVE), contentAlignment = Alignment.Center) {
                Text(
                    text = formatted,
                    style = amountStyle,
                    maxLines = 1,
                    softWrap = false,
                    autoSize = TextAutoSize.StepBased(minFontSize = MIN_CARD_AMOUNT_SIZE, maxFontSize = amountStyle.fontSize),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().wrapContentHeight(unbounded = true).testTag("dashboard_category_amount"),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MonthSelector(
    state: DashboardUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onCurrentMonth: () -> Unit,
) {
    // Con texto grande o poco ancho, «Mes actual» pasa a la línea de abajo en lugar de cortarse.
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPreviousMonth, modifier = Modifier.testTag("dashboard_previous_month")) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.dashboard_month_previous))
                }
                Icon(
                    imageVector = Icons.Filled.CalendarMonth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = formatMonth(state.month),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 8.dp).testTag("dashboard_month_label"),
                )
                IconButton(
                    onClick = onNextMonth,
                    enabled = state.canGoNext,
                    modifier = Modifier.testTag("dashboard_next_month"),
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.dashboard_month_next))
                }
            }
        }
        OutlinedButton(
            onClick = onCurrentMonth,
            enabled = !state.isCurrentMonth,
            modifier = Modifier.heightIn(min = 48.dp).testTag("dashboard_current_month"),
        ) {
            Text(stringResource(R.string.dashboard_current_month))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BreakdownCard(
    type: MovementType,
    content: DashboardContentState.Content,
    onSelectType: (MovementType) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().testTag("dashboard_breakdown_card"),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = Icons.Filled.DonutLarge,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = stringResource(type.breakdownTitleRes()),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() }.testTag("dashboard_breakdown_title"),
                    )
                }
                content.comparison?.let { VariationChip(it) }
            }
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                // Con texto grande las tarjetas y el gráfico no caben uno al lado del otro: pasan a una columna.
                val fontScale = LocalDensity.current.fontScale
                val wide = maxWidth >= SIDE_BY_SIDE_MIN_WIDTH && fontScale <= SIDE_BY_SIDE_MAX_FONT_SCALE
                // En una columna el gráfico crece con el texto (sin pasar del ancho disponible) para que quepa su total.
                val chartSize = if (wide) DonutChartDefaultSize else minOf(maxWidth, DonutChartDefaultSize * fontScale.coerceIn(1f, 2f))
                val tiles: @Composable (Modifier) -> Unit = { m ->
                    Column(modifier = m, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        TypeTiles(type, content, onSelectType)
                        AvailableRow(content.totals)
                    }
                }
                val chart: @Composable () -> Unit = { Chart(type, content, chartSize) }
                if (wide) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        tiles(Modifier.weight(1f))
                        chart()
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        tiles(Modifier.fillMaxWidth())
                        chart()
                    }
                }
            }
        }
    }
}

/** Indicador de comparación con el mes anterior (RF-06): flecha y texto con signo, verde, rojo o neutro. */
@Composable
private fun VariationChip(comparison: MonthComparison) {
    val tone = when (comparison.outcome) {
        ChangeOutcome.FAVORABLE -> Income
        ChangeOutcome.UNFAVORABLE -> Expense
        ChangeOutcome.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val icon = when (comparison.direction) {
        ChangeDirection.UP -> Icons.AutoMirrored.Filled.TrendingUp
        ChangeDirection.DOWN -> Icons.AutoMirrored.Filled.TrendingDown
        ChangeDirection.UNCHANGED -> Icons.AutoMirrored.Filled.TrendingFlat
    }
    val magnitude = formatPercent(comparison.variationPercent.abs())
    val description = if (comparison.direction == ChangeDirection.UNCHANGED) {
        stringResource(comparison.direction.variationDescriptionRes())
    } else {
        stringResource(comparison.direction.variationDescriptionRes(), magnitude)
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = tone.softBackground(),
        border = BorderStroke(1.dp, tone),
        // La etiqueta va antes de `clearAndSetSemantics`: lo que se declara después se descarta.
        modifier = Modifier.testTag("dashboard_variation").clearAndSetSemantics { contentDescription = description },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = tone, modifier = Modifier.size(18.dp))
            Text(
                text = stringResource(R.string.dashboard_variation_vs_previous, comparison.formattedVariation()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag("dashboard_variation_text"),
            )
        }
    }
}

/** Las tarjetas Gastos e Ingresos: la del tipo activo resaltada y anunciada como seleccionada (RF-03). */
@Composable
private fun TypeTiles(
    active: MovementType,
    content: DashboardContentState.Content,
    onSelectType: (MovementType) -> Unit,
) {
    Column(modifier = Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MovementType.entries.forEach { type ->
            TypeTile(
                type = type,
                amount = content.totals.totalOf(type),
                selected = type == active,
                percent = if (type == active) content.percentOfIncome else null,
                onClick = { onSelectType(type) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TypeTile(
    type: MovementType,
    amount: Money,
    selected: Boolean,
    percent: BigDecimal?,
    onClick: () -> Unit,
) {
    val tone = if (type == MovementType.EXPENSE) Expense else Income
    val name = stringResource(type.typeLabelRes())
    val formatted = amount.formatted()
    val description = stringResource(
        if (selected) R.string.dashboard_type_tile_selected_description else R.string.dashboard_type_tile_description,
        name,
        formatted,
    )
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) tone.softBackground() else MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) tone else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag(if (type == MovementType.EXPENSE) "dashboard_type_expenses" else "dashboard_type_income")
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // Con texto grande el nombre y el porcentaje pasan a líneas distintas en lugar de partir las palabras.
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        imageVector = if (type == MovementType.EXPENSE) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                        contentDescription = null,
                        tint = tone,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = name,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (percent != null) {
                        Surface(shape = RoundedCornerShape(12.dp), color = tone.softBackground()) {
                            Text(
                                text = formatPercent(percent),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp).testTag("dashboard_type_percent"),
                            )
                        }
                    }
                    if (!selected) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            val amountStyle = if (selected) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium
            // El monto nunca se corta: si no cabe en una línea se reduce el tamaño del texto.
            Text(
                text = formatted,
                style = amountStyle,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
                autoSize = TextAutoSize.StepBased(minFontSize = MIN_AMOUNT_SIZE, maxFontSize = amountStyle.fontSize),
                modifier = Modifier.fillMaxWidth().testTag("dashboard_type_amount"),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AvailableRow(totals: MonthTotals) {
    // Si el monto no cabe junto al texto, pasa a la línea de abajo.
    FlowRow(
        modifier = Modifier.fillMaxWidth().testTag("dashboard_available"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.dashboard_available),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = totals.available.formatted(),
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(
                minFontSize = MIN_AMOUNT_SIZE,
                maxFontSize = MaterialTheme.typography.titleSmall.fontSize,
            ),
            modifier = Modifier.testTag("dashboard_available_amount"),
        )
    }
}

/** El gráfico del tipo activo: segmentos con el color de cada categoría y, en el centro, el total y el porcentaje. */
@Composable
private fun Chart(type: MovementType, content: DashboardContentState.Content, size: Dp) {
    val total = content.totals.totalOf(type)
    val segments = content.breakdown?.segments.orEmpty()
    val slices = segments.map { DonutSlice(it.share.toFloat(), it.entry.displayColor()) }
    val header = stringResource(type.chartDescriptionRes(), total.formatted())
    val caption = content.percentOfIncome?.let {
        val text = formatPercent(it)
        if (type == MovementType.INCOME) stringResource(R.string.dashboard_chart_received, text) else text
    }
    DonutChart(
        slices = slices,
        centerLabel = stringResource(R.string.dashboard_chart_total),
        centerTotal = total.formatted(),
        centerCaption = caption,
        description = chartDescription(header, segments),
        size = size,
    )
}

@Composable
private fun CategoryAmount.displayColor(): Color =
    CategoryColors.resolve(category.color, colorPosition).displayColor()

private val SIDE_BY_SIDE_MIN_WIDTH = 330.dp
private val CATEGORY_MIN_CELL_WIDTH = 104.dp
private val CATEGORY_GRID_SPACING = 8.dp
private val BOTTOM_SPACE_FOR_FAB = 88.dp
private val MIN_CARD_AMOUNT_SIZE = 11.sp
private const val AMOUNT_LINE_RESERVE = 1.15f
private const val DIMMED_ALPHA = 0.55f
private const val SIDE_BY_SIDE_MAX_FONT_SCALE = 1.3f
private val MIN_AMOUNT_SIZE = 12.sp

// --- Vistas previas ---

private fun previewCategory(id: String, name: String, color: String) =
    Category(id, CategoryType.EXPENSE, name, null, CategoryStatus.ACTIVE, emptyList(), color)

private fun previewState(
    type: MovementType = MovementType.EXPENSE,
    comparison: MonthComparison? = MonthComparison(
        Money(1_120_450, "CLP"), Money(1_177_000, "CLP"), BigDecimal("-4.8"), ChangeDirection.DOWN, ChangeOutcome.FAVORABLE,
    ),
    withSegments: Boolean = true,
    canGoNext: Boolean = true,
): DashboardUiState {
    val food = CategoryAmount(previewCategory("c1", "Comestibles", "sage"), Money(425_800, "CLP"), 0)
    val health = CategoryAmount(previewCategory("c2", "Salud", "amber"), Money(115_000, "CLP"), 1)
    val other = CategoryAmount(previewCategory("c3", "Restaurantes", "slate"), Money(579_650, "CLP"), 2)
    val total = Money(1_120_450, "CLP")
    val segments = if (withSegments) {
        listOf(food, health, other).map { ChartSegment(it, BigDecimal(it.amount.amountMinor).divide(BigDecimal(total.amountMinor), 10, java.math.RoundingMode.HALF_UP)) }
    } else {
        emptyList()
    }
    val totals = MonthTotals(income = Money(1_850_000, "CLP"), expenses = if (withSegments) total else Money(0, "CLP"))
    return DashboardUiState(
        month = YearMonth.of(2026, 10), canGoNext = canGoNext, isCurrentMonth = !canGoNext, activeType = type,
        content = DashboardContentState.Content(
            totals = totals,
            percentOfIncome = totals.percentOfIncome(type),
            breakdown = CategoryBreakdown(totals.totalOf(type), listOf(food, health, other), segments),
            comparison = comparison, isMonthEmpty = !withSegments, isStale = false, isRefreshing = false,
        ),
    )
}

@Composable
private fun PreviewHost(state: DashboardUiState) {
    FamilyFinAIAppTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            DashboardContent(
                state = state, onPreviousMonth = {}, onNextMonth = {}, onCurrentMonth = {}, onSelectType = {},
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Preview(showBackground = true, name = "Gastos")
@Composable
private fun DashboardExpensesPreview() = PreviewHost(previewState())

@Preview(showBackground = true, name = "Ingresos")
@Composable
private fun DashboardIncomePreview() = PreviewHost(previewState(type = MovementType.INCOME))

@Preview(showBackground = true, name = "Mes vacío, mes en curso")
@Composable
private fun DashboardEmptyPreview() = PreviewHost(previewState(comparison = null, withSegments = false, canGoNext = false))
