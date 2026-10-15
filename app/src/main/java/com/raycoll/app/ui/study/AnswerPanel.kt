package com.raycoll.app.ui.study

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.raycoll.app.R
import com.raycoll.app.domain.model.Ratings
import com.raycoll.app.domain.session.AnswerStatus
import com.raycoll.app.domain.session.LastAnswerResult
import com.raycoll.app.ui.theme.LocalRatingColors
import com.raycoll.app.ui.theme.Spacing

/**
 * Wynik odpowiedzi głosowej: co usłyszano, dopasowanie i proponowana ocena. Dotknięcie = pominięcie czekania.
 * [listeningLabel]: słowo „Słucham” przy fali — gdy słuchana strona jest zakryta i na karcie nie ma etykiety.
 */
@Composable
fun AnswerPanel(answer: LastAnswerResult, listening: Boolean, onTap: () -> Unit, listeningLabel: Boolean = false) {
    Surface(
        onClick = onTap,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier
            .fillMaxWidth()
            // wynik czytany na głos po zakończeniu; w trakcie nasłuchu tekst zmienia się kilka razy na sekundę (i mikrofon złapałby głos TalkBacka)
            .semantics {
                role = Role.Button
                if (!listening) liveRegion = LiveRegionMode.Polite
            }
    ) {
        Column(
            modifier = Modifier.padding(Spacing.m),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.s)
        ) {
            if (answer.text.isNotEmpty()) {
                Text(
                    stringResource(R.string.study_recognized_text, answer.text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            when (answer.status) {
                AnswerStatus.CORRECT -> CorrectResult(answer)

                AnswerStatus.INCORRECT -> if (listening) RetryHint() else IncorrectResult(answer)

                AnswerStatus.SKIPPED -> Text(
                    stringResource(R.string.study_answer_skipped),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                AnswerStatus.ERROR -> Text(
                    stringResource(R.string.study_answer_error),
                    color = MaterialTheme.colorScheme.error
                )

                AnswerStatus.NONE -> Unit
            }
            if (listening) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    Icon(
                        Icons.Filled.GraphicEq,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    if (listeningLabel) {
                        Text(
                            stringResource(R.string.study_badge_listening),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CorrectResult(answer: LastAnswerResult) {
    val colors = LocalRatingColors.current
    val rating = answer.suggestedRating ?: Ratings.GOOD
    val (container, content) = when (rating) {
        Ratings.AGAIN -> colors.againBg to colors.againFg
        Ratings.HARD -> colors.hardBg to colors.hardFg
        Ratings.EASY -> colors.easyBg to colors.easyFg
        else -> colors.goodBg to colors.goodFg
    }
    ResultChip(
        icon = Icons.Filled.CheckCircle,
        title =
            answer.percent?.let { stringResource(R.string.study_answer_correct_percent, it) }
                ?: stringResource(R.string.study_answer_correct),
        badge = stringResource(R.string.study_suggested_rating, stringResource(ratingLabel(rating))),
        container = container,
        content = content
    )
}

@Composable
private fun IncorrectResult(answer: LastAnswerResult) {
    val colors = LocalRatingColors.current
    val percent = answer.percent
    val threshold = answer.threshold
    ResultChip(
        icon = Icons.Filled.Refresh,
        title = when {
            percent != null && threshold != null -> stringResource(
                R.string.study_answer_incorrect_threshold,
                percent,
                threshold
            )

            percent != null -> stringResource(R.string.study_answer_incorrect_percent, percent)

            else -> stringResource(R.string.study_answer_incorrect)
        },
        badge = stringResource(R.string.study_suggested_rating, stringResource(ratingLabel(Ratings.AGAIN))),
        container = colors.againBg,
        content = colors.againFg
    )
}

/** Błędna odpowiedź, ale tryb daje drugą szansę — mikrofon znów słucha. */
@Composable
private fun RetryHint() {
    val colors = MaterialTheme.colorScheme
    ResultChip(
        Icons.Filled.Refresh,
        stringResource(R.string.study_answer_retry),
        badge = null,
        container = colors.secondaryContainer,
        content = colors.onSecondaryContainer
    )
}

@Composable
private fun ResultChip(icon: ImageVector, title: String, badge: String?, container: Color, content: Color) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = container,
        contentColor = content,
        border = BorderStroke(1.dp, content.copy(alpha = 0.35f))
    ) {
        FlowRow(
            modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s),
            itemVerticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.s)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s)
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(title, style = MaterialTheme.typography.titleSmall)
            }
            if (badge != null) {
                Surface(shape = MaterialTheme.shapes.extraSmall, color = content.copy(alpha = 0.15f)) {
                    Text(
                        badge,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.xxs)
                    )
                }
            }
        }
    }
}
