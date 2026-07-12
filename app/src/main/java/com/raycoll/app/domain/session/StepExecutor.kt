package com.raycoll.app.domain.session

import com.raycoll.app.domain.model.AutoRateFixedStep
import com.raycoll.app.domain.model.AutoRateFromAnswerStep
import com.raycoll.app.domain.model.CompoundStep
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

class StepExecutor(
    private val deps: SessionDeps,
    private val state: MutableStateFlow<SessionState>,
    private val group: FlashcardGroup,
    private val ttsRate: Float,
    private val engine: SessionEngine,
) {
    private var lastTtsDurationMs: Long = 0

    suspend fun executeStep(step: ModeStep, card: Flashcard, stepIndex: Int) {
        if (!checkCondition(step)) return

        when (step) {
            is ShowPageStep -> {
                state.value = sessionReducer(state.value, SessionAction.RevealPage(stepIndex, step.pageIndex))
            }
            is ShowAllPagesStep -> {
                val allPages = (0 until group.activePageCount).toList()
                state.value = sessionReducer(state.value, SessionAction.RevealPages(allPages))
            }
            is WaitForTapToRevealNextStep -> {
                val hiddenPages = (0 until group.activePageCount).filter { it !in state.value.revealedPages }
                if (hiddenPages.isEmpty()) return
                state.value = sessionReducer(state.value, SessionAction.StartTapRevealGate(stepIndex + 1, TapRevealMode.SINGLE))
                return
            }
            is WaitForTapToRevealStep -> {
                val hiddenPages = (0 until group.activePageCount).filter { it !in state.value.revealedPages }
                if (hiddenPages.isEmpty()) return
                state.value = sessionReducer(state.value, SessionAction.StartTapRevealGate(stepIndex + 1, TapRevealMode.REMAINING))
                return
            }
            is ShowRatingsStep -> {
                if (state.value.cardReviewed) return
                state.value = sessionReducer(state.value, SessionAction.ShowRatings)
                return
            }
            is SpeakPageStep -> {
                speakSinglePage(card, stepIndex, step.pageIndex)
            }
            is SpeakAllPagesStep -> {
                // tap przerywa i POMIJA pozostałe strony — jeden skippable dla całej pętli
                engine.runSkippable {
                    for (pageIdx in 0 until group.activePageCount) {
                        if (state.value.status == SessionStatus.FINISHED) break
                        speakPageBody(card, stepIndex, pageIdx)
                    }
                }
                state.value = sessionReducer(state.value, SessionAction.EndSpeaking)
            }
            is DynamicPauseStep -> {
                val pageText = getPageText(card, step.nextPageIndex)
                val pauseMs = (pageText.length * StudyConstants.DYNAMIC_PAUSE_MS_PER_CHAR * step.pauseMultiplier).toLong()
                state.value = sessionReducer(state.value, SessionAction.SetCurrentStep(stepIndex))
                engine.runSkippable { delay(pauseMs) }
            }
            is WaitStep -> {
                state.value = sessionReducer(state.value, SessionAction.SetCurrentStep(stepIndex))
                engine.runSkippable { delay(step.ms) }
            }
            is ListenAndCheckStep -> {
                val pageText = getPageText(card, step.pageIndex)
                val lang = group.pageLanguages.getOrElse(step.pageIndex) { "en-US" }
                state.value = sessionReducer(state.value, SessionAction.StartListening(stepIndex, step.pageIndex))
                val softTimeout = maxOf(StudyConstants.STT_SOFT_TIMEOUT_MS_BASE, lastTtsDurationMs * 3)
                val hardTimeout = softTimeout + StudyConstants.STT_HARD_EXTRA_MS
                var result: ListenResult? = null
                val skipped = engine.runSkippable {
                    result = deps.listen(lang, hardTimeout) { partial ->
                        state.value = sessionReducer(state.value, SessionAction.UpdatePartialStt(partial))
                    }
                }
                val listenResult = result
                val answerResult = when {
                    // tap podczas nasłuchu → 'skipped' BEZ liczenia dopasowania
                    skipped || listenResult == null || listenResult.status == ListenStatus.SKIPPED ->
                        LastAnswerResult(status = AnswerStatus.SKIPPED, text = "", pageIndex = step.pageIndex, threshold = step.successThreshold)
                    listenResult.status == ListenStatus.ERROR -> {
                        engine.onSttFailure()
                        LastAnswerResult(status = AnswerStatus.ERROR, text = listenResult.text, pageIndex = step.pageIndex, threshold = step.successThreshold)
                    }
                    else -> {
                        val percent = matchSpeech(listenResult.text, pageText)
                        val status = if (percent >= step.successThreshold) AnswerStatus.CORRECT else AnswerStatus.INCORRECT
                        val suggestedRating = mapMatchToRating(percent, step.successThreshold)
                        LastAnswerResult(status = status, text = listenResult.text, percent = percent, threshold = step.successThreshold, pageIndex = step.pageIndex, suggestedRating = suggestedRating)
                    }
                }
                state.value = sessionReducer(state.value, SessionAction.SetLastAnswerResult(answerResult))
                state.value = sessionReducer(state.value, SessionAction.EndSpeaking)
            }
            is FeedbackSuccessStep -> { deps.feedbackSuccess() }
            is FeedbackErrorStep -> { deps.feedbackError() }
            is AutoRateFromAnswerStep -> {
                val result = state.value.lastAnswerResult
                if (state.value.cardReviewed) return
                if (result.status != AnswerStatus.CORRECT && result.status != AnswerStatus.INCORRECT) return
                val rating = result.suggestedRating ?: mapMatchToRating(result.percent ?: 0, result.threshold)
                engine.submitReview(rating)
            }
            is AutoRateFixedStep -> {
                if (state.value.cardReviewed) return
                engine.submitReview(step.rating)
            }
            is MarkFailedStep -> {
                state.value = sessionReducer(state.value, SessionAction.AddFailedCard(card.id))
            }
            is WaitForTapStep -> {
                state.value = sessionReducer(state.value, SessionAction.StartTapRevealGate(stepIndex + 1, TapRevealMode.NONE))
                return
            }
            is NextCardStep -> {
                return
            }
            is CompoundStep -> {
                // Compound steps should already be expanded
            }
        }
    }

    private suspend fun speakSinglePage(card: Flashcard, stepIndex: Int, pageIndex: Int) {
        engine.runSkippable { speakPageBody(card, stepIndex, pageIndex) }
        state.value = sessionReducer(state.value, SessionAction.EndSpeaking)
    }

    private suspend fun speakPageBody(card: Flashcard, stepIndex: Int, pageIndex: Int) {
        val pageText = getPageText(card, pageIndex)
        val lang = group.pageLanguages.getOrElse(pageIndex) { "en-US" }
        state.value = sessionReducer(state.value, SessionAction.StartSpeaking(stepIndex, pageIndex))
        val start = System.currentTimeMillis()
        val result = try {
            deps.speak(pageText, lang, ttsRate)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e // przerwanie tapem ≠ błąd
        } catch (_: Exception) {
            SpeakResult.ERROR
        }
        lastTtsDurationMs = System.currentTimeMillis() - start
        if (result != SpeakResult.SUCCESS) {
            engine.onTtsFailure(result)
        }
    }

    private fun checkCondition(step: ModeStep): Boolean {
        val condition = step.condition ?: return true
        val status = state.value.lastAnswerResult.status
        return when (condition) {
            StepCondition.CORRECT -> status == AnswerStatus.CORRECT
            StepCondition.WRONG -> status == AnswerStatus.INCORRECT
        }
    }

    private fun getPageText(card: Flashcard, pageIndex: Int): String =
        card.pages.getOrElse(pageIndex) { "" }
}
