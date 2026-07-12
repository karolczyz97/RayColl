package com.raycoll.app.ui.browse

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raycoll.app.R
import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.selectors.browseFilter
import com.raycoll.app.domain.srs.SrsCardCategory
import com.raycoll.app.ui.components.EmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    deckId: String,
    onBack: () -> Unit,
    viewModel: BrowseViewModel = hiltViewModel(),
) {
    val cards by viewModel.cards.collectAsStateWithLifecycle(emptyList())
    var search by remember { mutableStateOf("") }
    var editingCard by remember { mutableStateOf<Flashcard?>(null) }
    var deleteTarget by remember { mutableStateOf<Flashcard?>(null) }

    val filtered = browseFilter(cards, search, null, System.currentTimeMillis())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.route_browse)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) } }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.addCard(deckId) }) {
                Icon(Icons.Filled.Add, contentDescription = null)
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            TextField(
                value = search, onValueChange = { search = it },
                placeholder = { Text(stringResource(R.string.browse_search_placeholder)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                singleLine = true,
            )
            if (filtered.cards.isEmpty()) {
                EmptyState(stringResource(R.string.browse_no_cards))
            } else {
                LazyColumn(Modifier.padding(horizontal = 16.dp)) {
                    items(filtered.cards, key = { it.id }) { card ->
                        Card(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                            Column(Modifier.padding(16.dp)) {
                                card.pages.take(2).forEachIndexed { idx, page ->
                                    Text(page, style = if (idx == 0) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium)
                                }
                                Row {
                                    IconButton(onClick = { editingCard = card }) { Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.browse_edit_card)) }
                                    IconButton(onClick = { deleteTarget = card }) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.browse_delete_card)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Edit dialog
    editingCard?.let { card ->
        EditCardDialog(
            card = card,
            onDismiss = { editingCard = null },
            onSave = { updated -> viewModel.updateCard(deckId, updated); editingCard = null },
        )
    }

    // Delete confirmation
    deleteTarget?.let { card ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.dialog_delete_permanent_title)) },
            text = { Text(stringResource(R.string.dialog_delete_desc)) },
            confirmButton = { TextButton(onClick = { viewModel.deleteCard(deckId, card.id); deleteTarget = null }) { Text(stringResource(R.string.btn_delete)) } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.btn_cancel)) } },
        )
    }
}

@Composable
private fun EditCardDialog(
    card: Flashcard,
    onDismiss: () -> Unit,
    onSave: (Flashcard) -> Unit,
) {
    val pages = remember { mutableStateListOf(*card.pages.toTypedArray()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.browse_edit_card)) },
        text = {
            Column {
                pages.forEachIndexed { idx, page ->
                    OutlinedTextField(
                        value = page,
                        onValueChange = { pages[idx] = it },
                        label = { Text(stringResource(R.string.import_page_label, idx + 1)) },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(card.copy(pages = pages.toList())) },
                enabled = pages.count { it.isNotBlank() } >= 2
            ) { Text(stringResource(R.string.btn_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) } },
    )
}
