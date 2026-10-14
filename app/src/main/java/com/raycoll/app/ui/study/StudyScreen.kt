package com.raycoll.app.ui.study

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.speech.tts.TextToSpeech
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.core.net.toUri
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
import com.raycoll.app.ui.theme.RayCollMotion
import com.raycoll.app.ui.theme.Spacing
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

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

                SessionError.STT -> {
                    // bez zgody na mikrofon sam komunikat nie wystarcza — prowadzi do ustawień aplikacji
                    val noMicrophone = !hasPermission(context, Manifest.permission.RECORD_AUDIO)
                    val result = snackbarHostState.showSnackbar(
                        message = resources.getString(R.string.study_error_stt),
                        actionLabel = if (noMicrophone) resources.getString(R.string.app_settings_title) else null
                    )
                    if (result == SnackbarResult.ActionPerformed) openAppDetails(context)
                }
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

    val backGesture = rememberStudyBackGesture(enabled = inProgress, onCommit = { showExitDialog = true })

    ScreenScaffold(
        title = when (val state = uiState) {
            is StudyUiState.Active -> state.deckName
            is StudyUiState.NoDueCards -> state.deckName
            else -> ""
        },
        titleModifier = Modifier.sharedDeckTitle(viewModel.deckId),
        onBack = { if (inProgress) showExitDialog = true else onExit() },
        snackbarHostState = snackbarHostState,
        // pauza w pasku tytułu, nie w treści — nic na ekranie nie przeskakuje przy pauzie i wznowieniu
        actions = {
            AnimatedVisibility(visible = active?.paused == true, enter = fadeIn(), exit = fadeOut()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.study_paused),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = viewModel::resume) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(Spacing.xs))
                        Text(stringResource(R.string.study_resume))
                    }
                }
            }
        },
        modifier = Modifier
            .then(if (inProgress) Modifier.keepScreenOn() else Modifier)
            .graphicsLayer {
                val progress = backGesture.progress.value
                val scale = 1f - progress * 0.08f
                scaleX = scale
                scaleY = scale
                translationX = progress * 48.dp.toPx() * if (backGesture.edge == BackEventCompat.EDGE_RIGHT) -1f else 1f
                clip = progress > 0f
                shape = RoundedCornerShape((progress * 28).dp)
            }
    ) { padding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(padding)
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
        ConfirmDialog(
            title = stringResource(R.string.study_missing_voice_title),
            text = stringResource(R.string.study_missing_voice_desc),
            confirmLabel = stringResource(R.string.study_missing_voice_btn),
            onConfirm = {
                showMissingVoiceDialog = false
                runCatching { context.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)) }
            },
            onDismiss = { showMissingVoiceDialog = false }
        )
    }
}

/** Postęp gestu „wstecz” (0..1) i krawędź, z której zaczął się gest. */
private class StudyBackGesture {
    val progress = Animatable(0f)
    var edge by mutableIntStateOf(BackEventCompat.EDGE_LEFT)
}

/**
 * Gest „wstecz” w trakcie sesji: karta lekko się zmniejsza i odsuwa, po puszczeniu [onCommit]
 * (pytanie o wyjście), a po cofnięciu gestu wraca płynnie na miejsce.
 */
@Composable
private fun rememberStudyBackGesture(enabled: Boolean, onCommit: () -> Unit): StudyBackGesture {
    val gesture = remember { StudyBackGesture() }
    PredictiveBackHandler(enabled = enabled) { events ->
        try {
            events.collect { event ->
                gesture.progress.snapTo(event.progress)
                gesture.edge = event.swipeEdge
            }
            onCommit()
        } catch (_: CancellationException) {
            // gest cofnięty
        } finally {
            withContext(NonCancellable) { gesture.progress.animateTo(0f, RayCollMotion.spatial()) }
        }
    }
    return gesture
}

private fun hasPermission(context: Context, permission: String) =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun openAppDetails(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())
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
        if (needsMicrophone && !hasPermission(context, Manifest.permission.RECORD_AUDIO)) {
            microphone.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    LaunchedEffect(runsInBackground) {
        val needsNotificationPermission = runsInBackground &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !hasPermission(context, Manifest.permission.POST_NOTIFICATIONS)
        if (needsNotificationPermission) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
