package com.raycoll.app.domain.steps

import com.raycoll.app.domain.model.CompoundParams
import com.raycoll.app.domain.model.CompoundStep
import com.raycoll.app.domain.model.ShowPageStep
import com.raycoll.app.domain.model.ShowRatingsStep
import com.raycoll.app.domain.model.NextCardStep
import com.raycoll.app.domain.session.BackgroundSubstitution
import org.junit.Assert.assertEquals
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
        assertTrue(expanded[1] is com.raycoll.app.domain.model.SpeakPageStep)
    }

    @Test
    fun `flip_reveal all expands to 3 steps`() {
        val step = CompoundStep(params = CompoundParams.FlipReveal(revealStyle = "all"))
        val expanded = CompoundExpander.expandStep(step)
        assertEquals(3, expanded.size)
        assertTrue(expanded[0] is com.raycoll.app.domain.model.WaitForTapToRevealStep)
        assertTrue(expanded[1] is com.raycoll.app.domain.model.ShowAllPagesStep)
        assertTrue(expanded[2] is ShowRatingsStep)
    }

    @Test
    fun `flip_reveal next expands to 2 steps`() {
        val step = CompoundStep(params = CompoundParams.FlipReveal(revealStyle = "next"))
        val expanded = CompoundExpander.expandStep(step)
        assertEquals(2, expanded.size)
        assertTrue(expanded[0] is com.raycoll.app.domain.model.WaitForTapToRevealNextStep)
    }

    @Test
    fun `auto_pass_next expands to rate and next_card`() {
        val step = CompoundStep(params = CompoundParams.AutoPassNext(rating = 3))
        val expanded = CompoundExpander.expandStep(step)
        assertEquals(2, expanded.size)
        assertTrue(expanded[0] is com.raycoll.app.domain.model.AutoRateFixedStep)
        assertTrue(expanded[1] is NextCardStep)
    }

    @Test
    fun `fail_next expands to mark_failed, rate and next_card`() {
        val step = CompoundStep(params = CompoundParams.FailNext())
        val expanded = CompoundExpander.expandStep(step)
        assertEquals(3, expanded.size)
        assertTrue(expanded[0] is com.raycoll.app.domain.model.MarkFailedStep)
        assertTrue(expanded[1] is com.raycoll.app.domain.model.AutoRateFixedStep)
        assertTrue(expanded[2] is NextCardStep)
    }

    @Test
    fun `listen_grade expands with branches and manual fallback`() {
        val step = CompoundStep(params = CompoundParams.ListenGrade(
            answerPage = 1, threshold = 70,
            onCorrect = com.raycoll.app.domain.model.CompoundBranch(),
            onWrong = com.raycoll.app.domain.model.CompoundBranch(rate = "fixed", fixedRating = 1, markFailed = true),
            manualFallback = true,
        ))
        val expanded = CompoundExpander.expandStep(step)
        assertTrue(expanded.any { it is com.raycoll.app.domain.model.ListenAndCheckStep })
        assertTrue(expanded.any { it is com.raycoll.app.domain.model.FeedbackSuccessStep })
        assertTrue(expanded.any { it is com.raycoll.app.domain.model.FeedbackErrorStep })
        assertTrue(expanded.any { it is com.raycoll.app.domain.model.MarkFailedStep })
        assertTrue(expanded.any { it is ShowRatingsStep })
    }
}

class StepRegistryTest {

    @Test
    fun `all 17 types defined`() {
        assertEquals(17, StepRegistry.definitions.size)
    }

    @Test
    fun `clampStepNumberField clamps threshold`() {
        val result = StepRegistry.clampStepNumberField("listen_and_check", "successThreshold", 150.0)
        assertEquals(100.0, result, 0.01)
    }

    @Test
    fun `clampStepNumberField returns default for null`() {
        val result = StepRegistry.clampStepNumberField("wait", "ms", null)
        assertEquals(500.0, result, 0.01)
    }

    @Test
    fun `getStepPageIndex returns index for show_page`() {
        val step = ShowPageStep(pageIndex = 2)
        assertEquals(2, StepRegistry.getStepPageIndex(step))
    }

    @Test
    fun `getStepPageIndex returns null for show_all_pages`() {
        val step = com.raycoll.app.domain.model.ShowAllPagesStep()
        assertEquals(null, StepRegistry.getStepPageIndex(step))
    }
}

class ModeStepValidatorTest {

    @Test
    fun `empty steps gives error`() {
        val mode = com.raycoll.app.domain.model.StudyMode(id = "m1", name = "Test", steps = emptyList())
        val errors = ModeStepValidator.validate(mode, 2)
        assertTrue(errors.any { it.contains("at least one step") })
    }

    @Test
    fun `blank name gives error`() {
        val mode = com.raycoll.app.domain.model.StudyMode(id = "m1", name = "", steps = listOf(ShowPageStep()))
        val errors = ModeStepValidator.validate(mode, 2)
        assertTrue(errors.any { it.contains("name") })
    }

    @Test
    fun `page out of range gives error`() {
        val mode = com.raycoll.app.domain.model.StudyMode(id = "m1", name = "Test", steps = listOf(ShowPageStep(pageIndex = 5), ShowRatingsStep()))
        val errors = ModeStepValidator.validate(mode, 2)
        assertTrue(errors.any { it.contains("out of range") })
    }

    @Test
    fun `no terminal step gives error`() {
        val mode = com.raycoll.app.domain.model.StudyMode(id = "m1", name = "Test", steps = listOf(ShowPageStep()))
        val errors = ModeStepValidator.validate(mode, 2)
        assertTrue(errors.any { it.contains("terminal") })
    }
}

class BackgroundSubstitutionTest {

    @Test
    fun `hands-free mode without ratings or STT`() {
        val steps = listOf(ShowPageStep(), com.raycoll.app.domain.model.SpeakPageStep(), NextCardStep())
        assertTrue(BackgroundSubstitution.isModeHandsFreeCapable(steps))
    }

    @Test
    fun `not hands-free with ratings`() {
        val steps = listOf(ShowPageStep(), ShowRatingsStep())
        assertEquals(false, BackgroundSubstitution.isModeHandsFreeCapable(steps))
    }

    @Test
    fun `not hands-free with STT`() {
        val steps = listOf(ShowPageStep(), com.raycoll.app.domain.model.ListenAndCheckStep())
        assertEquals(false, BackgroundSubstitution.isModeHandsFreeCapable(steps))
    }

    @Test
    fun `empty steps not hands-free`() {
        assertEquals(false, BackgroundSubstitution.isModeHandsFreeCapable(emptyList()))
    }
}
