package com.raycoll.app.domain.normalize

import com.raycoll.app.domain.model.BranchRating
import com.raycoll.app.domain.model.CardOrder
import com.raycoll.app.domain.model.CompoundBranch
import com.raycoll.app.domain.model.CompoundParams
import com.raycoll.app.domain.model.CompoundPause
import com.raycoll.app.domain.model.CompoundStep
import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.domain.model.SrsState
import com.raycoll.app.domain.model.StoreData
import com.raycoll.app.domain.model.StudyFilter
import com.raycoll.app.domain.model.StudyMode
import com.raycoll.app.domain.model.WaitStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DataNormalizerTest {

    @Test
    fun `preserves a mode with an empty step list`() {
        val mode = StudyMode(id = "custom-broken", name = "Broken", steps = emptyList())
        val result = normalizeStudyMode(mode)
        val broken = result
        assertEquals(0, broken.steps.size)
    }

    @Test
    fun `preserves edited built-in mode name and properties`() {
        val mode = StudyMode(
            id = "classic",
            builtInSourceId = "classic",
            isBuiltIn = true,
            name = "Edited Classic",
            steps = listOf(WaitStep(ms = 250)),
            updatedAt = 1
        )
        val result = normalizeStudyMode(mode)
        assertTrue(result.isBuiltIn)
        assertEquals("classic", result.builtInSourceId)
        assertEquals("Edited Classic", result.name)
        assertEquals(1, result.steps.size)
    }

    @Test
    fun `padPageMetadata fills to count`() {
        val (names, langs) = padPageMetadata(listOf("A"), listOf("en-US"), 3)
        assertEquals(listOf("A", "Page 2", "Page 3"), names)
        assertEquals(listOf("en-US", "en-US", "en-US"), langs)
    }

    @Test
    fun `normalizes compound listen_grade branches (speakPage, rating, pause, rate)`() {
        val corrupt = CompoundStep(
            params = CompoundParams.ListenGrade(
                answerPage = -3,
                threshold = 250,
                onCorrect = CompoundBranch(
                    speakPage = -5,
                    fixedRating = 99,
                    rate = BranchRating.FROM_ANSWER,
                    pause = CompoundPause.Dynamic(page = -2, multiplier = 42.0)
                ),
                onWrong = CompoundBranch(
                    speakPage = 7,
                    fixedRating = 0,
                    rate = BranchRating.FIXED,
                    pause = CompoundPause.Fixed(ms = -100)
                )
            )
        )
        val mode = StudyMode(id = "m-compound", name = "Compound", steps = listOf(corrupt))
        val result = normalizeStudyMode(mode)
        val params = (result.steps[0] as CompoundStep).params as CompoundParams.ListenGrade

        assertEquals(0, params.answerPage) // page clamp >= 0
        assertEquals(100, params.threshold) // 0..100
        // correct branch
        assertEquals(0, params.onCorrect.speakPage) // -5 → 0
        assertEquals(4, params.onCorrect.fixedRating) // 99 → 4
        assertEquals(BranchRating.FROM_ANSWER, params.onCorrect.rate)
        assertEquals(0, (params.onCorrect.pause as CompoundPause.Dynamic).page)
        assertEquals(5.0, params.onCorrect.pause.multiplier, 0.0)
        // wrong branch
        assertEquals(1, params.onWrong.fixedRating) // 0 → 1
        assertEquals(BranchRating.FIXED, params.onWrong.rate)
        assertEquals(0L, (params.onWrong.pause as CompoundPause.Fixed).ms) // -100 → 0
    }
}
