package com.raycoll.app.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raycoll.app.R
import com.raycoll.app.ui.components.SegmentedProgressBar
import java.time.LocalDate

private const val HEATMAP_DAYS = 140

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(onBack: () -> Unit, viewModel: StatsViewModel = hiltViewModel()) {
    val stats by viewModel.stats.collectAsStateWithLifecycle(StatsUiState())

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.stats_title)) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) } }) }) { padding ->
        LazyColumn(Modifier.padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(stringResource(R.string.stats_heatmap_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                ActivityHeatmap(stats.heatmap)
            }
            item {
                Text(stringResource(R.string.stats_streak) + ": ${stats.streak}", style = MaterialTheme.typography.titleLarge)
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.stats_cards_title) + ": ${stats.totalCards}", style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.srs_badge_new) + ": ${stats.newCount}", style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.srs_badge_learning) + ": ${stats.learningCount}", style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.srs_badge_review) + ": ${stats.reviewCount}", style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.srs_badge_mastered) + ": ${stats.masteredCount}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            items(stats.decks, key = { it.id }) { deck ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(deck.name, style = MaterialTheme.typography.titleMedium)
                        SegmentedProgressBar(deck.stats, Modifier.padding(top = 8.dp))
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

/** Siatka aktywności: HEATMAP_DAYS dni w kolumnach po 7; intensywność wg liczby ocen; dziś wyróżnione. */
@Composable
private fun ActivityHeatmap(heatmap: Map<String, Int>) {
    val today = remember { LocalDate.now() }
    val weeks = HEATMAP_DAYS / 7
    val start = remember(today) { today.minusDays((HEATMAP_DAYS - 1).toLong()) }
    val base = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.surfaceVariant

    Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        for (week in 0 until weeks) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                for (dayOfWeek in 0 until 7) {
                    val date = start.plusDays((week * 7 + dayOfWeek).toLong())
                    if (date.isAfter(today)) continue
                    val count = heatmap[date.toString()] ?: 0
                    val color = when {
                        count <= 0 -> empty
                        count <= 2 -> base.copy(alpha = 0.35f)
                        count <= 5 -> base.copy(alpha = 0.65f)
                        else -> base
                    }
                    val cell = Modifier
                        .size(14.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(color)
                    androidx.compose.foundation.layout.Box(
                        if (date == today) cell.border(1.5.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(3.dp)) else cell
                    )
                }
            }
        }
    }
}
