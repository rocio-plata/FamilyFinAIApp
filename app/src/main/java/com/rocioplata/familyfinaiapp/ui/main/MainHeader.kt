package com.rocioplata.familyfinaiapp.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rocioplata.familyfinaiapp.R
import com.rocioplata.familyfinaiapp.domain.header.Family
import com.rocioplata.familyfinaiapp.domain.header.FamilyRole
import com.rocioplata.familyfinaiapp.domain.header.avatarInitial
import com.rocioplata.familyfinaiapp.ui.theme.FamilyFinAIAppTheme

private val MinTouchTarget = 48.dp
private const val MaxBadgeCount = 99

@Composable
fun MainHeader(
    state: MainUiState,
    onFamilyClick: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        FamilyArea(
            families = state.families,
            onFamilyClick = onFamilyClick,
            onRetry = onRetry,
            modifier = Modifier.weight(1f, fill = false),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (state.hasLocalChanges) {
                Box(
                    modifier = Modifier.size(MinTouchTarget),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CloudOff,
                        contentDescription = stringResource(R.string.header_local_changes_description),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            NotificationsIcon(count = state.pendingInvitations ?: 0)
            Spacer(Modifier.width(4.dp))
            Avatar(displayName = state.displayName)
        }
    }
}

@Composable
private fun FamilyArea(
    families: FamiliesState,
    onFamilyClick: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (families) {
        is FamiliesState.Content -> {
            val role = families.activeFamily.role.label()
            val name = families.activeFamily.name
            FamilyPill(
                title = name,
                subtitle = role,
                description = stringResource(R.string.header_family_selector_description, name, role),
                showArrow = families.hasSelector,
                onClick = if (families.hasSelector) onFamilyClick else null,
                modifier = modifier,
            )
        }
        FamiliesState.Loading -> FamilyPill(
            title = stringResource(R.string.header_family_loading),
            description = stringResource(R.string.header_family_loading),
            modifier = modifier,
        )
        FamiliesState.NoData -> FamilyPill(
            title = stringResource(R.string.header_no_data),
            description = stringResource(R.string.header_no_data),
            modifier = modifier,
        )
        FamiliesState.Error -> FamilyPill(
            title = stringResource(R.string.header_family_error),
            subtitle = stringResource(R.string.header_retry),
            description = stringResource(R.string.header_family_error_description),
            onClick = onRetry,
            modifier = modifier,
        )
    }
}

@Composable
private fun FamilyRole.label(): String = when (this) {
    FamilyRole.OWNER -> stringResource(R.string.role_owner)
    FamilyRole.MEMBER -> stringResource(R.string.role_member)
}

@Composable
private fun FamilyPill(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    showArrow: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val clickModifier = if (onClick != null) {
        Modifier.clickable(role = Role.Button, onClick = onClick)
    } else {
        Modifier
    }
    Surface(
        modifier = modifier
            .heightIn(min = MinTouchTarget)
            .then(clickModifier)
            .clearAndSetSemantics { contentDescription = description },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Groups,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(horizontal = 8.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (showArrow) {
                Icon(
                    imageVector = Icons.Outlined.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun NotificationsIcon(count: Int) {
    val description = if (count > 0) {
        pluralStringResource(R.plurals.header_notifications_count_description, count, count)
    } else {
        stringResource(R.string.header_notifications_description)
    }
    Box(
        modifier = Modifier
            .size(MinTouchTarget)
            .clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        BadgedBox(
            badge = {
                if (count > 0) {
                    Badge { Text(if (count > MaxBadgeCount) "$MaxBadgeCount+" else count.toString()) }
                }
            },
        ) {
            Icon(
                imageVector = Icons.Outlined.Notifications,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Avatar(displayName: String?) {
    val initial = avatarInitial(displayName)
    val description = if (initial != null) {
        stringResource(R.string.header_avatar_initial_description, initial)
    } else {
        stringResource(R.string.header_avatar_description)
    }
    Box(
        modifier = Modifier
            .size(MinTouchTarget)
            .padding(4.dp)
            .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
            .clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (initial != null) {
            Text(
                text = initial,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        } else {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

private val previewOwner = Family("1", "Familia Plata", FamilyRole.OWNER)
private val previewMember = Family("2", "Familia de los González Fernández de la Torre", FamilyRole.MEMBER)

private fun contentState(families: List<Family>, active: Family) = FamiliesState.Content(
    families = families,
    activeFamily = active,
    isStale = false,
)

@Preview(name = "Varias familias, 3 invitaciones", showBackground = true)
@Composable
private fun MainHeaderContentPreview() {
    FamilyFinAIAppTheme {
        MainHeader(
            state = MainUiState(
                families = contentState(listOf(previewOwner, previewMember), previewOwner),
                pendingInvitations = 3,
                displayName = "rocío",
            ),
            onFamilyClick = {},
            onRetry = {},
        )
    }
}

@Preview(name = "Una familia, nombre largo, cambios locales", showBackground = true)
@Composable
private fun MainHeaderSingleFamilyPreview() {
    FamilyFinAIAppTheme {
        MainHeader(
            state = MainUiState(
                families = contentState(listOf(previewMember), previewMember),
                hasLocalChanges = true,
            ),
            onFamilyClick = {},
            onRetry = {},
        )
    }
}

@Preview(name = "Cargando", showBackground = true)
@Composable
private fun MainHeaderLoadingPreview() {
    FamilyFinAIAppTheme {
        MainHeader(state = MainUiState(), onFamilyClick = {}, onRetry = {})
    }
}

@Preview(name = "Error", showBackground = true)
@Composable
private fun MainHeaderErrorPreview() {
    FamilyFinAIAppTheme {
        MainHeader(
            state = MainUiState(families = FamiliesState.Error),
            onFamilyClick = {},
            onRetry = {},
        )
    }
}

@Preview(name = "Sin datos", showBackground = true)
@Composable
private fun MainHeaderNoDataPreview() {
    FamilyFinAIAppTheme {
        MainHeader(
            state = MainUiState(families = FamiliesState.NoData),
            onFamilyClick = {},
            onRetry = {},
        )
    }
}
