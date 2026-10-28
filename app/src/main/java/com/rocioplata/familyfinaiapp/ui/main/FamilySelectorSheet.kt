package com.rocioplata.familyfinaiapp.ui.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rocioplata.familyfinaiapp.R
import com.rocioplata.familyfinaiapp.domain.header.Family
import com.rocioplata.familyfinaiapp.domain.header.FamilyRole
import com.rocioplata.familyfinaiapp.ui.theme.FamilyFinAIAppTheme

// Atrás o tocar fuera llaman a onDismiss, que cierra sin cambiar la familia activa.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilySelectorSheet(
    families: List<Family>,
    activeFamilyId: String,
    onSelect: (String) -> Unit,
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
            onSelect = onSelect,
        )
    }
}

@Composable
internal fun FamilySelectorContent(
    families: List<Family>,
    activeFamilyId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.navigationBarsPadding()) {
        Text(
            text = stringResource(R.string.family_selector_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        LazyColumn {
            items(families, key = { it.id }) { family ->
                FamilyRow(
                    family = family,
                    selected = family.id == activeFamilyId,
                    onClick = { onSelect(family.id) },
                )
            }
        }
    }
}

@Composable
private fun FamilyRow(family: Family, selected: Boolean, onClick: () -> Unit) {
    val role = when (family.role) {
        FamilyRole.OWNER -> stringResource(R.string.role_owner)
        FamilyRole.MEMBER -> stringResource(R.string.role_member)
    }
    val description = stringResource(
        if (selected) {
            R.string.family_selector_item_selected_description
        } else {
            R.string.family_selector_item_description
        },
        family.name,
        role,
    )
    ListItem(
        headlineContent = { Text(family.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(role) },
        leadingContent = { Icon(Icons.Outlined.Groups, contentDescription = null) },
        trailingContent = {
            if (selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
        modifier = Modifier
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
                this.selected = selected
            },
    )
}

@Preview(showBackground = true)
@Composable
private fun FamilySelectorContentPreview() {
    FamilyFinAIAppTheme {
        FamilySelectorContent(
            families = listOf(
                Family("1", "Familia Plata", FamilyRole.OWNER),
                Family("2", "Familia de los González Fernández de la Torre", FamilyRole.MEMBER),
            ),
            activeFamilyId = "1",
            onSelect = {},
        )
    }
}
