package com.raycoll.app.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.raycoll.app.R
import com.raycoll.app.ui.theme.SrsCardCategory
import com.raycoll.app.ui.theme.SrsColors
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(viewModel: StatsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.stats_title)) }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(stringResource(R.string.stats_streak), "${uiState.streak}", Modifier.weight(1f))
                    StatCard(stringResource(R.string.stats_active_days), "${uiState.activeDays}", Modifier.weight(1f))
                    StatCard(stringResource(R.string.stats_cards_title), "${uiState.totalCards}", Modifier.weight(1f))
                }
            }
            item { Text(stringResource(R.string.stats_heatmap_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium) }
            item { HeatmapGrid(uiState.heatmap) }
            item { Text(stringResource(R.string.stats_overall_progress), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium) }
            val stats = uiState.globalStats
            val total = (stats.newCount + stats.learning + stats.review + stats.mastered).coerceAtLeast(1)
            item {
                val segs = listOf(SrsColors.forCategory(SrsCardCategory.New, false).background to stats.newCount, SrsColors.forCategory(SrsCardCategory.Learning, false).background to stats.learning, SrsColors.forCategory(SrsCardCategory.Review, false).background to stats.review, SrsColors.forCategory(SrsCardCategory.Mastered, false).background to stats.mastered).filter { it.second > 0 }
                if (segs.isNotEmpty()) Row(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))) { segs.forEach { (c, n) -> Box(Modifier.weight(n.toFloat()).fillMaxHeight().background(c)) } }
            }
            item { Text(stringResource(R.string.stats_deck_progress), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium) }
            items(uiState.deckProgress) { (name, progress) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(name, Modifier.weight(1f)); Text("$progress%", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier) {
    Card(modifier, shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HeatmapGrid(heatmap: Map<String, Int>) {
    val today = LocalDate.now(); val days = mutableListOf<Pair<String, Int>>()
    for (w in 19 downTo 0) for (d in 6 downTo 0) { val date = today.minusDays((w * 7L + d)); days.add(date.toString() to (heatmap[date.toString()] ?: 0)) }
    val prim = MaterialTheme.colorScheme.primary
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        for (i in days.indices) {
            if (i % 7 == 0 && i > 0) Spacer(Modifier.width(3.dp))
            Box(Modifier.size(13.dp).clip(RoundedCornerShape(2.dp)).background(when { days[i].second <= 0 -> MaterialTheme.colorScheme.surfaceVariant; days[i].second <= 2 -> prim.copy(alpha = 0.2f); days[i].second <= 5 -> prim.copy(alpha = 0.45f); days[i].second <= 10 -> prim.copy(alpha = 0.7f); else -> prim }))
        }
    }
}
