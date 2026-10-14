package com.raycoll.app.ui.study

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.raycoll.app.R
import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.selectors.computeCardStats
import com.raycoll.app.domain.session.SessionState
import com.raycoll.app.domain.srs.SrsCardCategory
import com.raycoll.app.domain.srs.getCardCategory
import com.raycoll.app.ui.components.BarSegment
import com.raycoll.app.ui.components.CardStatsBar
import com.raycoll.app.ui.components.EmptyState
import com.raycoll.app.ui.components.RayCollCard
import com.raycoll.app.ui.components.SegmentedBar
import com.raycoll.app.ui.theme.LocalSrsColors
import com.raycoll.app.ui.theme.Spacing
import com.raycoll.app.ui.theme.SrsColors

/** Koniec sesji: wynik i co dalej. */
@Composable
fun SessionSummary(
    state: StudyUiState.Active,
    onRestartFailed: () -> Unit,
    onRestart: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val session = state.session
    Box(modifier.padding(Spacing.xl), contentAlignment = Alignment.Center) {
        RayCollCard(Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(
                Modifier.padding(Spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.m)
            ) {
                Text(
                    stringResource(if (state.endedEarly) R.string.study_session_ended_title else R.string.study_bravo),
                    style = MaterialTheme.typography.headlineLarge
                )
                Text(
                    stringResource(
                        when {
                            state.endedEarly && state.reviewedCardCount == 0 -> R.string.study_session_ended_no_ratings
                            state.endedEarly -> R.string.study_session_ended_desc
                            state.reviewedCardCount == 0 -> R.string.study_finished_without_ratings
                            else -> R.string.study_finished_desc
                        }
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                state.resultCards?.let { CardStatsBar(computeCardStats(it)) }
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

@Composable
fun NoDueCards(onExit: () -> Unit, modifier: Modifier = Modifier) {
    EmptyState(
        message = stringResource(R.string.study_no_due),
        modifier = modifier,
        action = {
            Button(onClick = onExit, shape = MaterialTheme.shapes.small) {
                Text(stringResource(R.string.study_back_to_panel))
            }
        }
    )
}

/** Postęp sesji: licznik i odcinek na kartę w kolorze kategorii; za nami pełny, przed nami blady. */
@Composable
fun SessionProgressBar(session: SessionState, modifier: Modifier = Modifier) {
    val srs = LocalSrsColors.current
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s)
    ) {
        SessionSegments(session, srs, Modifier.weight(1f))
        Text(
            stringResource(R.string.study_progress, session.currentCardIndex + 1, session.cards.size),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SessionSegments(session: SessionState, srs: SrsColors, modifier: Modifier) {
    SegmentedBar(
        segments = session.cards.mapIndexed { index, card ->
            val color = categoryColor(srs, card)
            BarSegment(
                weight = 1f,
                color = when {
                    index == session.currentCardIndex -> color
                    index < session.currentCardIndex -> color.copy(alpha = 0.85f)
                    else -> color.copy(alpha = 0.25f)
                }
            )
        },
        gap = if (session.cards.size > 30) 0.dp else Spacing.xs,
        modifier = modifier
    )
}

private fun categoryColor(srs: SrsColors, card: Flashcard) = when (getCardCategory(card.srsState)) {
    SrsCardCategory.NEW -> srs.newBg
    SrsCardCategory.LEARNING -> srs.learningBg
    SrsCardCategory.REVIEW -> srs.reviewBg
    SrsCardCategory.MASTERED -> srs.masteredBg
}
