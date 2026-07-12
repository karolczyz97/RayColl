package com.raycoll.app.ui.study

import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raycoll.app.R
import com.raycoll.app.domain.model.StudyConstants
import com.raycoll.app.domain.selectors.computeCardStats
import com.raycoll.app.domain.selectors.getSessionProgress
import com.raycoll.app.domain.session.SessionEngine
import com.raycoll.app.domain.session.SessionStatus
import com.raycoll.app.domain.srs.SrsCardCategory
import com.raycoll.app.playback.StudySessionService
import com.raycoll.app.ui.components.SegmentedProgressBar
import com.raycoll.app.ui.theme.LocalSrsColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyScreen(deckId: String, onExit: () -> Unit, viewModel: StudyViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle(StudyUiState())
    var showExitDialog by remember { mutableStateOf(false) }
    val view = LocalView.current
    val context = LocalContext.current
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(deckId) { viewModel.loadDeck(deckId) }

    // Mikrofon: tryb z listen_and_check prosi o uprawnienie na starcie sesji
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(state.needsMicPermission) {
        if (state.needsMicPermission) micLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
    }

    // Nauka w tle: przy zejściu w tło uruchom serwis medialny (tryb hands-free + pref ON),
    // w innym wypadku zwykła pauza; powrót na pierwszy plan wyłącza tryb tła
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    val current = viewModel.uiState.value
                    viewModel.onAppBackground()
                    if (current.backgroundEligible && !current.finished) {
                        ContextCompat.startForegroundService(
                            context,
                            Intent(context, StudySessionService::class.java),
                        )
                    }
                }
                Lifecycle.Event.ON_START -> viewModel.onAppForeground()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val sessionActive = state.loaded && !state.finished && state.currentCard != null

    // Keep screen on tylko podczas aktywnej sesji
    LaunchedEffect(sessionActive) { view.keepScreenOn = sessionActive }
    DisposableEffect(Unit) { onDispose { view.keepScreenOn = false } }

    // Błędy TTS/STT: snackbar; brak głosu TTS → dialog z przejściem do ustawień systemowych
    var showMissingVoiceDialog by remember { mutableStateOf(false) }
    val errTts = stringResource(R.string.study_error_tts)
    val errStt = stringResource(R.string.study_error_stt)
    LaunchedEffect(state.errorMsg) {
        when (state.errorMsg) {
            null -> Unit
            SessionEngine.ERROR_TTS_MISSING_VOICE -> { showMissingVoiceDialog = true; viewModel.clearError() }
            SessionEngine.ERROR_STT -> { viewModel.clearError(); snackbarHost.showSnackbar(errStt) }
            else -> { viewModel.clearError(); snackbarHost.showSnackbar(errTts) }
        }
    }

    if (showMissingVoiceDialog) {
        AlertDialog(
            onDismissRequest = { showMissingVoiceDialog = false },
            title = { Text(stringResource(R.string.study_missing_voice_title)) },
            text = { Text(stringResource(R.string.study_missing_voice_desc)) },
            confirmButton = {
                TextButton(onClick = {
                    showMissingVoiceDialog = false
                    runCatching { context.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)) }
                }) { Text(stringResource(R.string.study_missing_voice_btn)) }
            },
            dismissButton = { TextButton(onClick = { showMissingVoiceDialog = false }) { Text(stringResource(R.string.btn_cancel)) } },
        )
    }

    // Back podczas aktywnej sesji → dialog przerwania; potwierdzenie → podsumowanie endedEarly
    BackHandler(enabled = sessionActive) {
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
                    viewModel.endSessionEarly()
                }) { Text(stringResource(R.string.study_exit_confirm_btn)) }
            },
            dismissButton = { TextButton(onClick = { showExitDialog = false }) { Text(stringResource(R.string.btn_cancel)) } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.deckName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (sessionActive) {
                            Text(
                                "${state.currentIndex + 1}/${state.totalCards}",
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { if (sessionActive) showExitDialog = true else onExit() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.btn_back))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (sessionActive) {
                SessionProgressBar(state, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
            Box(Modifier.fillMaxSize()) {
                when {
                    !state.loaded -> Unit
                    state.finished -> StudyFinishedState(state, viewModel, onExit)
                    state.currentCard == null -> NoDueState(onExit)
                    else -> StudyCardContent(state, viewModel)
                }
            }
        }
    }
}

/** Pasek postępu sesji: segment per karta (past/current/future), kolor wg kategorii. */
@Composable
private fun SessionProgressBar(state: StudyUiState, modifier: Modifier = Modifier) {
    val srsColors = LocalSrsColors.current
    val progress = getSessionProgress(state.sessionCards, state.currentIndex)
    Row(
        modifier = modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        progress.forEach { info ->
            val base = when (info.category) {
                SrsCardCategory.NEW -> srsColors.newBg
                SrsCardCategory.LEARNING -> srsColors.learningBg
                SrsCardCategory.REVIEW -> srsColors.reviewBg
                SrsCardCategory.MASTERED -> srsColors.masteredBg
            }
            val color = when {
                info.isCurrent -> base
                info.isPast -> base.copy(alpha = 0.85f)
                else -> base.copy(alpha = 0.3f)
            }
            Segment(1f, color)
        }
    }
}

@Composable
private fun RowScope.Segment(weight: Float, color: Color) {
    Box(Modifier.weight(weight).fillMaxSize().background(color))
}

@Composable
private fun NoDueState(onExit: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.study_no_due), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onExit) { Text(stringResource(R.string.study_back_to_panel)) }
    }
}

@Composable
private fun StudyCardContent(state: StudyUiState, viewModel: StudyViewModel) {
    val card = state.currentCard ?: return
    val scrollState = rememberScrollState()

    // auto-scroll do najnowiej odsłoniętej strony
    LaunchedEffect(state.revealedPages.size, state.currentIndex) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        // silnik sam mierzy czas trzymania: peek >=500 ms, tap <500 ms
                        viewModel.pressDown()
                        try {
                            awaitRelease()
                        } finally {
                            viewModel.pressUp()
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
                if (state.revealedPages.contains(index)) {
                    CardPage(page, isAudioPage = state.audioPageIndex == index, listening = state.status == SessionStatus.LISTENING)
                }
            }

            // Peek — podgląd następnej ukrytej strony podczas trzymania
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
private fun CardPage(page: String, isAudioPage: Boolean, listening: Boolean) {
    // adaptacyjny rozmiar czcionki wg długości tekstu (progi z sekcji 5 planu)
    val fontSize = when {
        page.length > StudyConstants.CARD_FONT_TIER_3 -> 18.sp
        page.length > StudyConstants.CARD_FONT_TIER_2 -> 20.sp
        page.length > StudyConstants.CARD_FONT_TIER_1 -> 24.sp
        else -> 28.sp
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (isAudioPage) {
            Icon(
                if (listening) Icons.Filled.Mic else Icons.Filled.VolumeUp,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
        }
        Text(page, fontSize = fontSize, maxLines = 10, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SttPanel(state: StudyUiState) {
    Column(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
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
        when (state.answerStatus) {
            "correct" -> Text(stringResource(R.string.study_answer_correct), color = LocalSrsColors.current.masteredBg)
            "incorrect" -> Text(stringResource(R.string.study_answer_incorrect), color = MaterialTheme.colorScheme.error)
            "skipped" -> Text(stringResource(R.string.study_answer_skipped), color = MaterialTheme.colorScheme.outline)
            "error" -> Text(stringResource(R.string.study_answer_error), color = MaterialTheme.colorScheme.error)
        }
        if (state.status == SessionStatus.LISTENING) {
            Icon(Icons.Filled.GraphicEq, contentDescription = null, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun RatingButtons(onRate: (Int) -> Unit) {
    val srsColors = LocalSrsColors.current
    // kolory semantyczne ocen 1..4
    val colors = listOf(
        MaterialTheme.colorScheme.error,
        srsColors.learningBg,
        srsColors.reviewBg,
        srsColors.masteredBg,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(1, 2, 3, 4).forEachIndexed { idx, rating ->
            Button(
                onClick = { onRate(rating) },
                colors = ButtonDefaults.buttonColors(containerColor = colors[idx]),
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
        Text(
            stringResource(if (state.endedEarly) R.string.study_session_ended_title else R.string.study_bravo),
            style = MaterialTheme.typography.headlineLarge,
        )
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.study_finished_desc), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
        // pasek kategorii + statystyki sesji
        SegmentedProgressBar(computeCardStats(state.sessionCards))
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.study_finished_stats, state.totalCards, state.failedCount),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(24.dp))
        if (state.failedCount > 0) {
            Button(onClick = { viewModel.restartFailed() }) { Text(stringResource(R.string.study_restart_failed)) }
            Spacer(Modifier.height(8.dp))
        }
        OutlinedButton(onClick = { viewModel.restartSession() }) { Text(stringResource(R.string.study_restart_session)) }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { viewModel.exitSession(); onExit() }) { Text(stringResource(R.string.study_back_to_panel)) }
    }
}
