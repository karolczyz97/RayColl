package com.raycoll.app.ui.study

import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.raycoll.app.R
import com.raycoll.app.domain.session.CardDestination
import com.raycoll.app.domain.session.CardNavigation
import com.raycoll.app.ui.theme.Spacing

/** Reakcje ekranu nauki przekazywane do jednego silnika sesji. */
class CardActions(
    val onTap: () -> Unit,
    val onPressDown: () -> Unit,
    val onPressUp: () -> Unit,
    val onCancelPress: () -> Unit,
    val onRate: (Int) -> Unit,
    val onPrevious: () -> Unit,
    val onNext: () -> Unit,
    val onNavigationStart: (Long, String) -> CardNavigation? = { _, _ -> null },
    val onNavigationContinue: (CardNavigation) -> Boolean = { false },
    val onNavigationEnd: (CardNavigation, CardDestination?) -> Int? = { _, _ -> null }
)

@Composable
fun StudyCardContent(state: StudyUiState.Active, actions: CardActions) {
    if (state.currentCard == null) return
    key(state.session.sessionId) {
        val pager = rememberPagerState(initialPage = state.session.currentCardIndex) { state.session.cards.size + 1 }
        val blocked = pager.isScrollInProgress || state.session.navigation != null ||
            pager.settledPage != state.session.currentCardIndex
        val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
        CompositionLocalProvider(LocalCompactCard provides landscape) {
            Column(
                Modifier
                    .fillMaxSize()
                    .clickable(
                        enabled = !blocked,
                        interactionSource = null,
                        indication = null,
                        onClickLabel = stringResource(R.string.study_a11y_tap_card),
                        role = Role.Button,
                        onClick = actions.onTap
                    )
            ) {
                StudyPager(state, pager, actions, Modifier.weight(1f).fillMaxWidth())
                StudyControls(state, actions, landscape, blocked)
            }
        }
    }
}

/** Oceny i nawigacja są poza pagerem, więc nie przewijają się razem z kartami. */
@Composable
private fun StudyControls(state: StudyUiState.Active, actions: CardActions, landscape: Boolean, blocked: Boolean) {
    val gap = if (landscape) Spacing.s else Spacing.screen
    val modifier = Modifier
        .fillMaxWidth()
        .padding(start = Spacing.screen, end = Spacing.screen, bottom = gap)
    val pages = state.currentCard?.pages?.take(state.activePageCount)?.size ?: 0
    if (landscape) {
        Row(
            modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.l)
        ) {
            PreviousCardButton(actions, !blocked)
            StudyActions(state, pages, actions, !blocked, Modifier.weight(1f))
            NextCardButton(actions, !blocked)
        }
    } else {
        Column(
            modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.m)
        ) {
            StudyActions(state, pages, actions, !blocked, Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.l)) {
                PreviousCardButton(actions, !blocked)
                NextCardButton(actions, !blocked)
            }
        }
    }
}

/** Oceny po odsłonięciu, wcześniej odkrywanie — wyjście rysuje własny, poprzedni stan. */
@Composable
private fun StudyActions(
    state: StudyUiState.Active,
    pageCount: Int,
    actions: CardActions,
    enabled: Boolean,
    modifier: Modifier
) {
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val spatialOffset = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val target = studyActionState(state.session, pageCount)
    AnimatedContent(
        targetState = target,
        contentKey = { it.kind },
        transitionSpec = {
            val direction = if (targetState.kind == StudyActionKind.RATINGS) 1 else -1
            (fadeIn(effects) + slideInVertically(spatialOffset) { direction * it / 3 })
                .togetherWith(fadeOut(effects) + slideOutVertically(spatialOffset) { -direction * it / 3 })
        },
        label = "study_actions",
        contentAlignment = Alignment.Center,
        modifier = modifier.heightIn(min = RatingButtonHeight)
    ) { shown ->
        when (shown.kind) {
            StudyActionKind.RATINGS -> RatingButtons(
                onRate = actions.onRate,
                enabled = enabled && shown.enabled,
                suggestedRating = shown.suggested
            )

            StudyActionKind.REVEAL -> TextButton(
                shapes = ButtonDefaults.shapes(),
                onClick = actions.onTap,
                enabled = enabled
            ) {
                Text(stringResource(R.string.study_tap_to_reveal))
            }

            StudyActionKind.NONE -> Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
private fun PreviousCardButton(actions: CardActions, enabled: Boolean) {
    CardNavButton(painterResource(R.drawable.ic_chevron_left), R.string.study_prev_card, enabled, actions.onPrevious)
}

@Composable
private fun NextCardButton(actions: CardActions, enabled: Boolean) {
    CardNavButton(painterResource(R.drawable.ic_chevron_right), R.string.study_next_card, enabled, actions.onNext)
}

@Composable
private fun CardNavButton(icon: Painter, @StringRes description: Int, enabled: Boolean, onClick: () -> Unit) {
    FilledTonalIconButton(shapes = IconButtonDefaults.shapes(), onClick = onClick, enabled = enabled) {
        Icon(icon, contentDescription = stringResource(description))
    }
}
