package com.raycoll.app.ui.study

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raycoll.app.R
import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.session.AnswerStatus
import com.raycoll.app.ui.components.RayCollCard
import com.raycoll.app.ui.theme.LocalRatingColors
import com.raycoll.app.ui.theme.Spacing
import com.raycoll.app.ui.theme.answerColors
import kotlinx.coroutines.delay

/** Obraz aktywnej karty albo biernego podglądu sąsiada w pagerze. */
internal data class CardFace(
    val card: Flashcard,
    /** Aktywne strony karty (ukryte strony talii nie są pokazywane ani odkrywane). */
    val pages: List<String>,
    val revealedPages: List<Int>,
    val peekedPage: Int?,
    val audioPage: Int?,
    val listening: Boolean,
    /** Strona, z którą porównano odpowiedź głosową, i wynik — strona świeci na zielono albo czerwono. */
    val answerPage: Int? = null,
    val answerStatus: AnswerStatus = AnswerStatus.NONE
)

// próg długości tekstu (znaki) → mniejsza czcionka, żeby długie strony mieściły się na karcie
private const val LONG_TEXT = 70
private const val LONGER_TEXT = 140
private const val LONGEST_TEXT = 260

// po ustaniu dźwięku znacznik trzyma się chwilę, żeby nie migał między krokami (koniec czytania → początek następnego)
private const val AUDIO_RELEASE_MS = 250L

// w poziomie wysokość ekranu to ok. połowa pionowej — mniejsza czcionka i odstępy, żeby karta się mieściła
private const val COMPACT_FONT_SCALE = 0.72f

/** Zwarty układ karty (ekran w poziomie). */
internal val LocalCompactCard = staticCompositionLocalOf { false }

@Composable
internal fun FlashcardView(face: CardFace, actions: CardActions, modifier: Modifier, interactive: Boolean = true) {
    RayCollCard(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .pointerInput(face.card.id, actions, interactive) {
                if (!interactive) return@pointerInput
                // przytrzymanie = podgląd następnej strony; krótkie dotknięcie = zwykłe tapnięcie
                detectTapGestures(
                    onPress = {
                        actions.onPressDown()
                        var released = false
                        try {
                            released = tryAwaitRelease()
                            if (released) actions.onPressUp()
                        } finally {
                            if (!released) actions.onCancelPress()
                        }
                    }
                )
            }
    ) {
        CardPages(face)
    }
}

/** Stan strony: nic, czytana, słuchana albo wynik odpowiedzi na niej (dobrze / źle). */
private enum class AudioMark { NONE, SPEAKING, LISTENING, CORRECT, WRONG }

private data class AudioPos(val page: Int?, val listening: Boolean)

/**
 * Strony karty. Miejsce na wszystkie strony jest zajęte od początku, więc karta ma od razu
 * docelową wysokość i nic nie podskakuje przy odkrywaniu. Odkrywana strona obraca się w dół
 * jak odwracana karteczka (sprężyna Material 3 Expressive, z lekkim odbiciem); przytrzymanie
 * pokazuje zakrytą stronę półprzezroczyście w jej miejscu.
 */
@Composable
private fun CardPages(face: CardFace) {
    val compact = LocalCompactCard.current
    val gap = if (compact) Spacing.s else Spacing.l
    // pionowy odstęp każdej strony jest w jej własnym polu (nie w arrangement kolumny)
    val inset = if (compact) Spacing.xs else Spacing.s
    val audio = heldAudio(AudioPos(face.audioPage, face.listening))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = inset, end = inset, top = inset, bottom = gap - inset),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        face.pages.forEachIndexed { index, page ->
            // dźwięk na stronie ma pierwszeństwo przed wynikiem (np. druga próba po błędzie)
            val mark = when {
                audio.page == index && audio.listening -> AudioMark.LISTENING
                audio.page == index -> AudioMark.SPEAKING
                face.answerPage != index -> AudioMark.NONE
                face.answerStatus == AnswerStatus.CORRECT -> AudioMark.CORRECT
                face.answerStatus == AnswerStatus.INCORRECT -> AudioMark.WRONG
                else -> AudioMark.NONE
            }
            if (index > 0) {
                HorizontalDivider(
                    Modifier.padding(vertical = Spacing.xs),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }
            RevealablePage(
                revealed = index in face.revealedPages,
                peeked = face.peekedPage == index
            ) {
                CardPage(page = page, isFirst = index == 0, mark = mark)
            }
        }
    }
}

private const val FLIP_START_DEGREES = -90f
private const val PEEK_ALPHA = 0.45f
private const val FLIP_CAMERA_DISTANCE = 12f

/** Strona, która zajmuje swoje miejsce zawsze, a widać ją dopiero po odkryciu (albo w podglądzie). */
@Composable
private fun RevealablePage(revealed: Boolean, peeked: Boolean, content: @Composable () -> Unit) {
    // sprężyna przestrzenna Expressive lekko przestrzeliwuje — strona „dobija” z małym odbiciem
    val progress by animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "page_reveal"
    )
    val peekAlpha by animateFloatAsState(
        targetValue = if (peeked && !revealed) PEEK_ALPHA else 0f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "page_peek"
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .graphicsLayer {
                alpha = maxOf(progress.coerceIn(0f, 1f), peekAlpha)
                // podgląd bez obrotu; odkrycie — obrót od krawędzi u góry
                rotationX = if (peekAlpha > 0f && progress <= 0f) 0f else (1f - progress) * FLIP_START_DEGREES
                transformOrigin = TransformOrigin(0.5f, 0f)
                cameraDistance = FLIP_CAMERA_DISTANCE * density
            }
            // zakryta strona tylko trzyma miejsce — czytnik ekranu jej nie czyta
            .then(if (revealed || peeked) Modifier else Modifier.clearAndSetSemantics {})
    ) {
        content()
    }
}

/**
 * Strona karty. Stan strony pokazuje tylko kolor tła (bez napisów): czytana = kolor główny motywu,
 * słuchana = drugorzędny (nie myli się z wynikiem), wynik odpowiedzi = zielony (jak „Łatwe”)
 * albo czerwony (jak „Jeszcze raz”).
 * Geometria jest zawsze ta sama — zmienia się tylko kolor, więc tekst się nie przesuwa.
 */
@Composable
private fun CardPage(page: String, isFirst: Boolean, mark: AudioMark) {
    val compact = LocalCompactCard.current
    val colors = MaterialTheme.colorScheme
    val tintSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Color>()
    val inset = if (compact) Spacing.xs else Spacing.s
    val shape = if (compact) MaterialTheme.shapes.extraSmall else MaterialTheme.shapes.medium

    val ratings = LocalRatingColors.current
    // ostatni znacznik trzyma kolor, gdy tło już zanika
    var lastMark by remember { mutableStateOf(AudioMark.SPEAKING) }
    LaunchedEffect(mark) { if (mark != AudioMark.NONE) lastMark = mark }
    val (container, onContainer) = when (if (mark != AudioMark.NONE) mark else lastMark) {
        AudioMark.LISTENING -> colors.secondaryContainer to colors.onSecondaryContainer
        AudioMark.CORRECT -> ratings.answerColors(correct = true)
        AudioMark.WRONG -> ratings.answerColors(correct = false)
        else -> colors.primaryContainer to colors.onPrimaryContainer
    }
    // stan strony bez napisu na ekranie — czytnik ekranu nadal go ogłasza
    val stateLabel = when (mark) {
        AudioMark.SPEAKING -> stringResource(R.string.study_badge_speaking)
        AudioMark.LISTENING -> stringResource(R.string.study_badge_listening)
        AudioMark.CORRECT -> stringResource(R.string.study_answer_correct)
        AudioMark.WRONG -> stringResource(R.string.study_answer_incorrect)
        AudioMark.NONE -> null
    }
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
            .semantics { if (stateLabel != null) stateDescription = stateLabel }
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
