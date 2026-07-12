package com.raycoll.app.ui.study

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NavigateBefore
import androidx.compose.material.icons.filled.NavigateNext
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raycoll.app.R
import com.raycoll.app.domain.session.SessionStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyScreen(deckId: String, onExit: () -> Unit, viewModel: StudyViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle(StudyUiState())
    var showExitDialog by remember { mutableStateOf(false) }
    val view = LocalView.current

    LaunchedEffect(deckId) { viewModel.loadDeck(deckId) }

    // Keep screen on during active session
    LaunchedEffect(state.status) {
        if (state.status != SessionStatus.FINISHED) {
            view.keepScreenOn = true
        } else {
            view.keepScreenOn = false
        }
    }

    // Exit confirmation with predictive back
    BackHandler(enabled = state.status != SessionStatus.FINISHED && !state.finished) {
        showExitDialog = true
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text(stringResource(R.string.study_exit_confirm_title)) },
            text = { Text(stringResource(R.string.study_exit_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showExitDialog = false
                    viewModel.endSession()
                    onExit()
                }) { Text(stringResource(R.string.study_exit_confirm_btn)) }
            },
            dismissButton = { TextButton(onClick = { showExitDialog = false }) { Text(stringResource(R.string.btn_cancel)) } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${state.deckName}  ${state.currentIndex + 1}/${state.totalCards}") },
                navigationIcon = {
                    IconButton(onClick = { showExitDialog = true }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.btn_back))
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.finished -> StudyFinishedState(state, viewModel, onExit)
                state.currentCard == null -> {
                    Text(stringResource(R.string.study_no_due), modifier = Modifier.align(Alignment.Center))
                }
                else -> StudyCardContent(state, viewModel)
            }
        }
    }
}

@Composable
private fun StudyCardContent(state: StudyUiState, viewModel: StudyViewModel) {
    val card = state.currentCard ?: return
    val scrollState = rememberScrollState()
    val now = System.currentTimeMillis()

    // Detect tap and hold gestures
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { viewModel.handleCardPress() },
                    onLongPress = { viewModel.setHolding(true) },
                    onPress = {
                        val holdStart = now
                        try {
                            awaitRelease()
                        } finally {
                            val holdDuration = System.currentTimeMillis() - holdStart
                            if (holdDuration >= 500) {
                                viewModel.setHolding(false)
                            } else {
                                viewModel.setHolding(false)
                            }
                        }
                    }
                )
            }
    ) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Card pages
            card.pages.forEachIndexed { index, page ->
                if (index < state.revealedPages.size && state.revealedPages.contains(index)) {
                    val fontSize = when {
                        page.length > 260 -> 18.sp
                        page.length > 140 -> 20.sp
                        page.length > 70 -> 24.sp
                        else -> 28.sp
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (state.audioPageIndex == index) {
                            Icon(
                                if (state.status == SessionStatus.LISTENING) Icons.Filled.Mic else Icons.Filled.VolumeUp,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(page, fontSize = fontSize, maxLines = 10, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            // Peek overlay
            state.peekedPage?.let { peekIdx ->
                if (peekIdx !in state.revealedPages && peekIdx < card.pages.size) {
                    Text(card.pages[peekIdx], fontSize = 20.sp, color = MaterialTheme.colorScheme.outline)
                }
            }

            // STT panel
            if (state.status == SessionStatus.LISTENING || state.answerStatus != "none") {
                SttPanel(state)
            }

            Spacer(Modifier.height(16.dp))

            // Rating buttons
            if (state.showRatings) {
                RatingButtons(onRate = { viewModel.rate(it) })
            }

            // Navigation buttons
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                IconButton(onClick = { viewModel.goToPreviousCard() }) {
                    Icon(Icons.Filled.NavigateBefore, contentDescription = stringResource(R.string.study_prev_card))
                }
                IconButton(onClick = { viewModel.skipToNextCard() }) {
                    Icon(Icons.Filled.NavigateNext, contentDescription = stringResource(R.string.study_next_card))
                }
            }
        }
    }
}

@Composable
private fun SttPanel(state: StudyUiState) {
    Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        if (state.partialSttText.isNotEmpty()) {
            Text("${stringResource(R.string.study_recognized)} ${state.partialSttText}",
                style = MaterialTheme.typography.bodyMedium)
        }
        state.matchPercent?.let { percent ->
            val threshold = state.matchThreshold
            val matchText = if (threshold != null) {
                stringResource(R.string.study_match_percent_threshold, percent, threshold)
            } else {
                stringResource(R.string.study_match_percent, percent)
            }
            Text(matchText, style = MaterialTheme.typography.bodyMedium)
        }
        if (state.status == SessionStatus.LISTENING) {
            Icon(Icons.Filled.GraphicEq, contentDescription = null, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun RatingButtons(onRate: (Int) -> Unit) {
    val colors = listOf(
        ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
        ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(1, 2, 3, 4).forEachIndexed { idx, rating ->
            Button(
                onClick = { onRate(rating) },
                colors = colors[idx],
                modifier = Modifier.size(56.dp)
            ) { Text("$rating") }
        }
    }
}

@Composable
private fun StudyFinishedState(state: StudyUiState, viewModel: StudyViewModel, onExit: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(stringResource(R.string.study_bravo), style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.study_finished_desc), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(24.dp))
        if (state.failedCount > 0) {
            Button(onClick = { viewModel.resume() }) { Text(stringResource(R.string.study_restart_failed)) }
            Spacer(Modifier.height(8.dp))
        }
        Button(onClick = { viewModel.resume() }) { Text(stringResource(R.string.study_restart_session)) }
        Spacer(Modifier.height(8.dp))
        Button(onClick = { viewModel.endSession(); onExit() }) { Text(stringResource(R.string.study_back_to_panel)) }
    }
}
