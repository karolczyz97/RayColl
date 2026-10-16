package com.raycoll.app.ui.study

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raycoll.app.R
import com.raycoll.app.domain.model.Ratings
import com.raycoll.app.ui.theme.LocalRatingColors
import com.raycoll.app.ui.theme.Spacing

/** Minimalna wysokość przycisków ocen — tyle samo zajmuje miejsce kontrolek, gdy ocen jeszcze nie widać. */
internal val RatingButtonHeight = 68.dp
private val RatingLabelMinSize = 10.sp
private val RatingLabelPadding = Spacing.xs

@StringRes
fun ratingLabel(rating: Int): Int = when (rating) {
    Ratings.AGAIN -> R.string.study_rating_1
    Ratings.HARD -> R.string.study_rating_2
    Ratings.GOOD -> R.string.study_rating_3
    else -> R.string.study_rating_4
}

private class RatingStyle(val rating: Int, val icon: Painter, val container: Color, val content: Color)

/** Ocena karty: jeszcze raz / trudne / dobrze / łatwe. */
@Composable
fun RatingButtons(onRate: (Int) -> Unit, enabled: Boolean, suggestedRating: Int? = null) {
    val colors = LocalRatingColors.current
    val styles = listOf(
        RatingStyle(Ratings.AGAIN, painterResource(R.drawable.ic_refresh), colors.againBg, colors.againFg),
        RatingStyle(Ratings.HARD, painterResource(R.drawable.ic_sentiment_dissatisfied), colors.hardBg, colors.hardFg),
        RatingStyle(Ratings.GOOD, painterResource(R.drawable.ic_thumb_up), colors.goodBg, colors.goodFg),
        RatingStyle(Ratings.EASY, painterResource(R.drawable.ic_check_circle), colors.easyBg, colors.easyFg)
    )
    val labels = styles.map { stringResource(ratingLabel(it.rating)) }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val labelStyle = MaterialTheme.typography.labelLarge
        val measurer = rememberTextMeasurer()
        val fourColumnWidth = (maxWidth - Spacing.s * 3) / 4 - RatingLabelPadding * 2
        val widthPx = with(LocalDensity.current) { fourColumnWidth.roundToPx() }
        // Duża czcionka: dwa rzędy zamiast ściskania czterech etykiet do nieczytelnego tekstu.
        val largeText = LocalDensity.current.fontScale >= 1.3f
        val columns = if (largeText || labels.any {
                measurer.measure(it, labelStyle, maxLines = 1).size.width > widthPx
            }
        ) {
            2
        } else {
            4
        }
        val labelWidth = (maxWidth - Spacing.s * (columns - 1)) / columns - RatingLabelPadding * 2
        val labelSize = commonFitFontSize(labels, labelStyle, labelWidth)
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            styles.chunked(columns).forEachIndexed { row, group ->
                RatingRow(
                    group,
                    labels.drop(row * columns).take(columns),
                    labelStyle.copy(fontSize = labelSize),
                    suggestedRating,
                    enabled,
                    onRate
                )
            }
        }
    }
}

/**
 * Największy rozmiar (od rozmiaru stylu w dół, najmniej [RatingLabelMinSize]), przy którym każda etykieta
 * mieści się w jednej linii — wszystkie przyciski mają ten sam rozmiar tekstu, a słowo nie łamie się w środku.
 */
@Composable
private fun commonFitFontSize(labels: List<String>, style: TextStyle, width: Dp): TextUnit {
    val measurer = rememberTextMeasurer()
    val widthPx = with(LocalDensity.current) { width.roundToPx() }
    return remember(labels, style, widthPx, LocalDensity.current) {
        var size = style.fontSize.value
        while (size > RatingLabelMinSize.value &&
            labels.any { measurer.measure(it, style.copy(fontSize = size.sp), maxLines = 1).size.width > widthPx }
        ) {
            size -= 1f
        }
        size.sp
    }
}

@Composable
private fun RatingRow(
    styles: List<RatingStyle>,
    labels: List<String>,
    labelStyle: TextStyle,
    suggestedRating: Int?,
    enabled: Boolean,
    onRate: (Int) -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val interactions = remember(styles.size) { List(styles.size) { MutableInteractionSource() } }
    // wciśnięty przycisk rośnie, a sąsiedzi ustępują mu miejsca — oryginalna grupa przycisków Material 3 Expressive
    ButtonGroup(
        overflowIndicator = {},
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        modifier = Modifier.fillMaxWidth()
    ) {
        styles.forEachIndexed { index, style ->
            customItem(
                buttonGroupContent = {
                    val suggested = style.rating == suggestedRating
                    val suggestion =
                        stringResource(R.string.study_suggested_rating, stringResource(ratingLabel(style.rating)))
                    Button(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                            onRate(style.rating)
                        },
                        enabled = enabled,
                        border = if (suggested) BorderStroke(2.dp, style.content) else null,
                        shapes = ButtonDefaults.shapes(),
                        interactionSource = interactions[index],
                        colors = ButtonDefaults.buttonColors(
                            containerColor = style.container,
                            contentColor = style.content,
                            disabledContainerColor = style.container.copy(alpha = 0.5f),
                            disabledContentColor = style.content.copy(alpha = 0.5f)
                        ),
                        contentPadding = PaddingValues(horizontal = RatingLabelPadding, vertical = Spacing.s),
                        modifier = Modifier
                            .weight(1f)
                            .animateWidth(interactions[index])
                            .heightIn(min = RatingButtonHeight)
                            .semantics { if (suggested) stateDescription = suggestion }
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                        ) {
                            Icon(style.icon, contentDescription = null, modifier = Modifier.size(22.dp))
                            Text(labels[index], style = labelStyle, textAlign = TextAlign.Center, maxLines = 1)
                        }
                    }
                },
                menuContent = {}
            )
        }
    }
}
