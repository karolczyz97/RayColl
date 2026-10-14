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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.raycoll.app.domain.selectors.CardStats
import com.raycoll.app.ui.theme.LocalSrsColors
import com.raycoll.app.ui.theme.Spacing

/** Fragment paska: udział [weight] w kolorze [color]. */
data class BarSegment(val weight: Float, val color: Color)

/** Pasek z odcinków proporcjonalnych do wag (rozkład kart, postęp sesji). */
@Composable
fun SegmentedBar(segments: List<BarSegment>, modifier: Modifier = Modifier, height: Dp = 8.dp, gap: Dp = Spacing.xxs) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        horizontalArrangement = Arrangement.spacedBy(gap)
    ) {
        segments.filter { it.weight > 0f }.forEach { segment ->
            Box(
                Modifier
                    .weight(segment.weight)
                    .fillMaxHeight()
                    .background(segment.color)
            )
        }
    }
}

/** Rozkład kart talii: nowe / w nauce / powtórki / opanowane. */
@Composable
fun CardStatsBar(stats: CardStats, modifier: Modifier = Modifier) {
    val srs = LocalSrsColors.current
    SegmentedBar(
        segments = listOf(
            BarSegment(stats.newCount.toFloat(), srs.newBg),
            BarSegment(stats.learning.toFloat(), srs.learningBg),
            BarSegment(stats.review.toFloat(), srs.reviewBg),
            BarSegment(stats.mastered.toFloat(), srs.masteredBg)
        ),
        modifier = modifier
    )
}
