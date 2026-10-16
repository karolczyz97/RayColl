package com.raycoll.app.ui.study

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.speech.tts.TextToSpeech
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raycoll.app.R
import com.raycoll.app.domain.session.SessionError
import com.raycoll.app.ui.components.CollectEvents
import com.raycoll.app.ui.components.ConfirmDialog
import com.raycoll.app.ui.components.ErrorState
import com.raycoll.app.ui.components.LoadingState
import com.raycoll.app.ui.components.ScreenScaffold
import com.raycoll.app.ui.components.rememberSnackbarMessages
import com.raycoll.app.ui.navigation.sharedDeckTitle
import com.raycoll.app.ui.theme.Spacing

@Composable
fun StudyScreen(onExit: () -> Unit, viewModel: StudyViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbarHostState = remember { SnackbarHostState() }
    val messages = rememberSnackbarMessages(snackbarHostState)
    var showExitDialog by rememberSaveable { mutableStateOf(false) }
    var showMissingVoiceDialog by rememberSaveable { mutableStateOf(false) }
    var missingVoiceShown by rememberSaveable { mutableStateOf(false) }
    var voiceRepairRequested by rememberSaveable { mutableStateOf(false) }
    val active = uiState as? StudyUiState.Active
    val inProgress = active?.inProgress == true

    val onMicrophoneGranted = {
        messages.cancelAction("microphone")
        viewModel.onMicrophoneGranted()
    }
    SessionPermissions(active, onMicrophoneGranted = onMicrophoneGranted)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (hasPermission(context, Manifest.permission.RECORD_AUDIO)) onMicrophoneGranted()
        if (voiceRepairRequested) {
            missingVoiceShown = false
            voiceRepairRequested = false
        }
    }
    LaunchedEffect(inProgress) { if (!inProgress) showExitDialog = false }

    CollectEvents(viewModel.events) { event ->
        when (event) {
            is StudyEvent.SpeechError -> when (event.error) {
                SessionError.TTS_MISSING_VOICE -> if (!missingVoiceShown) {
                    missingVoiceShown = true
                    showMissingVoiceDialog = true
                }

                SessionError.TTS -> messages.showLatest(resources.getString(R.string.study_error_tts))

                SessionError.STT -> {
                    // bez zgody na mikrofon sam komunikat nie wystarcza — prowadzi do ustawień aplikacji
                    val noMicrophone = !hasPermission(context, Manifest.permission.RECORD_AUDIO)
                    val message = resources.getString(R.string.study_error_stt)
                    if (noMicrophone) {
                        val result = messages.showAction(
                            message,
                            resources.getString(R.string.app_settings_title),
                            key = "microphone"
                        )
                        if (result == SnackbarResult.ActionPerformed) openAppDetails(context)
                    } else {
                        messages.showLatest(message)
                    }
                }
            }

            StudyEvent.AudioFocusDenied -> messages.showLatest(resources.getString(R.string.study_audio_focus_denied))
        }
    }

    // w trakcie sesji „wstecz” pyta o wyjście zamiast zamykać ekran
    BackHandler(enabled = inProgress) { showExitDialog = true }

    ScreenScaffold(
        title = when (val state = uiState) {
            is StudyUiState.Active -> state.deckName
            is StudyUiState.NoDueCards -> state.deckName
            else -> ""
        },
        titleModifier = Modifier.sharedDeckTitle(viewModel.deckId),
        onBack = { if (inProgress) showExitDialog = true else onExit() },
        snackbarHostState = snackbarHostState,
        // nauka potrzebuje całej wysokości ekranu — zwykły, niski pasek tytułu
        collapsingTitle = false,
        // pauza w pasku tytułu, nie w treści — nic na ekranie nie przeskakuje przy pauzie i wznowieniu
        actions = {
            AnimatedVisibility(
                visible = active?.paused == true,
                enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec())
            ) {
                val pausedLabel = stringResource(R.string.study_paused)
                TextButton(
                    shapes = ButtonDefaults.shapes(),
                    onClick = viewModel::resume,
                    modifier = Modifier.semantics {
                        stateDescription = pausedLabel
                    }
                ) {
                    Icon(
                        painterResource(R.drawable.ic_play_arrow_filled),
                        contentDescription = null,
                        Modifier.size(ButtonDefaults.IconSize)
                    )
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.study_resume))
                }
            }
        },
        modifier = Modifier.then(if (inProgress) Modifier.keepScreenOn() else Modifier)
    ) { padding ->
        val fade = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
        Column(Modifier.fillMaxSize().padding(padding)) {
            AnimatedContent(
                targetState = uiState,
                contentKey = { it.screenPhase() },
                transitionSpec = { fadeIn(fade).togetherWith(fadeOut(fade)) },
                label = "study_screen",
                modifier = Modifier.fillMaxSize().weight(1f)
            ) { state ->
                val contentModifier = Modifier.fillMaxSize()
                when (state) {
                    StudyUiState.Loading -> LoadingState(contentModifier)

                    StudyUiState.DeckMissing -> ErrorState(
                        stringResource(R.string.errors_deck_missing),
                        contentModifier
                    )

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
                voiceRepairRequested = true
                runCatching { context.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)) }
            },
            onDismiss = { showMissingVoiceDialog = false }
        )
    }
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
internal fun SessionPermissions(state: StudyUiState.Active?, onMicrophoneGranted: () -> Unit) {
    val context = LocalContext.current
    val microphone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onMicrophoneGranted()
    }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val needsMicrophone = state?.needsMicrophone == true
    val runsInBackground = state?.runsInBackground == true
    var microphoneAttempted by rememberSaveable { mutableStateOf(false) }
    var notificationsAttempted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(needsMicrophone) {
        if (needsMicrophone && !microphoneAttempted && !hasPermission(context, Manifest.permission.RECORD_AUDIO)) {
            microphoneAttempted = true
            microphone.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    LaunchedEffect(runsInBackground) {
        val needsNotificationPermission = runsInBackground &&
            !hasPermission(context, Manifest.permission.POST_NOTIFICATIONS)
        if (needsNotificationPermission && !notificationsAttempted) {
            notificationsAttempted = true
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

internal enum class StudyScreenPhase { LOADING, EMPTY, ACTIVE, SUMMARY }

internal fun StudyUiState.screenPhase(): StudyScreenPhase = when (this) {
    StudyUiState.Loading -> StudyScreenPhase.LOADING
    StudyUiState.DeckMissing, is StudyUiState.NoDueCards -> StudyScreenPhase.EMPTY
    is StudyUiState.Active -> if (finished) StudyScreenPhase.SUMMARY else StudyScreenPhase.ACTIVE
}
