package com.raycoll.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.raycoll.app.R
import com.raycoll.app.domain.selectors.CardStats
import com.raycoll.app.ui.theme.LocalSrsColors
import com.raycoll.app.ui.theme.Spacing
import com.raycoll.app.ui.theme.withMinimumContrast

/** Fragment paska: udział [weight] w kolorze [color]. */
data class BarSegment(val weight: Float, val color: Color)

/** Pasek z odcinków proporcjonalnych do wag (rozkład kart, postęp sesji); [description] czyta TalkBack. */
@Composable
fun SegmentedBar(
    segments: List<BarSegment>,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    gap: Dp = Spacing.xxs,
    description: String? = null
) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(track)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
        horizontalArrangement = Arrangement.spacedBy(gap)
    ) {
        segments.filter { it.weight > 0f }.forEach { segment ->
            Box(
                Modifier
                    .weight(segment.weight)
                    .fillMaxHeight()
                    .background(segment.color.withMinimumContrast(track))
            )
        }
    }
}

/** Rozkład kart talii: nowe / w nauce / powtórki / opanowane (mocne kolory kategorii — wyraźne na tle toru). */
@Composable
fun CardStatsBar(stats: CardStats, modifier: Modifier = Modifier) {
    val srs = LocalSrsColors.current
    val description = listOf(
        R.string.srs_badge_new to stats.newCount,
        R.string.srs_badge_learning to stats.learning,
        R.string.srs_badge_review to stats.review,
        R.string.srs_badge_mastered to stats.mastered
    ).map { (label, count) ->
        stringResource(R.string.filter_chip_count, stringResource(label), count)
    }.joinToString(", ")
    SegmentedBar(
        segments = listOf(
            BarSegment(stats.newCount.toFloat(), srs.newAccent),
            BarSegment(stats.learning.toFloat(), srs.learningAccent),
            BarSegment(stats.review.toFloat(), srs.reviewAccent),
            BarSegment(stats.mastered.toFloat(), srs.masteredAccent)
        ),
        modifier = modifier,
        description = description
    )
}
