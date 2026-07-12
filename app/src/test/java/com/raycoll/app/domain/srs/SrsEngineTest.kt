package com.raycoll.app.domain.srs

import com.raycoll.app.domain.model.SrsState
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SrsEngineTest {

    @Test
    fun `createNewSrsState sets difficulty_5 stability_0 repetitions_0 state_0`() {
        val state = createNewSrsState()
        assertEquals(5.0, state.difficulty)
        assertEquals(0.0, state.stability)
        assertEquals(0, state.repetitions)
        assertEquals(0, state.state)
    }

    @Test
    fun `getCardCategory returns New for state_0`() {
        assertEquals(SrsCardCategory.New, getCardCategory(SrsState(state = 0)))
    }

    @Test
    fun `getCardCategory returns Learning for state_1`() {
        assertEquals(SrsCardCategory.Learning, getCardCategory(SrsState(state = 1)))
    }

    @Test
    fun `getCardCategory returns Learning for state_3 relearning`() {
        assertEquals(SrsCardCategory.Learning, getCardCategory(SrsState(state = 3)))
    }

    @Test
    fun `getCardCategory returns Review for state_2 with repetitions_2`() {
        assertEquals(SrsCardCategory.Review, getCardCategory(SrsState(state = 2, repetitions = 2)))
    }

    @Test
    fun `getCardCategory returns Mastered for state_2 with repetitions_3`() {
        assertEquals(SrsCardCategory.Mastered, getCardCategory(SrsState(state = 2, repetitions = 3)))
    }

    @Test
    fun `matchSpeech exact match case punctuation insensitive`() {
        assertEquals(100, matchSpeech("hello world", "Hello World!"))
    }

    @Test
    fun `matchSpeech partial match`() {
        val result = matchSpeech("hello", "hello world")
        assertTrue(result in 50..60, "Expected ~50 but got $result")
    }

    @Test
    fun `matchSpeech empty match`() {
        assertEquals(100, matchSpeech("", ""))
    }

    @Test
    fun `matchSpeech does not penalize extra recognized words`() {
        assertEquals(100, matchSpeech("hello world and then some", "hello world"))
    }

    @Test
    fun `mapMatchToRating 90 pct is Easy`() { assertEquals(4, mapMatchToRating(90)) }
    @Test
    fun `mapMatchToRating 70 pct is Good`() { assertEquals(3, mapMatchToRating(70)) }
    @Test
    fun `mapMatchToRating 50 pct is Hard`() { assertEquals(2, mapMatchToRating(50)) }
    @Test
    fun `mapMatchToRating 20 pct is Again`() { assertEquals(1, mapMatchToRating(20)) }

    @Test
    fun `mapMatchToRating uses STT threshold when provided`() {
        assertEquals(1, mapMatchToRating(75, 90))
        assertEquals(3, mapMatchToRating(90, 90))
        assertEquals(4, mapMatchToRating(95, 90))
    }

    @Test
    fun `calculateFsrs increments reps changes state to Review for rating 3`() {
        val state = createNewSrsState()
        val result = calculateFsrs(state, 3, System.currentTimeMillis())
        assertEquals(1, result.repetitions)
        assertTrue(result.stability > 0)
        assertTrue(result.difficulty < 5)
        assertEquals(2, result.state)
    }

    @Test
    fun `calculateFsrs handles extreme values without infinity`() {
        val dayMs = 24 * 60 * 60 * 1000L
        val now = System.currentTimeMillis()
        val extremeState = SrsState(
            difficulty = 100.0,
            stability = 1_000_000.0,
            repetitions = 999,
            state = 2,
            lastReviewTimestamp = now - 730 * dayMs,
            nextReviewTimestamp = now - 365 * dayMs,
        )
        val result = calculateFsrs(extremeState, 4, now)
        assertTrue(result.nextReviewTimestamp > 0, "next review timestamp should be positive")
        assertTrue(result.difficulty <= 10)
        assertTrue(result.stability <= 3650)
        assertTrue(result.nextReviewTimestamp - result.lastReviewTimestamp <= 3650 * dayMs + 1000)
    }

    @Test
    fun `SpeechMatcher normalizeText removes punctuation`() {
        assertEquals("hello world", normalizeText("Hello, World!"))
    }

    @Test
    fun `SpeechMatcher levenshteinDistance correct`() {
        assertEquals(3, levenshteinDistance("kitten", "sitting"))
        assertEquals(0, levenshteinDistance("test", "test"))
    }
}
