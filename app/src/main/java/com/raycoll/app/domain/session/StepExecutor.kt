package com.raycoll.app.domain.session

import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.domain.model.ListenAndCheckStep
import com.raycoll.app.domain.model.ModeStep
import com.raycoll.app.domain.model.ShowPageStep
import com.raycoll.app.domain.model.ShowAllPagesStep
import com.raycoll.app.domain.model.ShowRatingsStep
import com.raycoll.app.domain.model.SpeakPageStep
import com.raycoll.app.domain.model.SpeakAllPagesStep
import com.raycoll.app.domain.model.DynamicPauseStep
import com.raycoll.app.domain.model.WaitStep
import com.raycoll.app.domain.model.FeedbackSuccessStep
import com.raycoll.app.domain.model.FeedbackErrorStep
import com.raycoll.app.domain.model.AutoRateFromAnswerStep
import com.raycoll.app.domain.model.AutoRateFixedStep
import com.raycoll.app.domain.model.MarkFailedStep
import com.raycoll.app.domain.model.WaitForTapToRevealNextStep
import com.raycoll.app.domain.model.WaitForTapToRevealStep
import com.raycoll.app.domain.model.WaitForTapStep
import com.raycoll.app.domain.model.NextCardStep
import com.raycoll.app.domain.model.CompoundStep
import com.raycoll.app.domain.model.SrsState
import com.raycoll.app.domain.model.StudyConstants
import com.raycoll.app.domain.model.StepCondition
import com.raycoll.app.domain.srs.mapMatchToRating
import com.raycoll.app.domain.srs.matchSpeech
import kotlinx.coroutines.delay

class StepExecutor(
    private val deps: SessionDeps,
    private val state: kotlinx.coroutines.flow.MutableStateFlow<SessionState>,
    private val group: FlashcardGroup,
    private val ttsRate: Float,
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
                val pageText = getPageText(card, step.pageIndex)
                val lang = group.pageLanguages.getOrElse(step.pageIndex) { "en-US" }
                state.value = sessionReducer(state.value, SessionAction.StartSpeaking(stepIndex, step.pageIndex))
                val start = System.currentTimeMillis()
                try { deps.speak(pageText, lang, ttsRate) } catch (_: Exception) {}
                lastTtsDurationMs = System.currentTimeMillis() - start
                state.value = sessionReducer(state.value, SessionAction.EndSpeaking)
            }
            is SpeakAllPagesStep -> {
                for (pageIdx in 0 until group.activePageCount) {
                    if (state.value.status == SessionStatus.FINISHED) break
                    val pageText = getPageText(card, pageIdx)
                    val lang = group.pageLanguages.getOrElse(pageIdx) { "en-US" }
                    state.value = sessionReducer(state.value, SessionAction.StartSpeaking(stepIndex, pageIdx))
                    try { deps.speak(pageText, lang, ttsRate) } catch (_: Exception) {}
                    state.value = sessionReducer(state.value, SessionAction.EndSpeaking)
                }
            }
            is DynamicPauseStep -> {
                val pageText = getPageText(card, step.nextPageIndex)
                val pauseMs = (pageText.length * StudyConstants.DYNAMIC_PAUSE_MS_PER_CHAR * step.pauseMultiplier).toLong()
                state.value = sessionReducer(state.value, SessionAction.SetCurrentStep(stepIndex))
                delay(pauseMs)
            }
            is WaitStep -> {
                state.value = sessionReducer(state.value, SessionAction.SetCurrentStep(stepIndex))
                delay(step.ms)
            }
            is ListenAndCheckStep -> {
                val pageText = getPageText(card, step.pageIndex)
                val lang = group.pageLanguages.getOrElse(step.pageIndex) { "en-US" }
                state.value = sessionReducer(state.value, SessionAction.StartListening(stepIndex, step.pageIndex))
                val softTimeout = maxOf(StudyConstants.STT_SOFT_TIMEOUT_MS_BASE, lastTtsDurationMs * 3)
                val hardTimeout = softTimeout + StudyConstants.STT_HARD_EXTRA_MS
                val result = deps.listen(lang, hardTimeout)
                val answerResult = if (result.status == ListenStatus.SKIPPED) {
                    LastAnswerResult(status = AnswerStatus.SKIPPED, text = result.text, pageIndex = step.pageIndex, threshold = step.successThreshold)
                } else if (result.status == ListenStatus.ERROR) {
                    LastAnswerResult(status = AnswerStatus.ERROR, text = result.text, pageIndex = step.pageIndex, threshold = step.successThreshold)
                } else {
                    val percent = matchSpeech(result.text, pageText)
                    val status = if (percent >= step.successThreshold) AnswerStatus.CORRECT else AnswerStatus.INCORRECT
                    val suggestedRating = mapMatchToRating(percent, step.successThreshold)
                    LastAnswerResult(status = status, text = result.text, percent = percent, threshold = step.successThreshold, pageIndex = step.pageIndex, suggestedRating = suggestedRating)
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
                deps.haptic(rating)
                deps.onCardReviewed(group.id, card.id, rating, null)
                state.value = sessionReducer(state.value, SessionAction.SetCardReviewed)
            }
            is AutoRateFixedStep -> {
                if (state.value.cardReviewed) return
                deps.haptic(step.rating)
                deps.onCardReviewed(group.id, card.id, step.rating, null)
                state.value = sessionReducer(state.value, SessionAction.SetCardReviewed)
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
