package com.raycoll.app.ui.study

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.speech.tts.TextToSpeech
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raycoll.app.R
import com.raycoll.app.domain.session.SessionError
import com.raycoll.app.ui.components.CollectEvents
import com.raycoll.app.ui.components.ConfirmDialog
import com.raycoll.app.ui.components.ErrorState
import com.raycoll.app.ui.components.LoadingState
import com.raycoll.app.ui.components.ScreenScaffold
import com.raycoll.app.ui.navigation.sharedDeckTitle
import com.raycoll.app.ui.theme.Spacing
import kotlin.coroutines.cancellation.CancellationException

@Composable
fun StudyScreen(onExit: () -> Unit, viewModel: StudyViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showExitDialog by rememberSaveable { mutableStateOf(false) }
    var showMissingVoiceDialog by rememberSaveable { mutableStateOf(false) }
    val active = uiState as? StudyUiState.Active
    val inProgress = active?.inProgress == true

    SessionPermissions(active, onMicrophoneGranted = viewModel::onMicrophoneGranted)

    CollectEvents(viewModel.events) { event ->
        when (event) {
            is StudyEvent.SpeechError -> when (event.error) {
                SessionError.TTS_MISSING_VOICE -> showMissingVoiceDialog = true
                SessionError.TTS -> snackbarHostState.showSnackbar(resources.getString(R.string.study_error_tts))
                SessionError.STT -> snackbarHostState.showSnackbar(resources.getString(R.string.study_error_stt))
            }

            StudyEvent.ReviewSaveFailed -> {
                val result = snackbarHostState.showSnackbar(
                    message = resources.getString(R.string.study_review_save_failed),
                    actionLabel = resources.getString(R.string.errors_retry),
                    duration = SnackbarDuration.Long
                )
                if (result == SnackbarResult.ActionPerformed) viewModel.retryReviewSave()
            }
        }
    }

    // gest "wstecz" w trakcie sesji: karta lekko się zmniejsza i odsuwa, a po puszczeniu pyta o wyjście
    var backProgress by remember { mutableFloatStateOf(0f) }
    var backEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }
    PredictiveBackHandler(enabled = inProgress) { progress ->
        try {
            progress.collect { event ->
                backProgress = event.progress
                backEdge = event.swipeEdge
            }
            showExitDialog = true
        } catch (_: CancellationException) {
            // gest cofnięty
        } finally {
            backProgress = 0f
        }
    }

    ScreenScaffold(
        title = when (val state = uiState) {
            is StudyUiState.Active -> state.deckName
            is StudyUiState.NoDueCards -> state.deckName
            else -> ""
        },
        titleModifier = Modifier.sharedDeckTitle(viewModel.deckId),
        onBack = { if (inProgress) showExitDialog = true else onExit() },
        snackbarHostState = snackbarHostState,
        modifier = Modifier
            .then(if (inProgress) Modifier.keepScreenOn() else Modifier)
            .graphicsLayer {
                val scale = 1f - backProgress * 0.08f
                scaleX = scale
                scaleY = scale
                translationX = backProgress * 48.dp.toPx() * if (backEdge == BackEventCompat.EDGE_RIGHT) -1f else 1f
                clip = backProgress > 0f
                shape = RoundedCornerShape((backProgress * 28).dp)
            }
    ) { padding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .imePadding()
        when (val state = uiState) {
            StudyUiState.Loading -> LoadingState(contentModifier)

            StudyUiState.DeckMissing -> ErrorState(stringResource(R.string.errors_deck_missing), contentModifier)

            is StudyUiState.NoDueCards -> NoDueCards(onExit, contentModifier)

            is StudyUiState.Active -> if (state.finished) {
                SessionSummary(
                    state = state,
                    onRestartFailed = viewModel::restartFailed,
                    onRestart = viewModel::restartSession,
                    onExit = onExit,
                    modifier = contentModifier
                )
            } else {
                Column(contentModifier) {
                    if (state.paused) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = Spacing.screen),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.study_paused), Modifier.weight(1f))
                            TextButton(onClick = viewModel::resume) { Text(stringResource(R.string.study_resume)) }
                        }
                    }
                    SessionProgressBar(
                        state.session,
                        Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.xs)
                    )
                    StudyCardContent(
                        deckId = viewModel.deckId,
                        state = state,
                        actions = remember(viewModel) {
                            CardActions(
                                onTap = viewModel::tapCard,
                                onPressDown = viewModel::pressDown,
                                onPressUp = viewModel::pressUp,
                                onCancelPress = viewModel::cancelPress,
                                onRate = viewModel::rate,
                                onPrevious = viewModel::goToPreviousCard,
                                onNext = viewModel::skipToNextCard
                            )
                        }
                    )
                }
            }
        }
    }

    if (showExitDialog) {
        ConfirmDialog(
            title = stringResource(R.string.study_exit_confirm_title),
            text = stringResource(R.string.study_exit_confirm_message),
            confirmLabel = stringResource(R.string.study_exit_confirm_btn),
            onConfirm = {
                showExitDialog = false
                viewModel.endSessionEarly()
            },
            onDismiss = { showExitDialog = false }
        )
    }
    if (showMissingVoiceDialog) {
        AlertDialog(
            onDismissRequest = { showMissingVoiceDialog = false },
            title = { Text(stringResource(R.string.study_missing_voice_title)) },
            text = { Text(stringResource(R.string.study_missing_voice_desc)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showMissingVoiceDialog = false
                        runCatching { context.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)) }
                    }
                ) { Text(stringResource(R.string.study_missing_voice_btn)) }
            },
            dismissButton = {
                TextButton(onClick = { showMissingVoiceDialog = false }) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }
}

/** Zgody potrzebne sesji: mikrofon dla trybu z nasłuchem, powiadomienia dla nauki w tle. */
@Composable
private fun SessionPermissions(state: StudyUiState.Active?, onMicrophoneGranted: () -> Unit) {
    val context = LocalContext.current
    val microphone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onMicrophoneGranted()
    }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val needsMicrophone = state?.needsMicrophone == true
    val runsInBackground = state?.runsInBackground == true
    LaunchedEffect(needsMicrophone) {
        if (needsMicrophone &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            microphone.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    LaunchedEffect(runsInBackground) {
        val needsNotificationPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (runsInBackground &&
            needsNotificationPermission
        ) {
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
