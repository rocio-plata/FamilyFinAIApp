package com.rocioplata.familyfinaiapp.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rocioplata.familyfinaiapp.R
import com.rocioplata.familyfinaiapp.ui.theme.FamilyFinAIAppTheme

/**
 * Lo que muestran las cinco pestañas cuando el usuario no pertenece a ninguna familia (RF-24 de la 009): pasa si lo quitan
 * de la única que tenía. Sin familia no hay nada que registrar ni consultar, así que solo ofrece crear una o ver las
 * invitaciones recibidas.
 */
@Composable
fun NoFamiliesContent(
    onCreate: () -> Unit,
    onInvitations: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp)
            .testTag("no_families_content"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Icon(
            imageVector = Icons.Outlined.Groups,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.no_families_title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.no_families_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = onCreate,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("no_families_create"),
        ) { Text(stringResource(R.string.no_families_create)) }
        OutlinedButton(
            onClick = onInvitations,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("no_families_invitations"),
        ) { Text(stringResource(R.string.no_families_invitations)) }
    }
}

@Preview(showBackground = true)
@Composable
private fun NoFamiliesContentPreview() {
    FamilyFinAIAppTheme { NoFamiliesContent(onCreate = {}, onInvitations = {}) }
}
