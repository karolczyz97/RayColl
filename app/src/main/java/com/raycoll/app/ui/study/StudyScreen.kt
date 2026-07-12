@file:OptIn(ExperimentalMaterial3Api::class)

package com.raycoll.app.ui.study

import android.view.WindowManager
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.raycoll.app.R
import com.raycoll.app.domain.session.SessionStatus
import com.raycoll.app.domain.selectors.getVisiblePages
import com.raycoll.app.ui.component.EmptyState

@Composable
fun StudyScreen(groupId: String, viewModel: StudyViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var showExit by remember { mutableStateOf(false) }
    val context = LocalContext.current
    LaunchedEffect(groupId) { viewModel.load(groupId) }

    LaunchedEffect(uiState.sessionState.status) {
        val act = context as? android.app.Activity
        if (uiState.sessionState.status == SessionStatus.Finished || uiState.sessionState.status == SessionStatus.Idle) act?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else act?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    if (uiState.noDueCards) {
        Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.route_study)) }) }) { EmptyState(stringResource(R.string.study_no_due)) }
        return
    }
    if (uiState.sessionFinished) {
        StudyFinishedState(uiState, viewModel)
        return
    }

    val card = uiState.currentCard; val group = uiState.group
    val pages = if (card != null && group != null) getVisiblePages(card, group) else emptyList()
    val revealed = uiState.sessionState.revealedPages
    val peek = uiState.sessionState.peekedPageIndex
    val isRevealed = uiState.sessionState.status == SessionStatus.Revealed

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.route_study), fontWeight = FontWeight.Medium) },
                navigationIcon = { IconButton(onClick = { showExit = true }) { Icon(Icons.Default.ArrowBack, "Back") } },
                actions = { Text("${uiState.sessionState.currentCardIndex + 1}/${uiState.dueCount}", Modifier.padding(end = 16.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            )
        },
        bottomBar = {
            if (isRevealed) RatingBar { viewModel.rate(it) }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxSize()) {
                var holdStart by remember { mutableStateOf(0L) }
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(20.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .pointerInput(Unit) { detectTapGestures(onPress = { holdStart = System.currentTimeMillis(); viewModel.handleHoldStart(); kotlinx.coroutines.delay(Long.MAX_VALUE) }, onTap = { viewModel.handleCardPress() }) },
                    contentAlignment = Alignment.Center
                ) {
                    Column(Modifier.padding(24.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                        for ((idx, page) in pages.withIndex()) {
                            if (idx == 0 || idx in revealed) {
                                val fontSize = when { page.length > 260 -> 18.sp; page.length > 140 -> 22.sp; page.length > 70 -> 26.sp; else -> 30.sp }
                                AnimatedVisibility(visible = true, enter = fadeIn() + scaleIn()) {
                                    Text(page, style = MaterialTheme.typography.headlineSmall.copy(fontSize = fontSize, fontWeight = FontWeight.Medium), textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 8.dp))
                                }
                            } else if (idx == peek) {
                                Text(page, style = MaterialTheme.typography.headlineSmall.copy(fontSize = 20.sp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f), textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 8.dp))
                            } else if (!isRevealed) {
                                Box(Modifier.height(8.dp).width(40.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)))
                            }
                        }
                    }
                }

                if (!isRevealed) {
                    TextButton(onClick = { viewModel.handleCardPress() }, modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                        Text(stringResource(R.string.study_tap_to_reveal), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
    if (showExit) AlertDialog(onDismissRequest = { showExit = false }, icon = { Icon(Icons.Default.Warning, null) }, title = { Text(stringResource(R.string.study_exit_confirm_title)) }, text = { Text(stringResource(R.string.study_exit_confirm_message)) }, confirmButton = { TextButton(onClick = { showExit = false }) { Text(stringResource(R.string.study_exit_confirm_btn)) } }, dismissButton = { TextButton(onClick = { showExit = false }) { Text(stringResource(R.string.btn_cancel)) } })
}

@Composable
private fun RatingBar(onRate: (Int) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf(1 to R.string.study_rating_1, 2 to R.string.study_rating_2, 3 to R.string.study_rating_3, 4 to R.string.study_rating_4).forEach { (r, lbl) ->
                FilledTonalButton(onClick = { onRate(r) }, Modifier.weight(1f).padding(horizontal = 4.dp),                     colors = ButtonDefaults.filledTonalButtonColors(containerColor = when (r) { 1 -> MaterialTheme.colorScheme.errorContainer; 2 -> MaterialTheme.colorScheme.tertiaryContainer; 3 -> MaterialTheme.colorScheme.secondaryContainer; else -> MaterialTheme.colorScheme.primaryContainer })) { Text(stringResource(lbl), style = MaterialTheme.typography.labelLarge) }
            }
        }
    }
}

@Composable
private fun StudyFinishedState(uiState: StudyUiState, viewModel: StudyViewModel) {
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.study_bravo)) }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Default.EmojiEvents, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.study_finished_desc), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(32.dp))
            FilledTonalButton(onClick = { viewModel.restartSession() }, Modifier.fillMaxWidth()) { Icon(Icons.Default.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.study_restart_session)) }
            if (uiState.sessionState.failedCardIds.isNotEmpty()) { Spacer(Modifier.height(8.dp)); OutlinedButton(onClick = { viewModel.restartFailed() }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.study_restart_failed)) } }
        }
    }
}
