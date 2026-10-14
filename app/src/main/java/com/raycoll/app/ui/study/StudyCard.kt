package com.raycoll.app.ui.study

import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
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

// po ustaniu dźwięku znacznik trzyma się chwilę, żeby nie migał między krokami (koniec czytania → początek następnego)
private const val AUDIO_RELEASE_MS = 250L

// jak długo przewijanie podąża za rosnącą (animowaną) nowo odsłoniętą stroną
private const val REVEAL_FOLLOW_MS = 700L

// stałe miejsce na znacznik dźwięku po bokach strony — tekst nigdy się nie przesuwa; symetrycznie, żeby wyśrodkowany tekst został w środku
private val AudioGutter = 20.dp
private val AudioGutterCompact = 16.dp
private val AudioIcon = 16.dp
private val AudioIconCompact = 14.dp

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
        if (session.revealedPages.size <= 1) {
            scrollState.scrollTo(0)
        } else {
            // strona rośnie w animacji — dół przewijanej treści przesuwa się razem z nią
            withTimeoutOrNull(REVEAL_FOLLOW_MS) {
                snapshotFlow { scrollState.maxValue }.collectLatest { scrollState.animateScrollTo(it) }
            }
        }
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
                interactionSource = remember { MutableInteractionSource() },
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
        // stała wysokość miejsca na kontrolki: okno karty nie skacze przy odsłonięciu i ocenie
        modifier = modifier.heightIn(min = RatingButtonHeight)
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
    CardNavButton(Icons.AutoMirrored.Filled.NavigateBefore, R.string.study_prev_card, actions.onPrevious)
}

@Composable
private fun NextCardButton(actions: CardActions) {
    CardNavButton(Icons.AutoMirrored.Filled.NavigateNext, R.string.study_next_card, actions.onNext)
}

@Composable
private fun CardNavButton(icon: ImageVector, @StringRes description: Int, onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick) {
        Icon(icon, contentDescription = stringResource(description))
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

/** Co robi dźwięk na danej stronie: nic, mówi (głośnik) albo słucha (mikrofon). */
private enum class AudioMark { NONE, SPEAKING, LISTENING }

private data class AudioPos(val page: Int?, val listening: Boolean)

@Composable
private fun CardPages(face: CardFace) {
    val effects = remember { RayCollMotion.effects<Float>() }
    val spatialSize = remember { RayCollMotion.spatial<IntSize>() }
    val compact = LocalCompactCard.current
    val gap = if (compact) Spacing.s else Spacing.l
    // pionowy odstęp każdej strony jest w jej własnym polu (nie w arrangement kolumny): ukryte strony nie dokładają przerw
    val inset = if (compact) Spacing.xs else Spacing.s
    val audio = heldAudio(AudioPos(face.audioPage, face.listening))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = inset, vertical = gap - inset),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        face.pages.forEachIndexed { index, page ->
            AnimatedVisibility(
                visible = index in face.revealedPages,
                enter = fadeIn(effects) + expandVertically(spatialSize, expandFrom = Alignment.Top),
                exit = fadeOut(effects) + shrinkVertically(spatialSize, shrinkTowards = Alignment.Top)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (index > 0) {
                        HorizontalDivider(
                            Modifier.padding(vertical = Spacing.xs),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                    CardPage(
                        page = page,
                        isFirst = index == 0,
                        mark = when {
                            audio.page != index -> AudioMark.NONE
                            audio.listening -> AudioMark.LISTENING
                            else -> AudioMark.SPEAKING
                        }
                    )
                }
            }
        }
        val peeked = face.peekedPage?.takeIf { it !in face.revealedPages }?.let(face.pages::getOrNull)
        AnimatedVisibility(
            visible = peeked != null,
            enter = fadeIn(effects) + expandVertically(spatialSize),
            exit = fadeOut(effects) + shrinkVertically(spatialSize)
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.padding(top = inset)
            ) {
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

/**
 * Strona karty. Strona, którą właśnie czytamy lub której słuchamy, dostaje tło i ramkę oraz ikonę
 * w stałym marginesie z lewej. Cała geometria (margines, odstępy) jest zawsze ta sama — zmienia się
 * tylko kolor i przezroczystość, więc tekst się nie przesuwa. Opis dla TalkBack siedzi na ikonie.
 */
@Composable
private fun CardPage(page: String, isFirst: Boolean, mark: AudioMark) {
    val compact = LocalCompactCard.current
    val colors = MaterialTheme.colorScheme
    val tintSpec = remember { RayCollMotion.effects<Color>() }
    val fadeSpec = remember { RayCollMotion.effects<Float>() }
    val gutter = if (compact) AudioGutterCompact else AudioGutter
    val iconSize = if (compact) AudioIconCompact else AudioIcon
    val inset = if (compact) Spacing.xs else Spacing.s
    val shape = if (compact) MaterialTheme.shapes.extraSmall else MaterialTheme.shapes.medium

    // ostatni znacznik trzyma ikonę i kolor, gdy tło już zanika
    var lastMark by remember { mutableStateOf(AudioMark.SPEAKING) }
    LaunchedEffect(mark) { if (mark != AudioMark.NONE) lastMark = mark }
    val shown = if (mark != AudioMark.NONE) mark else lastMark
    val listening = shown == AudioMark.LISTENING
    val container = if (listening) colors.tertiaryContainer else colors.primaryContainer
    val onContainer = if (listening) colors.onTertiaryContainer else colors.onPrimaryContainer
    // nieaktywna: ten sam odcień z alfa 0 (Color.Transparent zanikałby przez czerń)
    val tint by animateColorAsState(
        targetValue = if (mark == AudioMark.NONE) container.copy(alpha = 0f) else container,
        animationSpec = tintSpec,
        label = "audio_tint"
    )

    val size = pageFontSize(page, compact)
    val lineHeight = size * 1.3f
    val firstLine = with(LocalDensity.current) { lineHeight.toDp() }

    Box(
        Modifier
            .fillMaxWidth()
            .background(tint, shape)
            .border(1.dp, onContainer.copy(alpha = 0.35f * tint.alpha), shape)
    ) {
        // ikona leży w lewym marginesie i nie wpływa na rozmiar pola
        AnimatedVisibility(
            visible = mark != AudioMark.NONE,
            enter = fadeIn(fadeSpec),
            exit = fadeOut(fadeSpec),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = (gutter - iconSize) / 2, top = inset + (firstLine - iconSize) / 2)
        ) {
            Icon(
                imageVector = if (listening) Icons.Filled.Mic else Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = stringResource(
                    if (listening) R.string.study_badge_listening else R.string.study_badge_speaking
                ),
                tint = onContainer,
                modifier = Modifier.size(iconSize)
            )
        }
        Text(
            text = page,
            style = if (isFirst) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
            fontSize = size,
            lineHeight = lineHeight,
            // cały tekst, bez ucinania — długą stronę przewija kolumna karty
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = gutter, vertical = inset)
        )
    }
}

/** Rozmiar tekstu strony: im dłuższa, tym mniejszy; w poziomie dodatkowo zwarty. */
internal fun pageFontSize(page: String, compact: Boolean): TextUnit {
    val base = when {
        page.length > LONGEST_TEXT -> 18.sp
        page.length > LONGER_TEXT -> 20.sp
        page.length > LONG_TEXT -> 24.sp
        else -> 28.sp
    }
    return if (compact) base * COMPACT_FONT_SCALE else base
}

/** Pozycja dźwięku z chwilowym przytrzymaniem po jego ustaniu: pojawia się od razu, znika z opóźnieniem. */
@Composable
private fun heldAudio(now: AudioPos): AudioPos {
    var held by remember { mutableStateOf(now) }
    LaunchedEffect(now) {
        if (now.page == null) delay(AUDIO_RELEASE_MS)
        held = now
    }
    return if (now.page != null) now else held
}
