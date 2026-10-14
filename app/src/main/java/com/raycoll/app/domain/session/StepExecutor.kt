package com.raycoll.app.domain.session

import com.raycoll.app.core.Clock
import com.raycoll.app.domain.model.AutoRateFixedStep
import com.raycoll.app.domain.model.AutoRateFromAnswerStep
import com.raycoll.app.domain.model.CompoundStep
import com.raycoll.app.domain.model.DEFAULT_PAGE_LANGUAGE
import com.raycoll.app.domain.model.DynamicPauseStep
import com.raycoll.app.domain.model.FeedbackErrorStep
import com.raycoll.app.domain.model.FeedbackSuccessStep
import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.domain.model.ListenAndCheckStep
import com.raycoll.app.domain.model.MarkFailedStep
import com.raycoll.app.domain.model.ModeStep
import com.raycoll.app.domain.model.NextCardStep
import com.raycoll.app.domain.model.ShowAllPagesStep
import com.raycoll.app.domain.model.ShowPageStep
import com.raycoll.app.domain.model.ShowRatingsStep
import com.raycoll.app.domain.model.SpeakAllPagesStep
import com.raycoll.app.domain.model.SpeakPageStep
import com.raycoll.app.domain.model.StepCondition
import com.raycoll.app.domain.model.StudyConstants
import com.raycoll.app.domain.model.WaitForTapStep
import com.raycoll.app.domain.model.WaitForTapToRevealNextStep
import com.raycoll.app.domain.model.WaitForTapToRevealStep
import com.raycoll.app.domain.model.WaitStep
import com.raycoll.app.domain.srs.mapMatchToRating
import com.raycoll.app.domain.srs.matchSpeech
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** Wykonuje pojedyncze kroki atomowe dla jednej karty; przebieg kroków prowadzi [SessionEngine]. */
internal class StepExecutor(
    private val engine: SessionEngine,
    private val deps: SessionDeps,
    private val group: FlashcardGroup,
    private val clock: Clock,
    private val ttsRate: Float
) {
    private var lastTtsDurationMs: Long = 0

    private val state: SessionState get() = engine.state.value

    /**
     * 'correct' pasuje tylko do poprawnej odpowiedzi, 'wrong' tylko do błędnej;
     * pominięta/błąd/brak odpowiedzi nie pasuje do żadnego warunku.
     */
    fun isConditionMet(step: ModeStep): Boolean = when (step.condition) {
        null -> true
        StepCondition.CORRECT -> state.lastAnswerResult.status == AnswerStatus.CORRECT
        StepCondition.WRONG -> state.lastAnswerResult.status == AnswerStatus.INCORRECT
    }

    /** Wykonuje krok o spełnionym warunku (sprawdza wołający przez [isConditionMet]). */
    suspend fun execute(step: ModeStep, card: Flashcard, stepIndex: Int) {
        when (step) {
            is ShowPageStep -> engine.dispatch(SessionAction.RevealPages(listOf(step.pageIndex)))

            is ShowAllPagesStep -> engine.dispatch(SessionAction.RevealPages((0 until group.activePageCount).toList()))

            is WaitForTapToRevealNextStep -> startRevealGate(stepIndex, TapRevealMode.SINGLE)

            is WaitForTapToRevealStep -> startRevealGate(stepIndex, TapRevealMode.REMAINING)

            is WaitForTapStep -> engine.dispatch(SessionAction.StartTapRevealGate(stepIndex + 1, TapRevealMode.NONE))

            is ShowRatingsStep -> if (!state.cardReviewed) engine.dispatch(SessionAction.ShowRatings)

            is SpeakPageStep -> {
                engine.runSkippable { speakPage(card, step.pageIndex) }
                engine.dispatch(SessionAction.EndSpeaking)
            }

            is SpeakAllPagesStep -> {
                // tap przerywa i pomija wszystkie pozostałe strony naraz
                engine.runSkippable {
                    for (pageIndex in 0 until group.activePageCount) {
                        if (state.status == SessionStatus.FINISHED) break
                        speakPage(card, pageIndex)
                    }
                }
                engine.dispatch(SessionAction.EndSpeaking)
            }

            is DynamicPauseStep -> {
                val pauseMs = pageText(card, step.nextPageIndex).length *
                    StudyConstants.DYNAMIC_PAUSE_MS_PER_CHAR * step.pauseMultiplier
                engine.dispatch(SessionAction.PeekClear)
                engine.runSkippable { delay(pauseMs.toLong()) }
            }

            is WaitStep -> {
                engine.dispatch(SessionAction.PeekClear)
                engine.runSkippable { delay(step.ms) }
            }

            is ListenAndCheckStep -> listenAndCheck(step, card)

            is FeedbackSuccessStep -> deps.feedbackSuccess()

            is FeedbackErrorStep -> deps.feedbackError()

            is AutoRateFromAnswerStep -> {
                val result = state.lastAnswerResult
                val answered = result.status == AnswerStatus.CORRECT || result.status == AnswerStatus.INCORRECT
                if (!state.cardReviewed && answered) {
                    engine.submitReview(
                        result.suggestedRating ?: mapMatchToRating(result.percent ?: 0, result.threshold)
                    )
                }
            }

            is AutoRateFixedStep -> if (!state.cardReviewed) engine.submitReview(step.rating)

            is MarkFailedStep -> engine.dispatch(SessionAction.AddFailedCard(card.id))

            // przejście do następnej karty robi silnik — musi poczekać na puszczenie trzymanej karty
            is NextCardStep -> Unit

            // kroki złożone są rozwijane przed startem sesji
            is CompoundStep -> Unit
        }
    }

    private fun startRevealGate(stepIndex: Int, mode: TapRevealMode) {
        val hasHiddenPages = (0 until group.activePageCount).any { it !in state.revealedPages }
        if (hasHiddenPages) engine.dispatch(SessionAction.StartTapRevealGate(stepIndex + 1, mode))
    }

    private suspend fun listenAndCheck(step: ListenAndCheckStep, card: Flashcard) {
        val expected = pageText(card, step.pageIndex)
        if (expected.isBlank()) {
            engine.dispatch(
                SessionAction.SetLastAnswerResult(
                    LastAnswerResult(
                        status = AnswerStatus.SKIPPED,
                        pageIndex = step.pageIndex,
                        threshold = step.successThreshold
                    )
                )
            )
            return
        }
        engine.dispatch(SessionAction.StartListening(step.pageIndex))
        // czas nasłuchu skalowany długością ostatniej wypowiedzi TTS (dłuższa fraza = dłuższa odpowiedź)
        val timeoutMs =
            maxOf(StudyConstants.STT_SOFT_TIMEOUT_MS_BASE, lastTtsDurationMs * 3) + StudyConstants.STT_HARD_EXTRA_MS
        var listenResult: ListenResult? = null
        val tapped = engine.runSkippable {
            listenResult = deps.listen(languageOf(step.pageIndex), timeoutMs) { partial ->
                engine.dispatch(SessionAction.UpdatePartialStt(partial))
            }
        }
        val result = listenResult
        val answer = when {
            // tap podczas nasłuchu = pominięcie, bez liczenia dopasowania
            tapped || result == null || result.status == ListenStatus.SKIPPED ->
                LastAnswerResult(
                    status = AnswerStatus.SKIPPED,
                    pageIndex = step.pageIndex,
                    threshold = step.successThreshold
                )

            result.status == ListenStatus.ERROR -> {
                engine.onSttFailure()
                LastAnswerResult(
                    status = AnswerStatus.ERROR,
                    text = result.text,
                    pageIndex = step.pageIndex,
                    threshold = step.successThreshold
                )
            }

            else -> {
                val percent = matchSpeech(result.text, expected)
                LastAnswerResult(
                    status = if (percent >= step.successThreshold) AnswerStatus.CORRECT else AnswerStatus.INCORRECT,
                    text = result.text,
                    percent = percent,
                    threshold = step.successThreshold,
                    pageIndex = step.pageIndex,
                    suggestedRating = mapMatchToRating(percent, step.successThreshold)
                )
            }
        }
        engine.dispatch(SessionAction.SetLastAnswerResult(answer))
        engine.dispatch(SessionAction.EndSpeaking)
    }

    private suspend fun speakPage(card: Flashcard, pageIndex: Int) {
        val text = pageText(card, pageIndex)
        // pusta strona: krok mowy kończy się od razu (strona nadal może być pokazana innym krokiem)
        if (text.isBlank()) {
            lastTtsDurationMs = 0
            return
        }
        engine.dispatch(SessionAction.StartSpeaking(pageIndex))
        val start = clock.now()
        val result = try {
            deps.speak(text, languageOf(pageIndex), ttsRate)
        } catch (e: CancellationException) {
            throw e // tap przerywający mowę to nie błąd
        } catch (_: Exception) {
            SpeakResult.ERROR
        }
        lastTtsDurationMs = clock.now() - start
        if (result != SpeakResult.SUCCESS) engine.onTtsFailure(result)
    }

    private fun pageText(card: Flashcard, pageIndex: Int): String = card.pages.getOrElse(pageIndex) { "" }

    private fun languageOf(pageIndex: Int): String = group.pageLanguages.getOrElse(pageIndex) { DEFAULT_PAGE_LANGUAGE }
}
