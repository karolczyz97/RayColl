package com.raycoll.app.ui.study

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raycoll.app.R
import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.session.AnswerStatus
import com.raycoll.app.domain.session.SessionStatus
import com.raycoll.app.ui.components.RayCollCard
import com.raycoll.app.ui.navigation.sharedDeckCard
import com.raycoll.app.ui.theme.RayCollMotion
import com.raycoll.app.ui.theme.Spacing

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

/** To, co widać na karcie w danej chwili — animacja zmiany karty pokazuje stary i nowy stan osobno. */
private data class CardFace(
    val card: Flashcard,
    /** Aktywne strony karty (ukryte strony talii nie są pokazywane ani odkrywane). */
    val pages: List<String>,
    val revealedPages: List<Int>,
    val peekedPage: Int?,
    val audioPage: Int?,
    val listening: Boolean
)

// próg długości tekstu (znaki) → mniejsza czcionka, żeby długie strony mieściły się na karcie
private const val LONG_TEXT = 70
private const val LONGER_TEXT = 140
private const val LONGEST_TEXT = 260

// w poziomie wysokość ekranu to ok. połowa pionowej — mniejsza czcionka i odstępy, żeby karta się mieściła
private const val COMPACT_FONT_SCALE = 0.72f

/** Zwarty układ karty (ekran w poziomie). */
private val LocalCompactCard = staticCompositionLocalOf { false }

@Composable
fun StudyCardContent(deckId: String, state: StudyUiState.Active, actions: CardActions) {
    val card = state.currentCard ?: return
    val session = state.session
    val pages = card.pages.take(state.activePageCount)
    val scrollState = rememberScrollState()
    val effects = remember { RayCollMotion.effects<Float>() }
    val spatialSize = remember { RayCollMotion.spatial<IntSize>() }

    // nowo odsłonięta strona jest na dole — przewiń do niej
    LaunchedEffect(session.revealedPages.size, session.currentCardIndex) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    // w poziomie ekran jest niski: zwarta karta i nawigacja po bokach akcji
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    CompositionLocalProvider(LocalCompactCard provides landscape) {
        StudyLayout(state, pages, scrollState, landscape, actions) {
            FlashcardView(
                face = CardFace(
                    card = card,
                    pages = pages,
                    revealedPages = session.revealedPages,
                    peekedPage = session.peekedPageIndex,
                    audioPage = session.audioPageIndex,
                    listening = session.status == SessionStatus.LISTENING
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
                AnswerPanel(answer = answer, listening = listening, onTap = actions.onTap)
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
    landscape: Boolean,
    actions: CardActions,
    card: @Composable ColumnScope.() -> Unit
) {
    val gap = if (landscape) Spacing.s else Spacing.screen
    Column(
        Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember {
                    MutableInteractionSource()
                },
                indication = null,
                onClick = actions.onTap
            )
    ) {
        // przewija się tylko karta; oceny i nawigacja zostają zawsze widoczne na dole
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
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

/** Oceny po odsłonięciu, wcześniej „dotknij, aby odkryć” (gdy jest co odkrywać). */
@Composable
private fun StudyActions(state: StudyUiState.Active, pageCount: Int, actions: CardActions, modifier: Modifier) {
    val session = state.session
    val effects = remember { RayCollMotion.effects<Float>() }
    val spatialOffset = remember { RayCollMotion.spatial<IntOffset>() }
    val hasAnswer = session.lastAnswerResult.status != AnswerStatus.NONE
    AnimatedContent(
        targetState = session.status == SessionStatus.REVEALED,
        transitionSpec = {
            val direction = if (targetState) 1 else -1
            (fadeIn(effects) + slideInVertically(spatialOffset) { direction * it / 3 })
                .togetherWith(fadeOut(effects) + slideOutVertically(spatialOffset) { -direction * it / 3 })
        },
        label = "study_actions",
        contentAlignment = Alignment.Center,
        modifier = modifier
    ) { showRatings ->
        val canReveal = session.status != SessionStatus.LISTENING &&
            session.revealedPages.count { it < pageCount } < pageCount &&
            !session.cardReviewed &&
            !hasAnswer
        when {
            showRatings -> RatingButtons(onRate = actions.onRate, enabled = !session.cardReviewed)

            canReveal -> TextButton(onClick = actions.onTap) {
                Text(stringResource(R.string.study_tap_to_reveal))
            }

            else -> Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
private fun PreviousCardButton(actions: CardActions) {
    FilledTonalIconButton(onClick = actions.onPrevious) {
        Icon(
            Icons.AutoMirrored.Filled.NavigateBefore,
            contentDescription = stringResource(R.string.study_prev_card)
        )
    }
}

@Composable
private fun NextCardButton(actions: CardActions) {
    FilledTonalIconButton(onClick = actions.onNext) {
        Icon(
            Icons.AutoMirrored.Filled.NavigateNext,
            contentDescription = stringResource(R.string.study_next_card)
        )
    }
}

@Composable
private fun FlashcardView(face: CardFace, actions: CardActions, modifier: Modifier) {
    val effects = remember { RayCollMotion.effects<Float>() }
    val spatialOffset = remember { RayCollMotion.spatial<IntOffset>() }
    RayCollCard(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.pointerInput(actions) {
            // przytrzymanie = podgląd następnej strony; krótkie dotknięcie = zwykłe tapnięcie
            detectTapGestures(
                onPress = {
                    actions.onPressDown()
                    if (tryAwaitRelease()) actions.onPressUp() else actions.onCancelPress()
                }
            )
        }
    ) {
        AnimatedContent(
            targetState = face,
            contentKey = { it.card.id },
            transitionSpec = {
                (fadeIn(effects) + slideInHorizontally(spatialOffset) { it / 4 })
                    .togetherWith(fadeOut(effects) + slideOutHorizontally(spatialOffset) { -it / 4 })
            },
            label = "card_change",
            modifier = Modifier.fillMaxWidth()
        ) { shown -> CardPages(shown) }
    }
}

@Composable
private fun CardPages(face: CardFace) {
    val effects = remember { RayCollMotion.effects<Float>() }
    val spatialSize = remember { RayCollMotion.spatial<IntSize>() }
    val gap = if (LocalCompactCard.current) Spacing.s else Spacing.l
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(gap),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(gap)
    ) {
        face.pages.forEachIndexed { index, page ->
            AnimatedVisibility(
                visible = index in face.revealedPages,
                enter = fadeIn(effects) + expandVertically(spatialSize, expandFrom = Alignment.Top),
                exit = fadeOut(effects) + shrinkVertically(spatialSize, shrinkTowards = Alignment.Top)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (index >
                        0
                    ) {
                        HorizontalDivider(
                            Modifier.padding(vertical = Spacing.xs),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                    CardPage(
                        page,
                        isFirst = index == 0,
                        audioActive = face.audioPage == index,
                        listening = face.listening
                    )
                }
            }
        }
        val peeked = face.peekedPage?.takeIf { it !in face.revealedPages }?.let(face.pages::getOrNull)
        AnimatedVisibility(
            visible = peeked != null,
            enter = fadeIn(effects) + expandVertically(spatialSize),
            exit =
                fadeOut(effects) + shrinkVertically(spatialSize)
        ) {
            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                Text(
                    peeked.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.xs)
                )
            }
        }
    }
}

@Composable
private fun CardPage(page: String, isFirst: Boolean, audioActive: Boolean, listening: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        if (audioActive) AudioBadge(listening)
        Text(
            text = page,
            style = if (isFirst) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
            fontSize = when {
                page.length > LONGEST_TEXT -> 18.sp
                page.length > LONGER_TEXT -> 20.sp
                page.length > LONG_TEXT -> 24.sp
                else -> 28.sp
            } * if (LocalCompactCard.current) COMPACT_FONT_SCALE else 1f,
            // cały tekst, bez ucinania — długą stronę przewija kolumna karty
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Co robi teraz dźwięk: mówi (głośnik) albo słucha (mikrofon). Słuchanie ma własny kolor
 * (tertiary) — kolor błędu jest zarezerwowany dla złej odpowiedzi.
 */
@Composable
private fun AudioBadge(listening: Boolean) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = if (listening) colors.tertiaryContainer else colors.primaryContainer,
        contentColor = if (listening) colors.onTertiaryContainer else colors.onPrimaryContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            Icon(
                if (listening) Icons.Filled.Mic else Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Text(
                stringResource(if (listening) R.string.study_badge_listening else R.string.study_badge_speaking),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
