package com.raycoll.app.ui.study

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.raycoll.app.R
import com.raycoll.app.domain.selectors.computeCardStats
import com.raycoll.app.ui.components.CardStatsBar
import com.raycoll.app.ui.components.RayCollCard
import com.raycoll.app.ui.theme.Spacing

/** Koniec sesji: wynik i co dalej. Przewijany, żeby w poziomie przyciski się nie ucinały. */
@Composable
fun SessionSummary(
    state: StudyUiState.Active,
    onRestartFailed: () -> Unit,
    onRestart: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val session = state.session
    val description = when {
        state.endedEarly && state.reviewedCardCount == 0 -> R.string.study_session_ended_no_ratings
        state.endedEarly -> R.string.study_session_ended_desc
        state.reviewedCardCount == 0 -> R.string.study_finished_without_ratings
        else -> R.string.study_finished_desc
    }
    val stats = remember(state.resultCards) { state.resultCards?.let(::computeCardStats) }
    BoxWithConstraints(modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = maxHeight)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.xl),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            RayCollCard(Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(
                    Modifier.padding(Spacing.xl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.m)
                ) {
                    Text(
                        stringResource(
                            if (state.endedEarly) R.string.study_session_ended_title else R.string.study_bravo
                        ),
                        style = MaterialTheme.typography.headlineLarge
                    )
                    Text(
                        stringResource(description),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    stats?.let { CardStatsBar(it) }
                    Text(
                        stringResource(R.string.study_summary_cards_done, state.completedCardCount, session.cards.size),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (state.reviewedCardCount > 0) {
                        Text(
                            stringResource(
                                R.string.study_summary_ratings,
                                state.reviewedCardCount,
                                session.failedCardIds.size
                            ),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    if (session.failedCardIds.isNotEmpty()) {
                        Button(
                            onClick = onRestartFailed,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.study_restart_failed))
                        }
                    }
                    OutlinedButton(
                        onClick = onRestart,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.study_restart_session))
                    }
                    TextButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.study_back_to_panel))
                    }
                }
            }
        }
    }
}
