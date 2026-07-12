package com.raycoll.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.raycoll.app.domain.selectors.CardStats
import com.raycoll.app.ui.theme.LocalSrsColors

@Composable
fun SegmentedProgressBar(stats: CardStats, modifier: Modifier = Modifier) {
    val srsColors = LocalSrsColors.current
    val total = stats.total.coerceAtLeast(1)
    Row(
        modifier = modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        if (stats.newCount > 0) Segment(stats.newCount.toFloat() / total, srsColors.newBg)
        if (stats.learning > 0) Segment(stats.learning.toFloat() / total, srsColors.learningBg)
        if (stats.review > 0) Segment(stats.review.toFloat() / total, srsColors.reviewBg)
        if (stats.mastered > 0) Segment(stats.mastered.toFloat() / total, srsColors.masteredBg)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Segment(weight: Float, color: Color) {
    Box(modifier = Modifier.weight(weight).background(color))
}
