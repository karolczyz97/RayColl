package com.raycoll.app.domain.seed

import com.raycoll.app.domain.model.AutoRateFixedStep
import com.raycoll.app.domain.model.DynamicPauseStep
import com.raycoll.app.domain.model.FeedbackErrorStep
import com.raycoll.app.domain.model.FeedbackSuccessStep
import com.raycoll.app.domain.model.ListenAndCheckStep
import com.raycoll.app.domain.model.MarkFailedStep
import com.raycoll.app.domain.model.NextCardStep
import com.raycoll.app.domain.model.ShowAllPagesStep
import com.raycoll.app.domain.model.ShowPageStep
import com.raycoll.app.domain.model.ShowRatingsStep
import com.raycoll.app.domain.model.SpeakPageStep
import com.raycoll.app.domain.model.StepCondition
import com.raycoll.app.domain.model.WaitForTapStep
import com.raycoll.app.domain.seed.SEED_MODES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeedDataTest {

    @Test
    fun `seed modes are the five built-in modes in their display order`() {
        assertEquals(BuiltInMode.entries.map { it.id }, SEED_MODES.map { it.id })
        assertEquals(5, SEED_MODES.size)
        assertNotNull(SEED_MODES.find { it.id == "classic" })
        assertNotNull(SEED_MODES.find { it.id == "listen-speak" })
    }

    @Test
    fun `listen mode reads each page and waits twice its length without rating`() {
        val steps = SEED_MODES.single { it.id == "listen-only" }.steps
        assertEquals(
            listOf(
                ShowPageStep(pageIndex = 0),
                SpeakPageStep(pageIndex = 0),
                DynamicPauseStep(nextPageIndex = 0, pauseMultiplier = 2.0),
                ShowPageStep(pageIndex = 1),
                SpeakPageStep(pageIndex = 1),
                DynamicPauseStep(nextPageIndex = 1, pauseMultiplier = 2.0),
                NextCardStep()
            ),
            steps
        )
        assertTrue(steps.none { it is ShowRatingsStep || it is AutoRateFixedStep })
    }

    @Test
    fun `browse mode moves on by tapping and never rates`() {
        val steps = SEED_MODES.single { it.id == "browse" }.steps
        assertEquals(2, steps.count { it is WaitForTapStep })
        assertTrue(steps.last() is NextCardStep)
        assertTrue(steps.none { it is ShowRatingsStep || it is AutoRateFixedStep })
    }

    @Test
    fun `auto-reveal mode waits, reveals the answer and asks for a rating`() {
        val steps = SEED_MODES.single { it.id == "auto-reveal" }.steps
        assertTrue(steps.any { it is DynamicPauseStep })
        assertTrue(steps.last() is ShowRatingsStep)
        assertTrue(steps.none { it is WaitForTapStep })
    }

    @Test
    fun `classic mode has correct steps`() {
        val modes = SEED_MODES
        val classic = modes.find { it.id == "classic" }!!
        assertEquals(5, classic.steps.size)
        assertTrue(classic.steps[0] is ShowPageStep)
        assertTrue(classic.steps[1] is SpeakPageStep)
        assertTrue(classic.steps[2] is WaitForTapStep)
        assertTrue(classic.steps[3] is ShowAllPagesStep)
        assertTrue(classic.steps[4] is ShowRatingsStep)
    }

    @Test
    fun `listen-speak mode has STT with branches`() {
        val modes = SEED_MODES
        val listenSpeak = modes.find { it.id == "listen-speak" }!!
        assertTrue(listenSpeak.steps.any { it is ListenAndCheckStep })
        assertTrue(listenSpeak.steps.any { it is FeedbackSuccessStep && it.condition == StepCondition.CORRECT })
        assertTrue(listenSpeak.steps.any { it is FeedbackErrorStep && it.condition == StepCondition.WRONG })
        assertTrue(listenSpeak.steps.any { it is MarkFailedStep && it.condition == StepCondition.WRONG })
        assertTrue(
            listenSpeak.steps.any {
                it is AutoRateFixedStep && it.rating == 1 &&
                    it.condition == StepCondition.WRONG
            }
        )
    }

    @Test
    fun `seed modes are built-in`() {
        val modes = SEED_MODES
        modes.forEach { assertTrue(it.isBuiltIn) }
        assertEquals("classic", modes.find { it.id == "classic" }?.builtInSourceId)
        assertEquals("listen-speak", modes.find { it.id == "listen-speak" }?.builtInSourceId)
    }

    @Test
    fun `seed groups have correct names and languages`() {
        val groups = SeedData.createSeedGroups("pl")
        assertEquals(2, groups.size)
        val english = groups.find { it.name == "Angielski – podstawy" }
        val spanish = groups.find { it.name == "Hiszpański – podstawy" }
        assertNotNull(english)
        assertNotNull(spanish)
        assertEquals(listOf("en-US", "pl-PL"), english?.pageLanguages)
        assertEquals(listOf("es-ES", "pl-PL"), spanish?.pageLanguages)
    }

    @Test
    fun `seed groups have correct card counts`() {
        val groups = SeedData.createSeedGroups("pl")
        assertEquals(5, groups.find { it.name == "Angielski – podstawy" }?.cards?.size)
        assertEquals(5, groups.find { it.name == "Hiszpański – podstawy" }?.cards?.size)
    }

    @Test
    fun `seed data creates complete store data`() {
        val data = SeedData.createSeedData("pl")
        assertEquals(2, data.groups.size)
        assertEquals(SEED_MODES, data.studyModes)
        assertTrue(data.activityHeatmap.isEmpty())
    }

    @Test
    fun `every UI language gets foreign decks with native translations`() {
        val translations = mapOf(
            "en-US" to "Good morning",
            "pl-PL" to "Dzień dobry",
            "de-DE" to "Guten Morgen",
            "es-ES" to "Buenos días",
            "it-IT" to "Buongiorno"
        )
        translations.forEach { (locale, firstTranslation) ->
            val groups = SeedData.createSeedGroups(locale)
            assertEquals(2, groups.size)
            groups.forEach { deck ->
                assertEquals(locale, deck.pageLanguages[1])
                assertTrue(deck.pageLanguages[0] != locale)
                assertEquals(5, deck.cards.size)
                assertEquals(firstTranslation, deck.cards.first().pages[1])
                assertTrue(deck.cards.all { it.srsState == com.raycoll.app.domain.model.SrsState() })
            }
        }
    }

    @Test
    fun `unsupported UI language uses English translations`() {
        val deck = SeedData.createSeedGroups("xx").first()
        assertEquals("en-US", deck.pageLanguages[1])
        assertEquals("Good morning", deck.cards.first().pages[1])
    }
}
