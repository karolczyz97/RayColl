package com.raycoll.app.ui.stats

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raycoll.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(onBack: () -> Unit, viewModel: StatsViewModel = hiltViewModel()) {
    val stats by viewModel.stats.collectAsStateWithLifecycle(StatsUiState())

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.stats_title)) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) } }) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            Text(stringResource(R.string.stats_streak) + ": ${stats.streak}", style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.stats_cards_title) + ": ${stats.totalCards}", style = MaterialTheme.typography.bodyLarge)
            Text("New: ${stats.newCount}", style = MaterialTheme.typography.bodyMedium)
            Text("Learning: ${stats.learningCount}", style = MaterialTheme.typography.bodyMedium)
            Text("Review: ${stats.reviewCount}", style = MaterialTheme.typography.bodyMedium)
            Text("Mastered: ${stats.masteredCount}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
