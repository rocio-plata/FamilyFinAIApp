package com.rocioplata.familyfinaiapp.ui.common

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rocioplata.familyfinaiapp.domain.header.FamilyRole

/** Insignia con el rol de una persona en una familia: oscura para el Propietario, clara para el Integrante. */
@Composable
internal fun RoleBadge(role: FamilyRole, label: String, modifier: Modifier = Modifier) {
    val owner = role == FamilyRole.OWNER
    Surface(
        modifier = modifier,
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
