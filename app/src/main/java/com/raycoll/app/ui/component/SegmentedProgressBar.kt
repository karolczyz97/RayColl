package com.raycoll.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun SegmentedProgressBar(
    new: Int, learning: Int, review: Int, mastered: Int,
    newColor: Color, learningColor: Color, reviewColor: Color, masteredColor: Color,
    modifier: Modifier = Modifier,
) {
    val total = (new + learning + review + mastered).coerceAtLeast(1)
    Row(
        modifier = modifier
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
    ) {
        if (new > 0) Box(modifier = Modifier.weight(new.toFloat()).fillMaxHeight().background(newColor))
        if (learning > 0) Box(modifier = Modifier.weight(learning.toFloat()).fillMaxHeight().background(learningColor))
        if (review > 0) Box(modifier = Modifier.weight(review.toFloat()).fillMaxHeight().background(reviewColor))
        if (mastered > 0) Box(modifier = Modifier.weight(mastered.toFloat()).fillMaxHeight().background(masteredColor))
    }
}
