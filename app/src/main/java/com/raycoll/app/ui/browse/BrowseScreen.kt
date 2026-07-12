package com.raycoll.app.ui.browse

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.LaunchedEffect
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
import com.raycoll.app.ui.components.SegmentedProgressBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    deckId: String,
    onBack: () -> Unit,
    viewModel: BrowseViewModel = hiltViewModel(),
) {
    val cards by viewModel.cards.collectAsStateWithLifecycle(emptyList())
    val deck by viewModel.deck.collectAsStateWithLifecycle(null)
    val editRequestId by viewModel.editRequestCardId.collectAsStateWithLifecycle(null)
    var search by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<SrsCardCategory?>(null) }
    var editingCard by remember { mutableStateOf<Flashcard?>(null) }
    var deleteTarget by remember { mutableStateOf<Flashcard?>(null) }

    LaunchedEffect(deckId) { viewModel.loadDeck(deckId) }

    // FAB dodał kartę → od razu otwórz edycję
    LaunchedEffect(editRequestId, cards) {
        val id = editRequestId ?: return@LaunchedEffect
        cards.find { it.id == id }?.let {
            editingCard = it
            viewModel.consumeEditRequest()
        }
    }

    val activePageCount = deck?.activePageCount ?: 2
    val filtered = browseFilter(cards, search, category, System.currentTimeMillis(), activePageCount)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(deck?.name ?: stringResource(R.string.route_browse)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) } }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.addCard(deckId) }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.btn_add))
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            // nagłówek: pasek statystyk talii, odświeżany po każdej zmianie
            SegmentedProgressBar(filtered.counts, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            TextField(
                value = search, onValueChange = { search = it },
                placeholder = { Text(stringResource(R.string.browse_search_placeholder)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                singleLine = true,
            )
            // chipy filtrów z licznikami
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                val chips = listOf(
                    null to stringResource(R.string.filter_all) + " (${filtered.counts.total})",
                    SrsCardCategory.NEW to stringResource(R.string.srs_badge_new) + " (${filtered.counts.newCount})",
                    SrsCardCategory.LEARNING to stringResource(R.string.srs_badge_learning) + " (${filtered.counts.learning})",
                    SrsCardCategory.REVIEW to stringResource(R.string.srs_badge_review) + " (${filtered.counts.review})",
                    SrsCardCategory.MASTERED to stringResource(R.string.srs_badge_mastered) + " (${filtered.counts.mastered})",
                )
                chips.forEach { (cat, label) ->
                    FilterChip(
                        selected = category == cat,
                        onClick = { category = cat },
                        label = { Text(label) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }
            if (filtered.cards.isEmpty()) {
                EmptyState(stringResource(R.string.browse_no_cards))
            } else {
                LazyColumn(Modifier.padding(horizontal = 16.dp)) {
                    items(filtered.cards, key = { it.id }) { card ->
                        Card(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                            Column(Modifier.padding(16.dp)) {
                                card.pages.take(activePageCount).forEachIndexed { idx, page ->
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
            activePageCount = activePageCount,
            pageNames = deck?.pageNames ?: emptyList(),
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
    activePageCount: Int,
    pageNames: List<String>,
    onDismiss: () -> Unit,
    onSave: (Flashcard) -> Unit,
) {
    // edytujemy tylko WIDOCZNE strony; ukryte pozostają nietknięte przy zapisie
    val visibleCount = minOf(activePageCount, maxOf(card.pages.size, activePageCount))
    val pages = remember {
        mutableStateListOf(*List(visibleCount) { card.pages.getOrElse(it) { "" } }.toTypedArray())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.browse_edit_card)) },
        text = {
            Column {
                pages.forEachIndexed { idx, page ->
                    OutlinedTextField(
                        value = page,
                        onValueChange = { pages[idx] = it },
                        label = { Text(pageNames.getOrNull(idx) ?: stringResource(R.string.import_page_label, idx + 1)) },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val merged = card.pages.toMutableList()
                    while (merged.size < pages.size) merged.add("")
                    pages.forEachIndexed { idx, value -> merged[idx] = value }
                    onSave(card.copy(pages = merged))
                },
                enabled = pages.count { it.isNotBlank() } >= 2
            ) { Text(stringResource(R.string.btn_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) } },
    )
}
