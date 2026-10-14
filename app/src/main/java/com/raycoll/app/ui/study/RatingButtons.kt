package com.raycoll.app.ui.study

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SentimentDissatisfied
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.raycoll.app.R
import com.raycoll.app.domain.model.Ratings
import com.raycoll.app.ui.theme.LocalSrsColors
import com.raycoll.app.ui.theme.Spacing

@StringRes
fun ratingLabel(rating: Int): Int = when (rating) {
    Ratings.AGAIN -> R.string.study_rating_1
    Ratings.HARD -> R.string.study_rating_2
    Ratings.GOOD -> R.string.study_rating_3
    else -> R.string.study_rating_4
}

private class RatingStyle(val rating: Int, val icon: ImageVector, val container: Color, val content: Color)

/** Ocena karty: jeszcze raz / trudne / dobrze / łatwe. */
@Composable
fun RatingButtons(onRate: (Int) -> Unit, enabled: Boolean) {
    val srs = LocalSrsColors.current
    val colors = MaterialTheme.colorScheme
    val styles = listOf(
        RatingStyle(Ratings.AGAIN, Icons.Filled.Refresh, colors.errorContainer, colors.onErrorContainer),
        RatingStyle(Ratings.HARD, Icons.Filled.SentimentDissatisfied, srs.learningBg, srs.learningFg),
        RatingStyle(Ratings.GOOD, Icons.Filled.ThumbUp, srs.reviewBg, srs.reviewFg),
        RatingStyle(Ratings.EASY, Icons.Filled.CheckCircle, srs.masteredBg, srs.masteredFg)
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        styles.forEach { style ->
            Button(
                onClick = { onRate(style.rating) },
                enabled = enabled,
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = style.container,
                    contentColor = style.content,
                    disabledContainerColor = style.container.copy(alpha = 0.5f),
                    disabledContentColor = style.content.copy(alpha = 0.5f)
                ),
                contentPadding = PaddingValues(horizontal = Spacing.xs, vertical = Spacing.s),
                modifier = Modifier
                    .weight(1f)
                    .height(68.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    Icon(style.icon, contentDescription = null, modifier = Modifier.size(22.dp))
                    Text(
                        stringResource(ratingLabel(style.rating)),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
