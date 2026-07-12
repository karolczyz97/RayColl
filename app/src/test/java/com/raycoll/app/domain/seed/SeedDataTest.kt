package com.raycoll.app.domain.seed

import com.raycoll.app.domain.model.ShowPageStep
import com.raycoll.app.domain.model.ShowRatingsStep
import com.raycoll.app.domain.model.SpeakPageStep
import com.raycoll.app.domain.model.WaitForTapToRevealStep
import com.raycoll.app.domain.model.ShowAllPagesStep
import com.raycoll.app.domain.model.ListenAndCheckStep
import com.raycoll.app.domain.model.FeedbackSuccessStep
import com.raycoll.app.domain.model.FeedbackErrorStep
import com.raycoll.app.domain.model.AutoRateFromAnswerStep
import com.raycoll.app.domain.model.AutoRateFixedStep
import com.raycoll.app.domain.model.MarkFailedStep
import com.raycoll.app.domain.model.NextCardStep
import com.raycoll.app.domain.model.StepCondition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeedDataTest {

    @Test
    fun `seed modes have classic and listen-speak`() {
        val modes = SeedData.createSeedModes()
        assertEquals(2, modes.size)
        val classic = modes.find { it.id == "classic" }
        val listenSpeak = modes.find { it.id == "listen-speak" }
        assertTrue(classic != null)
        assertTrue(listenSpeak != null)
    }

    @Test
    fun `classic mode has correct steps`() {
        val modes = SeedData.createSeedModes()
        val classic = modes.find { it.id == "classic" }!!
        assertEquals(5, classic.steps.size)
        assertTrue(classic.steps[0] is ShowPageStep)
        assertTrue(classic.steps[1] is SpeakPageStep)
        assertTrue(classic.steps[2] is WaitForTapToRevealStep)
        assertTrue(classic.steps[3] is ShowAllPagesStep)
        assertTrue(classic.steps[4] is ShowRatingsStep)
    }

    @Test
    fun `listen-speak mode has STT with branches`() {
        val modes = SeedData.createSeedModes()
        val listenSpeak = modes.find { it.id == "listen-speak" }!!
        assertTrue(listenSpeak.steps.any { it is ListenAndCheckStep })
        assertTrue(listenSpeak.steps.any { it is FeedbackSuccessStep && it.condition == StepCondition.CORRECT })
        assertTrue(listenSpeak.steps.any { it is FeedbackErrorStep && it.condition == StepCondition.WRONG })
        assertTrue(listenSpeak.steps.any { it is MarkFailedStep && it.condition == StepCondition.WRONG })
        assertTrue(listenSpeak.steps.any { it is AutoRateFixedStep && it.rating == 1 && it.condition == StepCondition.WRONG })
    }

    @Test
    fun `seed modes are built-in`() {
        val modes = SeedData.createSeedModes()
        modes.forEach { assertTrue(it.isBuiltIn) }
        assertEquals("classic", modes.find { it.id == "classic" }?.builtInSourceId)
        assertEquals("listen-speak", modes.find { it.id == "listen-speak" }?.builtInSourceId)
    }

    @Test
    fun `seed groups have correct names and languages`() {
        val groups = SeedData.createSeedGroups()
        assertEquals(2, groups.size)
        val english = groups.find { it.name == "Angielski - Podstawy" }
        val spanish = groups.find { it.name == "Hiszpański - Podstawy" }
        assertTrue(english != null)
        assertTrue(spanish != null)
        assertEquals(listOf("en-US", "pl-PL"), english?.pageLanguages)
        assertEquals(listOf("es-ES", "pl-PL"), spanish?.pageLanguages)
    }

    @Test
    fun `seed groups have correct card counts`() {
        val groups = SeedData.createSeedGroups()
        assertEquals(5, groups.find { it.name == "Angielski - Podstawy" }?.cards?.size)
        assertEquals(4, groups.find { it.name == "Hiszpański - Podstawy" }?.cards?.size)
    }

    @Test
    fun `seed data creates complete store data`() {
        val data = SeedData.createSeedData()
        assertEquals(2, data.groups.size)
        assertEquals(2, data.studyModes.size)
        assertTrue(data.activityHeatmap.isEmpty())
    }
}
