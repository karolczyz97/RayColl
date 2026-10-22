package com.raycoll.app.ui.study

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.raycoll.app.R
import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.session.SessionState
import com.raycoll.app.domain.srs.getCardCategory
import com.raycoll.app.ui.components.BarSegment
import com.raycoll.app.ui.components.SegmentedBar
import com.raycoll.app.ui.theme.LocalSrsColors
import com.raycoll.app.ui.theme.Spacing
import com.raycoll.app.ui.theme.SrsColors
import com.raycoll.app.ui.theme.accent

// przezroczystość odcinków: bieżąca karta pełna, ukończone przygaszone, nadchodzące ledwo widoczne
private const val DONE_ALPHA = 0.6f
private const val UPCOMING_ALPHA = 0.22f

/** Postęp sesji: licznik i odcinek na kartę w kolorze kategorii; za nami wyraźny, przed nami blady. */
@Composable
fun SessionProgressBar(session: SessionState, modifier: Modifier = Modifier) {
    val srs = LocalSrsColors.current
    val counterStyle = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum")
    val widestCounter = stringResource(R.string.study_progress, session.cards.size, session.cards.size)
    val counterWidth = with(LocalDensity.current) {
        rememberTextMeasurer().measure(widestCounter, counterStyle, maxLines = 1).size.width.toDp()
    }
    val progressDescription =
        stringResource(R.string.study_progress_description, session.currentCardIndex + 1, session.cards.size)
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s)
    ) {
        SessionSegments(session, srs, Modifier.weight(1f))
        Text(
            stringResource(R.string.study_progress, session.currentCardIndex + 1, session.cards.size),
            style = counterStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .width(counterWidth)
                .clearAndSetSemantics { contentDescription = progressDescription }
        )
    }
}

@Composable
private fun SessionSegments(session: SessionState, srs: SrsColors, modifier: Modifier) {
    // odcinki zależą tylko od kart i pozycji — częste zmiany stanu sesji (np. tekst z mikrofonu) ich nie przeliczają
    val segments = remember(session.cards, session.currentCardIndex, srs) {
        session.cards.mapIndexed { index, card ->
            val color = categoryColor(srs, card)
            BarSegment(
                weight = 1f,
                color = when {
                    index == session.currentCardIndex -> color
                    index < session.currentCardIndex -> color.copy(alpha = DONE_ALPHA)
                    else -> color.copy(alpha = UPCOMING_ALPHA)
                }
            )
        }
    }
    SegmentedBar(
        segments = segments,
        gap = if (session.cards.size > 30) 0.dp else Spacing.xs,
        modifier = modifier
    )
}

private fun categoryColor(srs: SrsColors, card: Flashcard) = srs.accent(getCardCategory(card.srsState))
