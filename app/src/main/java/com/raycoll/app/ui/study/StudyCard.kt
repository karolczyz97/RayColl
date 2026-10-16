package com.raycoll.app.ui.study

import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.raycoll.app.R
import com.raycoll.app.domain.session.AnswerStatus
import com.raycoll.app.domain.session.SessionStatus
import com.raycoll.app.ui.navigation.sharedDeckCard
import com.raycoll.app.ui.theme.Spacing
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Reakcje ekranu nauki przekazywane do sesji. */
class CardActions(
    val onTap: () -> Unit,
    val onPressDown: () -> Unit,
    val onPressUp: () -> Unit,
    val onCancelPress: () -> Unit,
    val onRate: (Int) -> Unit,
    val onPrevious: () -> Unit,
    val onNext: () -> Unit
)

@Composable
fun StudyCardContent(deckId: String, state: StudyUiState.Active, actions: CardActions) {
    val card = state.currentCard ?: return
    val session = state.session
    val pages = card.pages.take(state.activePageCount)
    val scrollState = rememberScrollState()
    val userScroll = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }
    val scrollInput = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) userScroll.tryEmit(Unit)
                return Offset.Zero
            }
        }
    }
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val spatialSize = MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>()

    // nowo odsłonięta strona jest na dole — przewiń do niej
    LaunchedEffect(session.revealedPages.size, session.currentCardIndex) {
        if (session.revealedPages.size <= 1) {
            scrollState.scrollTo(0)
        } else {
            // strona rośnie w animacji — dół przewijanej treści przesuwa się razem z nią
            withTimeoutOrNull(700L) {
                coroutineScope {
                    val follow = launch {
                        snapshotFlow { scrollState.maxValue }.collectLatest { scrollState.animateScrollTo(it) }
                    }
                    try {
                        userScroll.first()
                    } finally {
                        follow.cancel()
                    }
                }
            }
        }
    }

    // w poziomie ekran jest niski: zwarta karta i nawigacja po bokach akcji
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    CompositionLocalProvider(LocalCompactCard provides landscape) {
        StudyLayout(state, pages, scrollState, scrollInput, landscape, actions) {
            FlashcardView(
                face = CardFace(
                    card = card,
                    index = session.currentCardIndex,
                    pages = pages,
                    revealedPages = session.revealedPages,
                    peekedPage = session.peekedPageIndex,
                    audioPage = session.audioPageIndex,
                    listening = session.status == SessionStatus.LISTENING,
                    answerPage = session.lastAnswerResult.pageIndex,
                    answerStatus = session.lastAnswerResult.status
                ),
                actions = actions,
                modifier = Modifier
                    .sharedDeckCard(deckId)
                    .fillMaxWidth()
            )
            // panel odpowiedzi: w trakcie nasłuchu i po wyniku bieżącej karty, ale nie gdy karta czyta pytanie
            val listening = session.status == SessionStatus.LISTENING
            val readingQuestion = session.status == SessionStatus.SPEAKING && session.audioPageIndex == 0
            val hasAnswer = session.lastAnswerResult.status != AnswerStatus.NONE
            AnimatedVisibility(
                visible = (listening || hasAnswer) && !readingQuestion,
                enter = fadeIn(effects) + expandVertically(spatialSize),
                exit = fadeOut(effects) + shrinkVertically(spatialSize)
            ) {
                // po zmianie karty znikający panel pokazuje jeszcze wynik poprzedniej karty
                val answer = if (hasAnswer || listening) session.lastAnswerResult else session.previousAnswerResult
                AnswerPanel(
                    answer = answer,
                    listening = listening,
                    onTap = actions.onTap,
                    listeningLabel = listening && session.audioPageIndex !in session.revealedPages
                )
            }
        }
    }
}

/** Przewijana karta u góry, oceny i nawigacja przypięte na dole. */
@Composable
private fun StudyLayout(
    state: StudyUiState.Active,
    pages: List<String>,
    scrollState: ScrollState,
    scrollInput: NestedScrollConnection,
    landscape: Boolean,
    actions: CardActions,
    card: @Composable ColumnScope.() -> Unit
) {
    val gap = if (landscape) Spacing.s else Spacing.screen
    Column(
        Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = null,
                indication = null,
                onClickLabel = stringResource(R.string.study_a11y_tap_card),
                role = Role.Button,
                onClick = actions.onTap
            )
    ) {
        // przewija się tylko karta; oceny i nawigacja zostają zawsze widoczne na dole
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .nestedScroll(scrollInput)
                .verticalScroll(scrollState)
                .padding(horizontal = Spacing.screen, vertical = gap),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(gap),
            content = card
        )
        val controlsModifier = Modifier
            .fillMaxWidth()
            .padding(start = Spacing.screen, end = Spacing.screen, bottom = gap)
        if (landscape) {
            Row(
                controlsModifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.l)
            ) {
                PreviousCardButton(actions)
                StudyActions(state, pages.size, actions, Modifier.weight(1f))
                NextCardButton(actions)
            }
        } else {
            Column(
                controlsModifier,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.m)
            ) {
                StudyActions(state, pages.size, actions, Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.l)) {
                    PreviousCardButton(actions)
                    NextCardButton(actions)
                }
            }
        }
    }
}

private enum class StudyActionKind { RATINGS, REVEAL, NONE }

private data class StudyActionState(val kind: StudyActionKind, val enabled: Boolean = false, val suggested: Int? = null)

/** Oceny po odsłonięciu, wcześniej odkrywanie — wyjście rysuje własny, poprzedni stan. */
@Composable
private fun StudyActions(state: StudyUiState.Active, pageCount: Int, actions: CardActions, modifier: Modifier) {
    val session = state.session
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val spatialOffset = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val canReveal = session.status != SessionStatus.LISTENING &&
        session.revealedPages.count { it < pageCount } < pageCount &&
        !session.cardReviewed && session.lastAnswerResult.status == AnswerStatus.NONE
    val target = when {
        session.status == SessionStatus.REVEALED -> StudyActionState(
            StudyActionKind.RATINGS,
            !session.cardReviewed,
            session.lastAnswerResult.suggestedRating
        )

        canReveal -> StudyActionState(StudyActionKind.REVEAL)

        else -> StudyActionState(StudyActionKind.NONE)
    }
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
                enabled = shown.enabled,
                suggestedRating = shown.suggested
            )

            StudyActionKind.REVEAL -> TextButton(shapes = ButtonDefaults.shapes(), onClick = actions.onTap) {
                Text(stringResource(R.string.study_tap_to_reveal))
            }

            StudyActionKind.NONE -> Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
private fun PreviousCardButton(actions: CardActions) {
    CardNavButton(painterResource(R.drawable.ic_chevron_left), R.string.study_prev_card, actions.onPrevious)
}

@Composable
private fun NextCardButton(actions: CardActions) {
    CardNavButton(painterResource(R.drawable.ic_chevron_right), R.string.study_next_card, actions.onNext)
}

@Composable
private fun CardNavButton(icon: Painter, @StringRes description: Int, onClick: () -> Unit) {
    FilledTonalIconButton(shapes = IconButtonDefaults.shapes(), onClick = onClick) {
        Icon(icon, contentDescription = stringResource(description))
    }
}
