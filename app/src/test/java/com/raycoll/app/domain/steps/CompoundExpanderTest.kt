package com.raycoll.app.domain.steps

import com.raycoll.app.domain.model.AutoRateFixedStep
import com.raycoll.app.domain.model.AutoRateFromAnswerStep
import com.raycoll.app.domain.model.BranchRating
import com.raycoll.app.domain.model.CompoundBranch
import com.raycoll.app.domain.model.CompoundParams
import com.raycoll.app.domain.model.CompoundStep
import com.raycoll.app.domain.model.FeedbackErrorStep
import com.raycoll.app.domain.model.FeedbackSuccessStep
import com.raycoll.app.domain.model.ListenAndCheckStep
import com.raycoll.app.domain.model.MarkFailedStep
import com.raycoll.app.domain.model.NextCardStep
import com.raycoll.app.domain.model.Ratings
import com.raycoll.app.domain.model.RevealStyle
import com.raycoll.app.domain.model.ShowAllPagesStep
import com.raycoll.app.domain.model.ShowPageStep
import com.raycoll.app.domain.model.ShowRatingsStep
import com.raycoll.app.domain.model.SpeakPageStep
import com.raycoll.app.domain.model.StepCondition
import com.raycoll.app.domain.model.WaitForTapStep
import com.raycoll.app.domain.model.WaitForTapToRevealStep
import com.raycoll.app.domain.model.WaitStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompoundExpanderTest {

    @Test
    fun `present_front expands to show_page and optional speak_page`() {
        val step = CompoundStep(params = CompoundParams.PresentFront(page = 0, speak = false))
        val expanded = CompoundExpander.expandStep(step)
        assertEquals(1, expanded.size)
        assertTrue(expanded[0] is ShowPageStep)
    }

    @Test
    fun `present_front with speak adds speak_page`() {
        val step = CompoundStep(params = CompoundParams.PresentFront(page = 1, speak = true))
        val expanded = CompoundExpander.expandStep(step)
        assertEquals(2, expanded.size)
        assertTrue(expanded[0] is ShowPageStep)
        assertTrue(expanded[1] is SpeakPageStep)
    }

    @Test
    fun `flip_reveal all expands to 3 steps`() {
        val step = CompoundStep(params = CompoundParams.FlipReveal(revealStyle = RevealStyle.ALL))
        val expanded = CompoundExpander.expandStep(step)
        assertEquals(3, expanded.size)
        assertTrue(expanded[0] is WaitForTapStep)
        assertTrue(expanded[1] is ShowAllPagesStep)
        assertTrue(expanded[2] is ShowRatingsStep)
    }

    @Test
    fun `flip_reveal next expands to 2 steps`() {
        val step = CompoundStep(params = CompoundParams.FlipReveal(revealStyle = RevealStyle.NEXT))
        val expanded = CompoundExpander.expandStep(step)
        assertEquals(2, expanded.size)
        assertTrue(expanded[0] is WaitForTapToRevealStep)
    }

    @Test
    fun `auto_pass_next expands to rate and next_card`() {
        val step = CompoundStep(params = CompoundParams.AutoPassNext(rating = 3))
        val expanded = CompoundExpander.expandStep(step)
        assertEquals(2, expanded.size)
        assertTrue(expanded[0] is AutoRateFixedStep)
        assertTrue(expanded[1] is NextCardStep)
    }

    @Test
    fun `fail_next expands to mark_failed, rate and next_card`() {
        val step = CompoundStep(params = CompoundParams.FailNext)
        val expanded = CompoundExpander.expandStep(step)
        assertEquals(3, expanded.size)
        assertTrue(expanded[0] is MarkFailedStep)
        assertTrue(expanded[1] is AutoRateFixedStep)
        assertTrue(expanded[2] is NextCardStep)
    }

    @Test
    fun `compound defaults build usable learning steps`() {
        // Domyślne bloki czytają pytanie i pozwalają odsłonić odpowiedź.
        assertTrue(CompoundParams.PresentFront().speak)
        assertEquals(1, CompoundParams.SpeakPauseNext().nextPage)
        assertTrue(CompoundParams.AutoFlip().speakQuestion)
        assertFalse(CompoundParams.AutoFlip().speakAnswer)
        assertTrue(CompoundParams.ListenGrade().manualFallback)
        assertTrue(CompoundParams.GradeAfterListen().manualFallback)
        assertEquals(BranchRating.FIXED, CompoundParams.ListenGrade().onWrong.rate)
        assertEquals(1, CompoundParams.ListenGrade().onWrong.fixedRating)
        assertTrue(CompoundParams.ListenGrade().onWrong.markFailed)
    }

    @Test
    fun `flip_reveal unknown style falls back to all variant`() {
        // Nieznany klucz w backupie przywraca domyślny sposób odsłaniania.
        val expanded = CompoundExpander.expandStep(
            CompoundStep(
                params = kotlinx.serialization.json.Json.decodeFromString<CompoundParams.FlipReveal>(
                    """{"revealStyle":"garbage"}"""
                )
            )
        )
        assertEquals(3, expanded.size)
        assertTrue(expanded[0] is WaitForTapStep)
    }

    @Test
    fun `compound condition is not propagated to expanded steps`() {
        // Warunek na kroku złożonym jest
        // ignorowany; tylko gałęzie listen/grade niosą warunki correct/wrong.
        val expanded = CompoundExpander.expandStep(
            CompoundStep(
                condition = StepCondition.WRONG,
                params = CompoundParams.ListenGrade()
            )
        )
        val listen = expanded.filterIsInstance<ListenAndCheckStep>().single()
        org.junit.Assert.assertNull(listen.condition)
        val fallbackRatings = expanded.filterIsInstance<ShowRatingsStep>()
        assertTrue(fallbackRatings.all { it.condition == null })
    }

    @Test
    fun `listen_grade expands with branches and manual fallback`() {
        val step = CompoundStep(
            params = CompoundParams.ListenGrade(
                answerPage = 1,
                threshold = 70,
                onCorrect = CompoundBranch(),
                onWrong = CompoundBranch(
                    rate = BranchRating.FIXED,
                    fixedRating = 1,
                    markFailed = true
                ),
                manualFallback = true
            )
        )
        val expanded = CompoundExpander.expandStep(step)
        assertTrue(expanded.any { it is ListenAndCheckStep })
        assertTrue(expanded.any { it is FeedbackSuccessStep })
        assertTrue(expanded.any { it is FeedbackErrorStep })
        assertTrue(expanded.any { it is MarkFailedStep })
        assertTrue(expanded.any { it is ShowRatingsStep })
    }

    @Test
    fun `audio mode pacing sequence matches expected order and delays`() {
        val audioMode = com.raycoll.app.domain.seed.SEED_MODES.first { it.id == "listen-speak" }
        val steps = audioMode.steps

        val correctSteps = steps.filter { it.condition == StepCondition.CORRECT }
        assertEquals(10, correctSteps.size)
        assertTrue(correctSteps[0] is FeedbackSuccessStep)
        assertTrue(correctSteps[1] is ShowAllPagesStep)
        assertTrue(correctSteps[2] is AutoRateFromAnswerStep)
        val waitCorrect1 = correctSteps[3] as WaitStep
        assertEquals(2000L, waitCorrect1.ms)
        assertTrue(correctSteps[4] is NextCardStep)

        assertTrue(correctSteps[5] is FeedbackSuccessStep)
        assertTrue(correctSteps[6] is ShowAllPagesStep)
        assertEquals(Ratings.HARD, (correctSteps[7] as AutoRateFixedStep).rating)
        val waitCorrect2 = correctSteps[8] as WaitStep
        assertEquals(2000L, waitCorrect2.ms)
        assertTrue(correctSteps[9] is NextCardStep)

        val wrongSteps = steps.filter { it.condition == StepCondition.WRONG }
        assertEquals(10, wrongSteps.size)
        assertTrue(wrongSteps[0] is FeedbackErrorStep)
        val waitRetry = wrongSteps[1] as WaitStep
        assertEquals(800L, waitRetry.ms)
        val retryListen = wrongSteps[2] as ListenAndCheckStep
        assertEquals(1, retryListen.pageIndex)
        assertEquals(70, retryListen.successThreshold)

        assertTrue(wrongSteps[3] is FeedbackErrorStep)
        assertTrue(wrongSteps[4] is ShowAllPagesStep)
        val speakWrong = wrongSteps[5] as SpeakPageStep
        assertEquals(1, speakWrong.pageIndex)
        assertTrue(wrongSteps[6] is MarkFailedStep)
        val rateWrong = wrongSteps[7] as AutoRateFixedStep
        assertEquals(1, rateWrong.rating)
        val waitWrong = wrongSteps[8] as WaitStep
        assertEquals(2200L, waitWrong.ms)
        assertTrue(wrongSteps[9] is NextCardStep)
    }

    @Test
    fun `CompoundExpander expands ListenGrade with default audio pacing`() {
        val compound = CompoundStep(
            params = CompoundParams.ListenGrade()
        )
        val expanded = CompoundExpander.expandStep(compound)

        val correct = expanded.filter { it.condition == StepCondition.CORRECT }
        assertEquals(5, correct.size)
        assertTrue(correct[0] is FeedbackSuccessStep)
        assertTrue(correct[1] is ShowAllPagesStep)
        assertTrue(correct[2] is AutoRateFromAnswerStep)
        assertEquals(2000L, (correct[3] as WaitStep).ms)
        assertTrue(correct[4] is NextCardStep)

        val wrong = expanded.filter { it.condition == StepCondition.WRONG }
        assertEquals(7, wrong.size)
        assertTrue(wrong[0] is FeedbackErrorStep)
        assertTrue(wrong[1] is ShowAllPagesStep)
        assertEquals(1, (wrong[2] as SpeakPageStep).pageIndex)
        assertTrue(wrong[3] is MarkFailedStep)
        assertEquals(1, (wrong[4] as AutoRateFixedStep).rating)
        assertEquals(2200L, (wrong[5] as WaitStep).ms)
        assertTrue(wrong[6] is NextCardStep)
    }

    @Test
    fun `CompoundExpander expands GradeAfterListen with default audio pacing`() {
        val compound = CompoundStep(
            params = CompoundParams.GradeAfterListen()
        )
        val expanded = CompoundExpander.expandStep(compound)

        val correctWait = expanded.filter { it.condition == StepCondition.CORRECT }
            .filterIsInstance<WaitStep>()
            .first()
        assertEquals(2000L, correctWait.ms)

        val wrongWait = expanded.filter { it.condition == StepCondition.WRONG }
            .filterIsInstance<WaitStep>()
            .first()
        assertEquals(2200L, wrongWait.ms)
    }
}
