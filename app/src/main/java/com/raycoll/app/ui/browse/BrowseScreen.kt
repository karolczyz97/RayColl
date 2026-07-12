package com.raycoll.app.ui.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.raycoll.app.R
import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.normalize.MIN_PAGE_COUNT
import com.raycoll.app.domain.srs.createNewSrsState
import com.raycoll.app.ui.component.ConfirmDialog
import com.raycoll.app.ui.component.EmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(groupId: String, viewModel: BrowseViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var showEdit by remember { mutableStateOf(false) }; var editingCard by remember { mutableStateOf<Flashcard?>(null) }
    var showDelete by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(groupId) { viewModel.load(groupId) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(uiState.group?.name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = { }) { Icon(Icons.Default.ArrowBack, "Back") } })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editingCard = Flashcard(id = java.util.UUID.randomUUID().toString(), pages = List(MIN_PAGE_COUNT) { "" }, srsState = createNewSrsState(), contentUpdatedAt = System.currentTimeMillis())
                showEdit = true
            }) { Icon(Icons.Default.Add, "Add") }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(uiState.searchQuery, { viewModel.search(it) }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), placeholder = { Text(stringResource(R.string.browse_search_placeholder)) }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true, shape = RoundedCornerShape(12.dp))
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("all" to uiState.allCards.size, "new" to uiState.newCount, "learning" to uiState.learningCount, "review" to uiState.reviewCount, "mastered" to uiState.masteredCount).forEach { (cat, cnt) ->
                    FilterChip(selected = false, onClick = { viewModel.search(uiState.searchQuery) }, label = { Text("$cat ($cnt)") })
                }
            }
            if (uiState.allCards.isEmpty()) EmptyState(stringResource(R.string.browse_no_cards))
            else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(uiState.allCards, key = { it.id }) { card ->
                    Card(Modifier.fillMaxWidth().clickable { editingCard = card; showEdit = true }, shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(card.pages.firstOrNull() ?: "", Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                            IconButton(onClick = { showDelete = card.id }) { Icon(Icons.Default.Delete, "Delete", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    if (showEdit && editingCard != null) {
        val card = editingCard!!
        val pages = remember(card.id) { card.pages.toMutableList().also { while (it.size < (uiState.group?.activePageCount ?: 2)) it.add("") } }
        AlertDialog(onDismissRequest = { showEdit = false },
            title = { Text(stringResource(R.string.browse_edit_card)) },
            text = {
                Column {
                    for (i in 0 until (uiState.group?.activePageCount ?: 2)) {
                        val label = uiState.group?.pageNames?.getOrElse(i) { "Page ${i + 1}" } ?: "Page ${i + 1}"
                        OutlinedTextField(pages.getOrElse(i) { "" }, { if (it.length < 500) pages[i.coerceIn(0, pages.lastIndex)] = it }, label = { Text(label) }, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), singleLine = true)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.saveCard(card.copy(pages = pages.take(uiState.group?.activePageCount ?: 2).toList(), contentUpdatedAt = System.currentTimeMillis())); showEdit = false }, enabled = pages.take(uiState.group?.activePageCount ?: 2).count { it.trim().isNotEmpty() } >= MIN_PAGE_COUNT) { Text(stringResource(R.string.btn_save)) } },
            dismissButton = { TextButton(onClick = { showEdit = false }) { Text(stringResource(R.string.btn_cancel)) } })
    }
    showDelete?.let { id -> ConfirmDialog(stringResource(R.string.dialog_delete_permanent_title), stringResource(R.string.dialog_delete_desc), stringResource(R.string.btn_delete), { viewModel.deleteCard(id); showDelete = null }, { showDelete = null }) }
}
