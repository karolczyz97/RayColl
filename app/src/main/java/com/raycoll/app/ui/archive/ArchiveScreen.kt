package com.raycoll.app.ui.archive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raycoll.app.R
import com.raycoll.app.ui.components.CollectEvents
import com.raycoll.app.ui.components.LoadingState
import com.raycoll.app.ui.components.ScreenScaffold
import com.raycoll.app.ui.components.animateListItem
import com.raycoll.app.ui.components.rememberSnackbarMessages
import com.raycoll.app.ui.theme.Spacing

@Composable
fun ArchiveScreen(
    onBack: () -> Unit,
    onBrowse: (deckId: String) -> Unit,
    viewModel: ArchiveViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var revealedDeckId by remember { mutableStateOf<String?>(null) }
    var failedWriteKey by remember { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }
    val messages = rememberSnackbarMessages(snackbarHostState)
    val saveFailed = stringResource(R.string.errors_save_failed)
    CollectEvents(viewModel.events) {
        failedWriteKey++
        revealedDeckId = null
        messages.showLatest(saveFailed)
    }

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
                ArchiveEmptyState(contentModifier)
            } else {
                LazyColumn(
                    modifier = contentModifier,
                    contentPadding = Spacing.screenContent(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.item)
                ) {
                    item(key = "retention_info") {
                        Text(
                            stringResource(R.string.archive_restore_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s)
                        )
                    }
                    items(state.decks, key = { deck -> deck.id }) { deck ->
                        ArchivedDeckItem(
                            deck = deck,
                            onRestore = { viewModel.restore(deck.id) },
                            onDelete = { viewModel.delete(deck.id) },
                            onBrowse = { onBrowse(deck.id) },
                            revealed = revealedDeckId == deck.id,
                            onRevealedChange = { open ->
                                if (open) {
                                    revealedDeckId = deck.id
                                } else if (revealedDeckId == deck.id) {
                                    revealedDeckId = null
                                }
                            },
                            failedWriteKey = failedWriteKey,
                            modifier = Modifier.animateListItem(this)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArchiveEmptyState(modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = maxHeight)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.l, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ) {
                Icon(
                    painterResource(R.drawable.ic_archive),
                    contentDescription = null,
                    modifier = Modifier.padding(Spacing.xl).size(Spacing.xxl)
                )
            }
            Text(
                stringResource(R.string.archive_empty),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            Text(
                stringResource(R.string.archive_restore_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
