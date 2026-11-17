package com.rocioplata.familyfinaiapp.ui.movements

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rocioplata.familyfinaiapp.R
import com.rocioplata.familyfinaiapp.domain.money.Money
import com.rocioplata.familyfinaiapp.domain.movements.MovementType
import com.rocioplata.familyfinaiapp.ui.categories.CategoryIcons
import com.rocioplata.familyfinaiapp.ui.main.StaleDataBanner
import com.rocioplata.familyfinaiapp.ui.theme.Expense
import com.rocioplata.familyfinaiapp.ui.theme.FamilyFinAIAppTheme
import com.rocioplata.familyfinaiapp.ui.theme.Income
import java.time.LocalDate
import java.time.YearMonth

/**
 * Pestaña "Movimientos" (RF-01 a RF-03, RF-15, RF-17): selector de mes, resumen del mes, filtro de tipo,
 * grupos por día con su subtotal y filas con icono, concepto, categoría y etiqueta, quién lo registró y el
 * monto con signo. Sin estado: recibe [state] y callbacks. Los estados de carga, error y vacío conservan el
 * selector de mes y el filtro, para poder cambiar de mes.
 */
@Composable
fun MovementsContent(
    state: MovementsUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onFilterSelected: (MovementFilter) -> Unit,
    onRetry: () -> Unit,
    onMovementClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().testTag("movements_screen")) {
        MonthSelector(state.month, state.canGoNext, onPreviousMonth, onNextMonth)
        FilterRow(state.filter, onFilterSelected)
        val list = state.list
        if (list is MovementsListState.Content) {
            if (list.isStale) StaleDataBanner()
            if (list.isRefreshing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().testTag("movements_refreshing"))
            }
        }
        when (list) {
            MovementsListState.Loading -> LoadingState()
            MovementsListState.Error -> ErrorState(onRetry)
            is MovementsListState.Content -> ContentState(list, onMovementClick)
        }
    }
}

@Composable
private fun MonthSelector(
    month: YearMonth,
    canGoNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onPrevious, modifier = Modifier.testTag("movements_prev_month")) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.movements_month_previous))
        }
        Text(
            text = formatMonth(month),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.testTag("movements_month"),
        )
        IconButton(onClick = onNext, enabled = canGoNext, modifier = Modifier.testTag("movements_next_month")) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.movements_month_next))
        }
    }
}

@Composable
private fun FilterRow(selected: MovementFilter, onSelected: (MovementFilter) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MovementFilter.entries.forEach { filter ->
            FilterChip(
                selected = filter == selected,
                onClick = { onSelected(filter) },
                label = { Text(stringResource(filter.labelRes())) },
                modifier = Modifier.testTag("movements_filter_${filter.name}"),
            )
        }
    }
}

private fun MovementFilter.labelRes(): Int = when (this) {
    MovementFilter.ALL -> R.string.movements_filter_all
    MovementFilter.EXPENSES -> R.string.movements_filter_expenses
    MovementFilter.INCOMES -> R.string.movements_filter_incomes
}

@Composable
private fun LoadingState() {
    Column(
        modifier = Modifier.fillMaxSize().testTag("movements_loading"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Text(
            text = stringResource(R.string.movements_loading),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

@Composable
private fun ErrorState(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp).testTag("movements_error"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.movements_error_load),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
        )
        TextButton(onClick = onRetry, modifier = Modifier.padding(top = 8.dp).testTag("movements_retry")) {
            Text(stringResource(R.string.movements_retry))
        }
    }
}

@Composable
private fun ContentState(content: MovementsListState.Content, onMovementClick: (String) -> Unit) {
    // El espacio final deja ver la última fila por encima del botón "+".
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("movements_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
    ) {
        content.summary?.let { summary ->
            item(key = "summary") { SummaryCard(summary) }
        }
        when {
            content.isMonthEmpty -> item(key = "empty") { EmptyMessage(R.string.movements_empty_month, "movements_empty") }
            content.isFilterEmpty ->
                item(key = "filter-empty") { EmptyMessage(R.string.movements_empty_filter, "movements_filter_empty") }
        }
        content.groups.forEach { group ->
            item(key = "day-${group.date.toEpochDay()}") { DayHeader(group) }
            items(group.rows, key = { it.id }) { row ->
                MovementRowItem(row, onClick = { onMovementClick(row.id) })
                if (row != group.rows.last()) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun SummaryCard(summary: MonthSummary) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).testTag("movements_summary"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.movements_summary_balance),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = summary.balance.formattedSigned(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag("movements_summary_balance"),
            )
            Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryAmount(
                    label = stringResource(R.string.movements_summary_income),
                    text = summary.income.formattedFor(MovementType.INCOME),
                    color = Income,
                    tag = "movements_summary_income",
                    modifier = Modifier.weight(1f),
                )
                SummaryAmount(
                    label = stringResource(R.string.movements_summary_expenses),
                    text = summary.expenses.formattedFor(MovementType.EXPENSE),
                    color = Expense,
                    tag = "movements_summary_expenses",
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SummaryAmount(label: String, text: String, color: Color, tag: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.semantics(mergeDescendants = true) {}.testTag(tag)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun EmptyMessage(messageRes: Int, tag: String) {
    Text(
        text = stringResource(messageRes),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp).testTag(tag),
    )
}

@Composable
private fun DayHeader(group: DayGroup) {
    val subtotal = group.subtotal.formattedSigned()
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp)
            .semantics(mergeDescendants = true) {}.testTag("movements_day_${group.date.toEpochDay()}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = formatDay(group.date),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = subtotal,
            style = MaterialTheme.typography.labelLarge,
            color = if (group.subtotal.amountMinor < 0) Expense else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MovementRowItem(row: MovementRow, onClick: () -> Unit) {
    val context = LocalContext.current
    val amountText = row.amount.formattedFor(row.type)
    val typeText = stringResource(row.type.labelRes())
    val categoryText = row.categoryName ?: stringResource(R.string.movements_category_unknown)
    val recordedByText = row.recordedBy.text(context::getString)
    val description = listOfNotNull(
        stringResource(R.string.movements_row_description, typeText, row.title, amountText),
        categoryText,
        row.tagName,
        recordedByText,
    ).joinToString(", ")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(vertical = 12.dp)
            .testTag("movement_row_${row.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = CategoryIcons.find(row.categoryIcon) ?: CategoryIcons.generic
        Box(
            modifier = Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                text = row.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(categoryText, row.tagName?.let { "#$it" }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (recordedByText != null) RecordedBy(row.recordedBy, context::getString)
        }
        Text(
            text = amountText,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (row.type == MovementType.EXPENSE) Expense else Income,
            maxLines = 1,
        )
    }
}

private fun MovementType.labelRes(): Int = when (this) {
    MovementType.EXPENSE -> R.string.movements_type_expense
    MovementType.INCOME -> R.string.movements_type_income
}

/** Iniciales y nombre de quien lo registró; el propio usuario lleva además la marca "Tú" (RF-15). Nunca el correo. */
@Composable
private fun RecordedBy(label: MemberLabel, getString: (Int) -> String) {
    val name = if (label is MemberLabel.You) label.displayName else label.text(getString)
    Row(modifier = Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(20.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            val initials = label.initials
            if (initials != null) {
                Text(initials, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Icon(Icons.Outlined.Person, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
        if (name != null) {
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 6.dp, end = 6.dp).weight(1f, fill = false),
            )
        }
        if (label is MemberLabel.You) {
            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondaryContainer) {
                Text(
                    text = getString(R.string.movements_recorded_by_you),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
    }
}

private val previewRows = listOf(
    MovementRow(
        "1", MovementType.EXPENSE, "Supermercado Jumbo", Money(38_450, "CLP"), "Comestibles", "shopping_cart",
        "supermercado", "Tarjeta", MemberLabel.You("Valentina Plata", null),
    ),
    MovementRow(
        "2", MovementType.INCOME, "Sueldo", Money(1_200_000, "CLP"), "Sueldo", null, null, null,
        MemberLabel.FormerMember,
    ),
)

@Preview(showBackground = true)
@Composable
private fun MovementsContentPreview() {
    FamilyFinAIAppTheme {
        MovementsContent(
            state = MovementsUiState(
                month = YearMonth.of(2026, 11),
                canGoNext = false,
                filter = MovementFilter.ALL,
                list = MovementsListState.Content(
                    summary = MonthSummary(Money(1_200_000, "CLP"), Money(38_450, "CLP"), Money(1_161_550, "CLP")),
                    groups = listOf(DayGroup(LocalDate.of(2026, 11, 12), Money(1_161_550, "CLP"), previewRows)),
                    isMonthEmpty = false, isFilterEmpty = false, isStale = false, isRefreshing = false,
                ),
            ),
            onPreviousMonth = {}, onNextMonth = {}, onFilterSelected = {}, onRetry = {}, onMovementClick = {},
        )
    }
}
