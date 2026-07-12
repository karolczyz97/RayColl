package com.raycoll.app.ui.archive

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.raycoll.app.R
import com.raycoll.app.domain.selectors.archiveDaysLeft
import com.raycoll.app.ui.component.ConfirmDialog
import com.raycoll.app.ui.component.EmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveScreen(viewModel: ArchiveViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var delTarget by remember { mutableStateOf<String?>(null) }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.archive_title)) }) }) { padding ->
        if (uiState.groups.isEmpty()) EmptyState(stringResource(R.string.archive_empty))
        else LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(uiState.groups, key = { it.id }) { group ->
                val days = archiveDaysLeft(group, System.currentTimeMillis())
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Inventory2, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.outline)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(group.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text(when { days <= 0 -> stringResource(R.string.archive_auto_delete_today); days == 1 -> stringResource(R.string.archive_auto_delete_tomorrow); else -> stringResource(R.string.archive_auto_delete_in, days) }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        FilledTonalButton(onClick = { viewModel.restore(group.id) }) { Text(stringResource(R.string.btn_restore)) }
                        Spacer(Modifier.width(8.dp))
                        IconButton(onClick = { delTarget = group.id }) { Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
    delTarget?.let { id -> ConfirmDialog(stringResource(R.string.dialog_delete_permanent_title), stringResource(R.string.dialog_delete_permanent_desc, ""), stringResource(R.string.btn_delete), { viewModel.delete(id); delTarget = null }, { delTarget = null }) }
}
