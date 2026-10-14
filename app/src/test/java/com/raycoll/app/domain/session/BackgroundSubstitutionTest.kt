package com.raycoll.app.domain.session

import com.raycoll.app.domain.model.ListenAndCheckStep
import com.raycoll.app.domain.model.NextCardStep
import com.raycoll.app.domain.model.ShowPageStep
import com.raycoll.app.domain.model.ShowRatingsStep
import com.raycoll.app.domain.model.SpeakPageStep
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
        assertEquals(false, BackgroundSubstitution.isModeHandsFreeCapable(steps))
    }

    @Test
    fun `hands-free mode with STT`() {
        val steps = listOf(ShowPageStep(), ListenAndCheckStep())
        assertEquals(false, BackgroundSubstitution.isModeHandsFreeCapable(steps))
    }

    @Test
    fun `empty steps not hands-free`() {
        assertEquals(false, BackgroundSubstitution.isModeHandsFreeCapable(emptyList()))
    }
}
