package com.raycoll.app.domain.session

import com.raycoll.app.domain.model.ListenAndCheckStep
import com.raycoll.app.domain.model.NextCardStep
import com.raycoll.app.domain.model.ShowPageStep
import com.raycoll.app.domain.model.ShowRatingsStep
import com.raycoll.app.domain.model.SpeakPageStep
import com.raycoll.app.domain.model.StudyConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundSubstitutionTest {

    @Test
    fun `hands-free mode without ratings or STT`() {
        val steps = listOf(ShowPageStep(), SpeakPageStep(), NextCardStep())
        assertTrue(BackgroundSubstitution.isModeHandsFreeCapable(steps))
    }

    @Test
    fun `hands-free mode with ratings`() {
        val steps = listOf(ShowPageStep(), ShowRatingsStep())
        org.junit.Assert.assertFalse(BackgroundSubstitution.isModeHandsFreeCapable(steps))
    }

    @Test
    fun `hands-free mode with STT`() {
        val steps = listOf(ShowPageStep(), ListenAndCheckStep())
        org.junit.Assert.assertFalse(BackgroundSubstitution.isModeHandsFreeCapable(steps))
    }

    @Test
    fun `empty steps not hands-free`() {
        org.junit.Assert.assertFalse(BackgroundSubstitution.isModeHandsFreeCapable(emptyList()))
    }

    @Test
    fun `background wait is proportional to text and clamped`() {
        val perChar = StudyConstants.BG_WAIT_MS_PER_CHAR.toLong()
        assertEquals(StudyConstants.BG_WAIT_SUBSTITUTE_MIN, BackgroundSubstitution.calculateBgWaitMs(emptyList()))
        assertEquals(StudyConstants.BG_WAIT_SUBSTITUTE_MIN, BackgroundSubstitution.calculateBgWaitMs(listOf("", "")))
        // 20 + 30 znaków = 50 × stawka, między granicami
        val mid = BackgroundSubstitution.calculateBgWaitMs(listOf("a".repeat(20), "b".repeat(30)))
        assertEquals(50 * perChar, mid)
        assertTrue(mid in StudyConstants.BG_WAIT_SUBSTITUTE_MIN..StudyConstants.BG_WAIT_SUBSTITUTE_MAX)
        assertEquals(
            StudyConstants.BG_WAIT_SUBSTITUTE_MAX,
            BackgroundSubstitution.calculateBgWaitMs(listOf("x".repeat(10_000)))
        )
    }
}
