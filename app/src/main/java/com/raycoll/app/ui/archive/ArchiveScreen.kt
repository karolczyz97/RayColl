package com.raycoll.app.ui.archive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raycoll.app.R
import com.raycoll.app.ui.components.CollectEvents
import com.raycoll.app.ui.components.EmptyState
import com.raycoll.app.ui.components.LoadingState
import com.raycoll.app.ui.components.RayCollCard
import com.raycoll.app.ui.components.ScreenScaffold
import com.raycoll.app.ui.theme.Spacing

@Composable
fun ArchiveScreen(onBack: () -> Unit, viewModel: ArchiveViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val saveFailed = stringResource(R.string.errors_save_failed)
    CollectEvents(viewModel.events) { snackbarHostState.showSnackbar(saveFailed) }

    ScreenScaffold(
        title = stringResource(R.string.archive_title),
        onBack = onBack,
        snackbarHostState = snackbarHostState
    ) { padding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(padding)
        when (val state = uiState) {
            ArchiveUiState.Loading -> LoadingState(contentModifier)

            is ArchiveUiState.Ready -> if (state.decks.isEmpty()) {
                EmptyState(stringResource(R.string.archive_empty), contentModifier)
            } else {
                LazyColumn(
                    modifier = contentModifier,
                    contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Spacing.m)
                ) {
                    items(state.decks, key = {
                        it.id
                    }) { deck -> ArchivedDeckCard(deck, onRestore = { viewModel.restore(deck.id) }) }
                }
            }
        }
    }
}

@Composable
private fun ArchivedDeckCard(deck: ArchivedDeck, onRestore: () -> Unit) {
    RayCollCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(deck.name, style = MaterialTheme.typography.titleLarge)
            Text(
                pluralStringResource(R.plurals.cards_count, deck.cardCount, deck.cardCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                when (deck.daysLeft) {
                    0 -> stringResource(R.string.archive_auto_delete_today)
                    1 -> stringResource(R.string.archive_auto_delete_tomorrow)
                    else -> pluralStringResource(R.plurals.archive_auto_delete_in, deck.daysLeft, deck.daysLeft)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            OutlinedButton(
                onClick = onRestore,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.align(Alignment.End)
            ) {
                Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Text(stringResource(R.string.btn_restore), modifier = Modifier.padding(start = Spacing.s))
            }
        }
    }
}
