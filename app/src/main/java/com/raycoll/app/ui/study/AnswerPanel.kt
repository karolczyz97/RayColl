package com.raycoll.app.ui.study

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import com.raycoll.app.R
import com.raycoll.app.domain.session.AnswerStatus
import com.raycoll.app.domain.session.LastAnswerResult
import com.raycoll.app.ui.theme.LocalRatingColors
import com.raycoll.app.ui.theme.Spacing
import com.raycoll.app.ui.theme.answerColors

/**
 * Wynik odpowiedzi głosowej w jednej linii: co usłyszano, na tle w kolorze wyniku (zielony / czerwony),
 * bez dodatkowych napisów — sugerowaną ocenę pokazuje obwódka przycisku oceny. W trakcie nasłuchu fala;
 * [listeningLabel]: słowo „Słucham” przy fali, gdy słuchana strona jest zakryta (na karcie nic nie świeci).
 * Dotknięcie = pominięcie czekania.
 */
@Composable
fun AnswerPanel(answer: LastAnswerResult, listening: Boolean, onTap: () -> Unit, listeningLabel: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val ratings = LocalRatingColors.current
    val (container, content) = when {
        listening -> colors.surfaceContainerHighest to colors.onSurfaceVariant
        answer.status == AnswerStatus.CORRECT -> ratings.answerColors(correct = true)
        answer.status == AnswerStatus.INCORRECT -> ratings.answerColors(correct = false)
        else -> colors.surfaceContainerHighest to colors.onSurfaceVariant
    }
    val tintSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Color>()
    val animatedContainer by animateColorAsState(container, tintSpec, label = "answer_container")
    val animatedContent by animateColorAsState(content, tintSpec, label = "answer_content")
    val result = when (answer.status) {
        AnswerStatus.CORRECT -> stringResource(R.string.study_answer_correct)
        AnswerStatus.INCORRECT -> stringResource(R.string.study_answer_incorrect)
        else -> null
    }
    Surface(
        onClick = onTap,
        shape = MaterialTheme.shapes.medium,
        color = animatedContainer,
        contentColor = animatedContent,
        modifier = Modifier
            .fillMaxWidth()
            // wynik czytany na głos po zakończeniu; w trakcie nasłuchu tekst zmienia się kilka razy na sekundę (i mikrofon złapałby głos TalkBacka)
            .semantics {
                role = Role.Button
                if (!listening) liveRegion = LiveRegionMode.Polite
                if (!listening && result != null) stateDescription = result
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.CenterHorizontally)
        ) {
            if (listening) {
                Icon(painterResource(R.drawable.ic_graphic_eq), contentDescription = null, tint = colors.primary)
                if (listeningLabel) {
                    Text(
                        stringResource(R.string.study_badge_listening),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.primary
                    )
                }
            }
            when {
                answer.status == AnswerStatus.ERROR -> Text(
                    stringResource(R.string.study_answer_error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.error
                )

                answer.text.isNotEmpty() -> Text(
                    stringResource(R.string.study_recognized_text, answer.text),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
