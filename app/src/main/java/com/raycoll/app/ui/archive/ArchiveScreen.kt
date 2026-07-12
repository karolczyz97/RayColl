package com.raycoll.app.ui.archive

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raycoll.app.R
import com.raycoll.app.domain.selectors.archiveCountdown
import com.raycoll.app.ui.components.EmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveScreen(onBack: () -> Unit, viewModel: ArchiveViewModel = hiltViewModel()) {
    val decks by viewModel.archivedDecks.collectAsStateWithLifecycle(emptyList())

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.archive_title)) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) } }) }) { padding ->
        if (decks.isEmpty()) {
            EmptyState(stringResource(R.string.archive_empty), Modifier.padding(padding))
        } else {
            val now = System.currentTimeMillis()
            // sort: najnowiej zarchiwizowana pierwsza
            val sorted = decks.sortedByDescending { it.archivedAt ?: 0 }
            LazyColumn(Modifier.padding(padding).padding(16.dp)) {
                items(sorted, key = { it.id }) { deck ->
                    Card(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Text(deck.name, style = MaterialTheme.typography.titleMedium)
                            val cardCount = deck.cards.count { it.deletedAt == null }
                            Text(
                                androidx.compose.ui.res.pluralStringResource(R.plurals.cards_count, cardCount, cardCount),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            val daysLeft = archiveCountdown(deck, now)
                            Text(
                                when {
                                    daysLeft <= 0 -> stringResource(R.string.archive_auto_delete_today)
                                    daysLeft == 1 -> stringResource(R.string.archive_auto_delete_tomorrow)
                                    else -> stringResource(R.string.archive_auto_delete_in, daysLeft)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                            TextButton(onClick = { viewModel.restore(deck.id) }) { Text(stringResource(R.string.btn_restore)) }
                        }
                    }
                }
            }
        }
    }
}
