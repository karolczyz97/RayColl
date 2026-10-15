package com.raycoll.app.ui.study

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raycoll.app.R
import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.ui.components.RayCollCard
import com.raycoll.app.ui.theme.RayCollMotion
import com.raycoll.app.ui.theme.Spacing
import kotlinx.coroutines.delay

/** To, co widać na karcie w danej chwili — animacja zmiany karty pokazuje stary i nowy stan osobno. */
internal data class CardFace(
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

private val AudioLabelIcon = 16.dp
private val AudioLabelIconCompact = 12.dp

// w poziomie wysokość ekranu to ok. połowa pionowej — mniejsza czcionka i odstępy, żeby karta się mieściła
private const val COMPACT_FONT_SCALE = 0.72f

/** Zwarty układ karty (ekran w poziomie). */
internal val LocalCompactCard = staticCompositionLocalOf { false }

@Composable
internal fun FlashcardView(face: CardFace, actions: CardActions, modifier: Modifier) {
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
            // u góry odstęp daje linia etykiety pierwszej strony
            .padding(start = inset, end = inset, top = Spacing.xxs, bottom = gap - inset),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        face.pages.forEachIndexed { index, page ->
            AnimatedVisibility(
                visible = index in face.revealedPages,
                enter = fadeIn(effects) + expandVertically(spatialSize, expandFrom = Alignment.Top),
                exit = fadeOut(effects) + shrinkVertically(spatialSize, shrinkTowards = Alignment.Top)
            ) {
                val mark = when {
                    audio.page != index -> AudioMark.NONE
                    audio.listening -> AudioMark.LISTENING
                    else -> AudioMark.SPEAKING
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AudioLabelLine(showDivider = index > 0, mark = mark)
                    CardPage(page = page, isFirst = index == 0, mark = mark)
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.xs)
                )
            }
        }
    }
}

/**
 * Linia nad stroną: kreska oddzielająca (poza pierwszą stroną) i etykieta „Czytam” / „Słucham”,
 * gdy dźwięk dotyczy tej strony. Etykieta jest zawsze rozmieszczona i zmienia tylko przezroczystość,
 * więc linia ma stałą wysokość (także przy dużej czcionce) i tekst strony się nie przesuwa.
 */
@Composable
private fun AudioLabelLine(showDivider: Boolean, mark: AudioMark) {
    val fadeSpec = remember { RayCollMotion.effects<Float>() }
    // ostatni znacznik trzyma napis, gdy etykieta już zanika
    var lastMark by remember { mutableStateOf(AudioMark.SPEAKING) }
    LaunchedEffect(mark) { if (mark != AudioMark.NONE) lastMark = mark }
    val shown = if (mark != AudioMark.NONE) mark else lastMark
    val alpha by animateFloatAsState(
        targetValue = if (mark == AudioMark.NONE) 0f else 1f,
        animationSpec = fadeSpec,
        label = "audio_label"
    )
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xxs),
        contentAlignment = Alignment.Center
    ) {
        if (showDivider) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        AudioLabel(
            listening = shown == AudioMark.LISTENING,
            // niewidoczna etykieta tylko trzyma miejsce — TalkBack jej nie czyta
            modifier = Modifier
                .graphicsLayer { this.alpha = alpha }
                .then(if (mark == AudioMark.NONE) Modifier.clearAndSetSemantics {} else Modifier)
        )
    }
}

@Composable
private fun AudioLabel(listening: Boolean, modifier: Modifier) {
    val compact = LocalCompactCard.current
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = CircleShape,
        color = if (listening) colors.tertiaryContainer else colors.primaryContainer,
        contentColor = if (listening) colors.onTertiaryContainer else colors.onPrimaryContainer,
        modifier = modifier
    ) {
        Row(
            Modifier.padding(horizontal = if (compact) Spacing.s else Spacing.m, vertical = Spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            Icon(
                imageVector = if (listening) Icons.Filled.Mic else Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = null,
                modifier = Modifier.size(if (compact) AudioLabelIconCompact else AudioLabelIcon)
            )
            Text(
                stringResource(if (listening) R.string.study_badge_listening else R.string.study_badge_speaking),
                style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                maxLines = 1
            )
        }
    }
}

/**
 * Strona karty. Strona, którą właśnie czytamy lub której słuchamy, dostaje tło i ramkę; geometria
 * jest zawsze ta sama — zmienia się tylko kolor, więc tekst się nie przesuwa.
 */
@Composable
private fun CardPage(page: String, isFirst: Boolean, mark: AudioMark) {
    val compact = LocalCompactCard.current
    val colors = MaterialTheme.colorScheme
    val tintSpec = remember { RayCollMotion.effects<Color>() }
    val inset = if (compact) Spacing.xs else Spacing.s
    val shape = if (compact) MaterialTheme.shapes.extraSmall else MaterialTheme.shapes.medium

    // ostatni znacznik trzyma kolor, gdy tło już zanika
    var lastMark by remember { mutableStateOf(AudioMark.SPEAKING) }
    LaunchedEffect(mark) { if (mark != AudioMark.NONE) lastMark = mark }
    val listening = (if (mark != AudioMark.NONE) mark else lastMark) == AudioMark.LISTENING
    val container = if (listening) colors.tertiaryContainer else colors.primaryContainer
    val onContainer = if (listening) colors.onTertiaryContainer else colors.onPrimaryContainer
    // nieaktywna: ten sam odcień z alfa 0 (Color.Transparent zanikałby przez czerń)
    val tint by animateColorAsState(
        targetValue = if (mark == AudioMark.NONE) container.copy(alpha = 0f) else container,
        animationSpec = tintSpec,
        label = "audio_tint"
    )

    val size = pageFontSize(page, compact)
    Text(
        text = page,
        style = if (isFirst) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
        fontSize = size,
        lineHeight = size * 1.3f,
        // cały tekst, bez ucinania — długą stronę przewija kolumna karty
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .background(tint, shape)
            .border(1.dp, onContainer.copy(alpha = 0.35f * tint.alpha), shape)
            .padding(horizontal = if (compact) Spacing.s else Spacing.m, vertical = inset)
    )
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
