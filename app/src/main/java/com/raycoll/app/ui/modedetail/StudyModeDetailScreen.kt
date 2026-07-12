package com.raycoll.app.ui.modedetail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.raycoll.app.R
import com.raycoll.app.domain.model.ModeStep

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyModeDetailScreen(modeId: String, onBack: () -> Unit, viewModel: StudyModeDetailViewModel = hiltViewModel()) {
    val mode by viewModel.mode.collectAsState()
    LaunchedEffect(modeId) { viewModel.load(modeId) }
    Scaffold(
        topBar = { TopAppBar(title = { Text(mode?.name ?: "") }) },
        floatingActionButton = { FloatingActionButton(onClick = { }) { Icon(Icons.Default.Add, "Add step") } }
    ) { padding ->
        mode?.let { m ->
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Text(stringResource(R.string.study_modes_steps_count, m.steps.size), style = MaterialTheme.typography.bodyMedium) }
                itemsIndexed(m.steps) { idx, step ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp)) {
                            Text("${idx + 1}. ${stepTypeLabel(step)}", Modifier.weight(1f))
                            TextButton(onClick = { }) { Text(stringResource(R.string.btn_delete)) }
                        }
                    }
                }
                if (m.steps.isEmpty()) item { Text(stringResource(R.string.settings_validation_mode_steps_required)) }
            }
        }
    }
}

private fun stepTypeLabel(step: ModeStep): String = when (step) {
    is com.raycoll.app.domain.model.ShowPage -> "Show page ${step.pageIndex + 1}"
    is com.raycoll.app.domain.model.ShowAllPages -> "Show all pages"
    is com.raycoll.app.domain.model.SpeakPage -> "Speak page ${step.pageIndex + 1}"
    is com.raycoll.app.domain.model.ListenAndCheck -> "Check speech p.${step.pageIndex + 1}"
    is com.raycoll.app.domain.model.ShowRatings -> "Show ratings"
    is com.raycoll.app.domain.model.NextCard -> "Next card"
    is com.raycoll.app.domain.model.CompoundStep -> "Compound: ${step.params.kind.name}"
    else -> step::class.simpleName ?: "?"
}
