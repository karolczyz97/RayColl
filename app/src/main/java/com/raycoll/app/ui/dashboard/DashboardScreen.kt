package com.raycoll.app.ui.dashboard

import androidx.compose.foundation.background
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
import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.domain.selectors.CardStatsData
import com.raycoll.app.ui.component.EmptyState
import com.raycoll.app.ui.theme.SrsCardCategory
import com.raycoll.app.ui.theme.SrsColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onStudy: (String) -> Unit, onBrowse: (String) -> Unit,
    onSettings: (String) -> Unit, onImport: () -> Unit, onAppSettings: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_dashboard), fontWeight = FontWeight.Bold) },
                actions = { IconButton(onClick = onAppSettings) { Icon(Icons.Default.Settings, stringResource(R.string.app_settings_title)) } }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onImport, icon = { Icon(Icons.Default.Add, null) }, text = { Text(stringResource(R.string.import_title)) })
        }
    ) { padding ->
        if (uiState.groups.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState(stringResource(R.string.dashboard_no_groups))
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(uiState.groups, key = { it.id }) { group ->
                    GroupCard(group, uiState.dueCards[group.id] ?: 0, uiState.stats[group.id] ?: CardStatsData(), onStudy, onBrowse, onSettings)
                }
                item { Spacer(Modifier.height(88.dp)) }
            }
        }
    }
}

@Composable
private fun GroupCard(group: FlashcardGroup, dueCount: Int, stats: CardStatsData, onStudy: (String) -> Unit, onBrowse: (String) -> Unit, onSettings: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text(group.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (dueCount > 0) {
                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.errorContainer) {
                        Text(stringResource(R.string.dashboard_due_count, dueCount), modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            val total = (stats.newCount + stats.learning + stats.review + stats.mastered).coerceAtLeast(1)
            val segments = listOf(
                SrsColors.forCategory(SrsCardCategory.New, false).background to stats.newCount,
                SrsColors.forCategory(SrsCardCategory.Learning, false).background to stats.learning,
                SrsColors.forCategory(SrsCardCategory.Review, false).background to stats.review,
                SrsColors.forCategory(SrsCardCategory.Mastered, false).background to stats.mastered,
            ).filter { it.second > 0 }
            if (segments.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))) {
                    segments.forEach { (clr, cnt) -> Box(Modifier.weight(cnt.toFloat()).fillMaxHeight().background(clr)) }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilledTonalButton(onClick = { onStudy(group.id) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 12.dp)) { Icon(Icons.Default.PlayArrow, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.route_study)) }
                OutlinedButton(onClick = { onBrowse(group.id) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 12.dp)) { Icon(Icons.Default.Search, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.route_browse)) }
                IconButton(onClick = { onSettings(group.id) }, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.Tune, stringResource(R.string.route_deck_settings), Modifier.size(20.dp)) }
            }
        }
    }
}
