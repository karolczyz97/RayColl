package com.raycoll.app.ui.decksettings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.raycoll.app.domain.model.CardFilter
import com.raycoll.app.domain.model.CardOrder
import com.raycoll.app.domain.normalize.MAX_VISIBLE_PAGE_COUNT
import com.raycoll.app.domain.normalize.MIN_PAGE_COUNT

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckSettingsScreen(groupId: String, viewModel: DeckSettingsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var showDel by remember { mutableStateOf(false) }; var showArchive by remember { mutableStateOf(false) }
    LaunchedEffect(groupId) { viewModel.load(groupId) }
    val group = uiState.group ?: return

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title, group.name)) }, navigationIcon = { IconButton(onClick = { }) { Icon(Icons.Default.ArrowBack, "Back") } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { Text(stringResource(R.string.settings_section_general), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary) }
            item { var n by remember(group.id) { mutableStateOf(group.name) }; OutlinedTextField(n, { n = it; viewModel.updateGroup(group.copy(name = it)) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.settings_rename_label)) }, singleLine = true, shape = RoundedCornerShape(12.dp)) }
            item {
                var cnt by remember(group.id) { mutableStateOf(group.activePageCount) }
                Text(stringResource(R.string.settings_pages_config), style = MaterialTheme.typography.bodyMedium)
                Row(verticalAlignment = Alignment.CenterVertically) { IconButton({ if (cnt > MIN_PAGE_COUNT) { cnt--; viewModel.updateGroup(group.copy(activePageCount = cnt)) } }) { Icon(Icons.Default.Remove, "Less") }; Text("$cnt", Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.titleMedium); IconButton({ if (cnt < MAX_VISIBLE_PAGE_COUNT) { cnt++; viewModel.updateGroup(group.copy(activePageCount = cnt)) } }) { Icon(Icons.Default.Add, "More") } }
            }
            item { Text(stringResource(R.string.settings_section_cards), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary) }
            item { Text(stringResource(R.string.settings_study_scope)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { CardFilter.entries.forEach { f -> FilterChip(group.studyFilter == f, { viewModel.updateGroup(group.copy(studyFilter = f)) }, label = { Text(f.name) }) } } }
            item { Text(stringResource(R.string.settings_card_order)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { CardOrder.entries.forEach { o -> FilterChip(group.cardOrder == o, { viewModel.updateGroup(group.copy(cardOrder = o)) }, label = { Text(o.name) }) } } }
            item { Text(stringResource(R.string.settings_section_study), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary) }
            item { uiState.modes.forEach { m -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { RadioButton(group.activeModeId == m.id, { viewModel.setActiveMode(group.id, m.id) }); Spacer(Modifier.width(8.dp)); Text(m.name) } } }
            item { HorizontalDivider() }
            item { OutlinedButton({ showArchive = true }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Icon(Icons.Default.Archive, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.settings_archive_btn)) } }
            item { OutlinedButton({ showDel = true }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Icon(Icons.Default.Delete, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.btn_delete)) } }
        }
    }
    if (showDel) AlertDialog({ showDel = false }, title = { Text(stringResource(R.string.dialog_delete_permanent_title)) }, text = { Text(stringResource(R.string.dialog_delete_permanent_desc, group.name)) }, confirmButton = { TextButton({ viewModel.delete(group.id); showDel = false }) { Text("Delete") } }, dismissButton = { TextButton({ showDel = false }) { Text(stringResource(R.string.btn_cancel)) } })
    if (showArchive) AlertDialog({ showArchive = false }, title = { Text(stringResource(R.string.dialog_archive_title)) }, text = { Text(stringResource(R.string.dialog_archive_confirm, 14)) }, confirmButton = { TextButton({ viewModel.archive(group.id); showArchive = false }) { Text(stringResource(R.string.btn_archive)) } }, dismissButton = { TextButton({ showArchive = false }) { Text(stringResource(R.string.btn_cancel)) } })
}
