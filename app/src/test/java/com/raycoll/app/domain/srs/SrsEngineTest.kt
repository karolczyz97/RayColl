package com.raycoll.app.domain.srs

import com.raycoll.app.domain.model.SrsState
import com.raycoll.app.domain.model.createNewSrsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SrsEngineTest {

    @Test
    fun `createNewSrsState sets defaults`() {
        val state = createNewSrsState()
        assertEquals(5.0, state.difficulty, 0.001)
        assertEquals(0.0, state.stability, 0.001)
        assertEquals(0, state.repetitions)
        assertEquals(0, state.state)
    }

    @Test
    fun `getCardCategory returns new for state 0`() {
        assertEquals(SrsCardCategory.NEW, getCardCategory(createNewSrsState()))
    }

    @Test
    fun `getCardCategory returns learning for state 1`() {
        assertEquals(SrsCardCategory.LEARNING, getCardCategory(createNewSrsState().copy(state = 1)))
    }

    @Test
    fun `getCardCategory returns learning for state 3`() {
        assertEquals(SrsCardCategory.LEARNING, getCardCategory(createNewSrsState().copy(state = 3)))
    }

    @Test
    fun `getCardCategory returns review for state 2 with repetitions 2`() {
        assertEquals(SrsCardCategory.REVIEW, getCardCategory(createNewSrsState().copy(state = 2, repetitions = 2)))
    }

    @Test
    fun `getCardCategory returns mastered for state 2 with repetitions 3`() {
        assertEquals(SrsCardCategory.MASTERED, getCardCategory(createNewSrsState().copy(state = 2, repetitions = 3)))
    }

    @Test
    fun `matchSpeech exact match case insensitive`() {
        assertEquals(100, matchSpeech("hello world", "Hello World!"))
    }

    @Test
    fun `matchSpeech partial match`() {
        assertEquals(50, matchSpeech("hello", "hello world"))
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
    fun `mapMatchToRating 90 percent gives 4`() {
        assertEquals(4, mapMatchToRating(90))
    }

    @Test
    fun `mapMatchToRating 70 percent gives 3`() {
        assertEquals(3, mapMatchToRating(70))
    }

    @Test
    fun `mapMatchToRating 50 percent gives 2`() {
        assertEquals(2, mapMatchToRating(50))
    }

    @Test
    fun `mapMatchToRating 20 percent gives 1`() {
        assertEquals(1, mapMatchToRating(20))
    }

    @Test
    fun `mapMatchToRating with threshold`() {
        assertEquals(1, mapMatchToRating(75, 90))
        assertEquals(3, mapMatchToRating(90, 90))
        assertEquals(4, mapMatchToRating(95, 90))
    }

    @Test
    fun `calculateFsrs increments repetitions and changes state`() {
        val state = createNewSrsState()
        val result = calculateFsrs(state, 3, System.currentTimeMillis())
        assertEquals(1, result.repetitions)
        assertTrue(result.stability > 0)
        assertTrue(result.difficulty < 5)
        assertEquals(2, result.state)
    }

    @Test
    fun `calculateFsrs handles extreme values without infinity`() {
        val dayMs = 86_400_000L
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
        assertTrue(result.nextReviewTimestamp in 0..Long.MAX_VALUE)
        assertTrue(result.difficulty <= 10)
        assertTrue(result.stability <= 3650)
        assertTrue(result.nextReviewTimestamp - result.lastReviewTimestamp <= 3650 * dayMs + 1000)
    }

    @Test
    fun `calculateFsrs rating 1 on new card gives state 1`() {
        val result = calculateFsrs(createNewSrsState(), 1, System.currentTimeMillis())
        assertEquals(1, result.state)
    }

    @Test
    fun `calculateFsrs on existing card rating 1 gives relearning state 3`() {
        val existing = SrsState(
            difficulty = 5.0, stability = 10.0, repetitions = 5, state = 2,
            lastReviewTimestamp = System.currentTimeMillis() - 86_400_000L,
            nextReviewTimestamp = System.currentTimeMillis(),
        )
        val result = calculateFsrs(existing, 1, System.currentTimeMillis())
        assertEquals(3, result.state)
    }
}
