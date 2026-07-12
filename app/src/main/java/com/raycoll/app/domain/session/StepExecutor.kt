package com.raycoll.app.domain.session

import com.raycoll.app.domain.model.*
import com.raycoll.app.domain.srs.mapMatchToRating
import com.raycoll.app.domain.srs.matchSpeech
import kotlinx.coroutines.delay

object StepExecutor {

    suspend fun executeStep(
        state: StudySessionState,
        ctx: SessionContext,
    ): List<SessionAction> {
        val step = ctx.steps.getOrNull(state.currentStepIndex) ?: return listOf(SessionAction.FinishSession)
        val card = ctx.cards.getOrNull(state.currentCardIndex) ?: return listOf(SessionAction.FinishSession)

        val cond = step.condition
        val lastAnswer = state.lastAnswerResult.status
        if (cond != null) {
            if (cond == StepCondition.correct && lastAnswer != AnswerStatus.Correct)
                return listOf(SessionAction.SetCurrentStep(state.currentStepIndex + 1))
            if (cond == StepCondition.wrong && lastAnswer != AnswerStatus.Incorrect)
                return listOf(SessionAction.SetCurrentStep(state.currentStepIndex + 1))
        }

        return when (step) {
            is ShowPage -> listOf(
                SessionAction.RevealPage(state.currentStepIndex, step.pageIndex),
                SessionAction.SetCurrentStep(state.currentStepIndex + 1)
            )
            is ShowAllPages -> {
                val allPages = (0 until ctx.group.activePageCount).toSet()
                listOf(SessionAction.RevealPages(allPages), SessionAction.SetCurrentStep(state.currentStepIndex + 1))
            }
            is SpeakPage -> {
                val pageIdx = step.pageIndex.coerceIn(0, ctx.group.activePageCount - 1)
                val text = card.pages.getOrElse(pageIdx) { "" }
                val lang = ctx.group.pageLanguages.getOrElse(pageIdx) { "en-US" }
                ctx.deps.ttsSpeak(text, lang, 1.0f)
                listOf(SessionAction.SetCurrentStep(state.currentStepIndex + 1))
            }
            is SpeakAllPages -> {
                for (i in 0 until ctx.group.activePageCount) {
                    val text = card.pages.getOrElse(i) { "" }
                    val lang = ctx.group.pageLanguages.getOrElse(i) { "en-US" }
                    ctx.deps.ttsSpeak(text, lang, 1.0f)
                }
                listOf(SessionAction.SetCurrentStep(state.currentStepIndex + 1))
            }
            is DynamicPause -> {
                val nextPage = step.nextPageIndex.coerceIn(0, ctx.group.activePageCount - 1)
                val text = card.pages.getOrElse(nextPage) { "" }
                val duration = maxOf(500L, (text.length * 60L * step.pauseMultiplier))
                delay(duration)
                listOf(SessionAction.SetCurrentStep(state.currentStepIndex + 1))
            }
            is Wait -> {
                delay(step.ms.coerceAtLeast(0).toLong())
                listOf(SessionAction.SetCurrentStep(state.currentStepIndex + 1))
            }
            is ListenAndCheck -> {
                val pageIdx = step.pageIndex.coerceIn(0, ctx.group.activePageCount - 1)
                val original = card.pages.getOrElse(pageIdx) { "" }
                val lang = ctx.group.pageLanguages.getOrElse(pageIdx) { "en-US" }
                val result = ctx.deps.sttListen(lang, 15000L)
                val answer = when {
                    result.skipped -> LastAnswerResult(AnswerStatus.Skipped)
                    result.error != null -> LastAnswerResult(AnswerStatus.Error, text = result.text)
                    else -> {
                        val matchPercent = matchSpeech(result.text, original)
                        val rating = mapMatchToRating(matchPercent, step.successThreshold)
                        val status = if (rating == 1) AnswerStatus.Incorrect else AnswerStatus.Correct
                        LastAnswerResult(status, result.text, matchPercent, step.successThreshold, pageIdx, rating)
                    }
                }
                listOf(SessionAction.SetLastAnswerResult(answer), SessionAction.SetCurrentStep(state.currentStepIndex + 1))
            }
            is FeedbackSuccess -> { ctx.deps.feedbackSuccess(); listOf(SessionAction.SetCurrentStep(state.currentStepIndex + 1)) }
            is FeedbackError -> { ctx.deps.feedbackError(); listOf(SessionAction.SetCurrentStep(state.currentStepIndex + 1)) }
            is AutoRateFromAnswer -> {
                val r = state.lastAnswerResult
                if (r.status == AnswerStatus.Correct || r.status == AnswerStatus.Incorrect) {
                    val rating = r.suggestedRating ?: mapMatchToRating(r.percent ?: 0, r.threshold)
                    ctx.deps.haptic(rating)
                }
                listOf(SessionAction.SetCurrentStep(state.currentStepIndex + 1))
            }
            is AutoRateFixed -> { ctx.deps.haptic(step.rating); listOf(SessionAction.SetCurrentStep(state.currentStepIndex + 1)) }
            is MarkFailed -> listOf(SessionAction.MarkFailed(card.id), SessionAction.SetCurrentStep(state.currentStepIndex + 1))
            is WaitForTap -> listOf()
            is WaitForTapToReveal -> {
                val all = (0 until ctx.group.activePageCount).toSet()
                if (state.revealedPages.size >= ctx.group.activePageCount - 1)
                    listOf(SessionAction.RevealPages(all), SessionAction.SetCurrentStep(state.currentStepIndex + 1))
                else
                    listOf(SessionAction.StartTapRevealGate(state.currentStepIndex + 1, TapRevealMode.Remaining))
            }
            is WaitForTapToRevealNext -> {
                val nextHidden = (0 until ctx.group.activePageCount).firstOrNull { it !in state.revealedPages }
                if (nextHidden != null && state.revealedPages.size < ctx.group.activePageCount)
                    listOf(SessionAction.RevealNextPageInGate(nextHidden),
                        SessionAction.StartTapRevealGate(
                            if (state.revealedPages.size + 1 >= ctx.group.activePageCount) state.currentStepIndex + 1 else state.currentStepIndex,
                            TapRevealMode.Single))
                else listOf(SessionAction.SetCurrentStep(state.currentStepIndex + 1))
            }
            is ShowRatings ->
                if (state.cardReviewState == CardReviewState.None) listOf(SessionAction.ShowRatings)
                else listOf(SessionAction.SetCurrentStep(state.currentStepIndex + 1))
            is NextCard -> listOf()
            else -> listOf(SessionAction.SetCurrentStep(state.currentStepIndex + 1))
        }
    }
}
