package com.rocioplata.familyfinaiapp.ui.categories

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rocioplata.familyfinaiapp.R
import com.rocioplata.familyfinaiapp.domain.categories.Category
import com.rocioplata.familyfinaiapp.domain.categories.CategoryStatus
import com.rocioplata.familyfinaiapp.domain.categories.CategoryType
import com.rocioplata.familyfinaiapp.domain.categories.Tag
import com.rocioplata.familyfinaiapp.domain.categories.TagStatus
import com.rocioplata.familyfinaiapp.ui.main.StaleDataBanner
import com.rocioplata.familyfinaiapp.ui.theme.FamilyFinAIAppTheme

/**
 * Listado de categorías de la familia activa (RF-01 a RF-07, RF-20): barra con flecha, título y lupa
 * (que abre el buscador), pestañas «Gastos»/«Ingresos» con su contador, «Crear nueva categoría», filas
 * con icono, nombre y tags, la sección plegable de archivadas y los estados de carga, error y vacío.
 * Sin estado: recibe [state] y callbacks. [canManage] es verdadero solo para el Propietario (RF-07).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesContent(
    state: CategoriesUiState,
    canManage: Boolean,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSelectType: (CategoryType) -> Unit,
    onRetry: () -> Unit,
    onCategoryClick: (String) -> Unit,
    onCreateClick: (CategoryType) -> Unit,
    onToggleArchived: () -> Unit,
    onDeleteArchived: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Abierto de entrada si ya hay una búsqueda (por ejemplo, tras recrear la pantalla).
    var searchOpen by rememberSaveable { mutableStateOf((state as? CategoriesUiState.Content)?.query?.isNotEmpty() == true) }
    val query = (state as? CategoriesUiState.Content)?.query.orEmpty()
    val closeSearch = {
        searchOpen = false
        onQueryChange("")
    }
    // Atrás cierra primero el buscador y solo después sale de la pantalla.
    BackHandler(enabled = searchOpen, onBack = closeSearch)

    Scaffold(
        modifier = modifier.testTag("categories_screen"),
        topBar = {
            Column {
                if (searchOpen) {
                    SearchBar(query = query, onQueryChange = onQueryChange, onClose = closeSearch)
                } else {
                    TopAppBar(
                        title = { Text(stringResource(R.string.categories_title)) },
                        navigationIcon = {
                            IconButton(onClick = onBack, modifier = Modifier.testTag("categories_back")) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.categories_back_description),
                                )
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = { searchOpen = true },
                                modifier = Modifier.testTag("categories_search_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = stringResource(R.string.categories_search_description),
                                )
                            }
                        },
                    )
                }
                if ((state as? CategoriesUiState.Content)?.isStale == true) StaleDataBanner()
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding).imePadding()) {
            when (state) {
                CategoriesUiState.Loading -> LoadingState()
                CategoriesUiState.Error -> ErrorState(onRetry)
                is CategoriesUiState.Content -> ContentState(
                    state = state,
                    canManage = canManage,
                    onSelectType = onSelectType,
                    onCategoryClick = onCategoryClick,
                    onCreateClick = onCreateClick,
                    onToggleArchived = onToggleArchived,
                    onDeleteArchived = onDeleteArchived,
                )
            }
        }
    }
}

/** Barra de búsqueda (RF-05): sustituye al título; la flecha la cierra y borra el texto. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(query: String, onQueryChange: (String) -> Unit, onClose: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    TopAppBar(
        title = {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).testTag("categories_search_field"),
                placeholder = { Text(stringResource(R.string.categories_search_placeholder), maxLines = 1) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }, modifier = Modifier.testTag("categories_search_clear")) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.categories_search_clear_description),
                            )
                        }
                    }
                },
            )
        },
        navigationIcon = {
            IconButton(onClick = onClose, modifier = Modifier.testTag("categories_search_close")) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.categories_search_close_description),
                )
            }
        },
    )
}

@Composable
private fun LoadingState() {
    Column(
        modifier = Modifier.fillMaxSize().testTag("categories_loading"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Text(
            text = stringResource(R.string.categories_loading),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

@Composable
private fun ErrorState(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp).testTag("categories_error"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.categories_error_load),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
        )
        TextButton(onClick = onRetry, modifier = Modifier.padding(top = 8.dp).testTag("categories_retry")) {
            Text(stringResource(R.string.categories_retry))
        }
    }
}

@Composable
private fun ContentState(
    state: CategoriesUiState.Content,
    canManage: Boolean,
    onSelectType: (CategoryType) -> Unit,
    onCategoryClick: (String) -> Unit,
    onCreateClick: (CategoryType) -> Unit,
    onToggleArchived: () -> Unit,
    onDeleteArchived: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TypeTabs(state, onSelectType)
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("categories_list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (canManage) {
                item(key = "create") {
                    Button(
                        onClick = { onCreateClick(state.selectedType) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("categories_create"),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text(stringResource(R.string.categories_create_new), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
            if (state.isEmpty) item(key = "empty") { EmptyState(state.selectedType, canManage) }
            if (state.noResults) item(key = "no_results") { NoResultsState() }
            items(state.active, key = { it.category.id }) { item ->
                CategoryRow(item, onClick = { onCategoryClick(item.category.id) })
            }
            if (state.archived.isNotEmpty()) {
                item(key = "archived_header") {
                    ArchivedHeader(count = state.archived.size, expanded = state.archivedExpanded, onClick = onToggleArchived)
                }
                if (state.archivedExpanded) {
                    items(state.archived, key = { "archived_${it.category.id}" }) { item ->
                        ArchivedRow(
                            item = item,
                            canManage = canManage,
                            onClick = { onCategoryClick(item.category.id) },
                            onDelete = { onDeleteArchived(item.category.id) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TypeTabs(state: CategoriesUiState.Content, onSelectType: (CategoryType) -> Unit) {
    val types = listOf(
        Triple(CategoryType.EXPENSE, R.string.categories_tab_expenses, state.expenseActiveCount),
        Triple(CategoryType.INCOME, R.string.categories_tab_income, state.incomeActiveCount),
    )
    PrimaryTabRow(
        selectedTabIndex = types.indexOfFirst { it.first == state.selectedType },
        modifier = Modifier.fillMaxWidth(),
    ) {
        types.forEach { (type, labelRes, count) ->
            Tab(
                selected = state.selectedType == type,
                onClick = { onSelectType(type) },
                modifier = Modifier.testTag("categories_tab_${type.name.lowercase()}"),
                text = {
                    // Con texto grande el contador pasa debajo de la etiqueta en vez de quedar cortado.
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.Center,
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(labelRes))
                        Text(
                            text = pluralStringResource(R.plurals.categories_active_count, count, count),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun EmptyState(type: CategoryType, canManage: Boolean) {
    val message = if (type == CategoryType.EXPENSE) R.string.categories_empty_expense else R.string.categories_empty_income
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp).testTag("categories_empty"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(message), style = MaterialTheme.typography.bodyLarge)
        if (canManage) {
            Text(
                text = stringResource(R.string.categories_empty_owner_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CategoryRow(item: CategoryItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val icon = CategoryIcons.displayOf(item.category.icon)
    Surface(
        modifier = modifier.fillMaxWidth().testTag("category_row_${item.category.id}"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Row(
            modifier = Modifier
                .clickable(role = Role.Button, onClick = onClick)
                .heightIn(min = 72.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon.icon,
                        contentDescription = stringResource(icon.descriptionRes),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.category.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.activeTags.isNotEmpty()) {
                    Text(
                        text = item.activeTags.joinToString(" · ") { it.name },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NoResultsState() {
    Text(
        text = stringResource(R.string.categories_search_no_results),
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp).testTag("categories_no_results"),
    )
}

/** Cabecera plegable de «Categorías archivadas» con su cantidad (RF-04). */
@Composable
private fun ArchivedHeader(count: Int, expanded: Boolean, onClick: () -> Unit) {
    val description = pluralStringResource(R.plurals.categories_archived_section_description, count, count)
    val actionLabel = stringResource(
        if (expanded) R.string.categories_archived_collapse_description else R.string.categories_archived_expand_description,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClickLabel = actionLabel, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(vertical = 8.dp)
            .testTag("categories_archived_header"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.categories_archived_section_title),
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

/**
 * Fila de una categoría archivada: atenuada y con el texto «Archivada» (no solo el color). Solo el
 * Propietario ve el menú ⋮ con «Eliminar» (RF-13); la acción se conecta en T-21 y T-22.
 */
@Composable
private fun ArchivedRow(item: CategoryItem, canManage: Boolean, onClick: () -> Unit, onDelete: () -> Unit) {
    val icon = CategoryIcons.displayOf(item.category.icon)
    var menuOpen by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("category_row_${item.category.id}"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier
                .clickable(role = Role.Button, onClick = onClick)
                .heightIn(min = 72.dp)
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = if (canManage) 4.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                modifier = Modifier.size(48.dp).alpha(DIMMED_ALPHA),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(imageVector = icon.icon, contentDescription = stringResource(icon.descriptionRes))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.category.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = DIMMED_ALPHA),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.categories_status_archived),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            if (canManage) {
                Box {
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.testTag("archived_menu_${item.category.id}")) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.categories_archived_menu_description, item.category.name),
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.categories_archived_menu_delete)) },
                            onClick = {
                                menuOpen = false
                                onDelete()
                            },
                            modifier = Modifier.testTag("archived_delete_${item.category.id}"),
                        )
                    }
                }
            }
        }
    }
}

private const val DIMMED_ALPHA = 0.7f

private fun previewItem(id: String, name: String, icon: String?, vararg tags: String) = CategoryItem(
    category = Category(id, CategoryType.EXPENSE, name, icon, CategoryStatus.ACTIVE, emptyList()),
    activeTags = tags.mapIndexed { index, tag -> Tag("$id-$index", tag, TagStatus.ACTIVE, index) },
)

private fun previewContent(
    active: List<CategoryItem>,
    type: CategoryType = CategoryType.EXPENSE,
    isEmpty: Boolean = false,
    isStale: Boolean = false,
) = CategoriesUiState.Content(
    selectedType = type, expenseActiveCount = active.size, incomeActiveCount = 0, query = "",
    active = active, archived = emptyList(), archivedExpanded = false, isEmpty = isEmpty,
    noResults = false, isStale = isStale, isRefreshing = false,
)

@Composable
private fun PreviewHost(state: CategoriesUiState, canManage: Boolean = true) {
    FamilyFinAIAppTheme {
        CategoriesContent(
            state = state, canManage = canManage, onBack = {}, onQueryChange = {}, onSelectType = {},
            onRetry = {}, onCategoryClick = {}, onCreateClick = {}, onToggleArchived = {}, onDeleteArchived = {},
        )
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Listado")
@Composable
private fun CategoriesContentPreview() = PreviewHost(
    previewContent(
        listOf(
            previewItem("c1", "Alimentación", "shopping_cart", "Súper", "Feria", "Despensa"),
            previewItem("c2", "Servicios básicos con un nombre muy largo que no cabe", "bolt", "Luz", "Agua", "Gas"),
            previewItem("c3", "Sin icono", null),
            previewItem("c4", "Icono desconocido", "no_existe", "Uno"),
        ),
        isStale = true,
    ),
)

@Preview(showBackground = true, name = "Vacío (Propietario)")
@Composable
private fun CategoriesEmptyPreview() =
    PreviewHost(previewContent(emptyList(), type = CategoryType.INCOME, isEmpty = true))

@Preview(showBackground = true, name = "Cargando")
@Composable
private fun CategoriesLoadingPreview() = PreviewHost(CategoriesUiState.Loading)

@Preview(showBackground = true, name = "Error")
@Composable
private fun CategoriesErrorPreview() = PreviewHost(CategoriesUiState.Error)
