package com.raycoll.app.domain.srs

import com.raycoll.app.domain.model.SrsConstants
import com.raycoll.app.domain.model.SrsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SrsEngineTest {

    @Test
    fun `existing cards match official FSRS 4_5 reference vectors`() {
        // fsrs4anki v4.11.1, fsrs4anki_scheduler.js: te same 17 wag i wzory FSRS-4.5.
        // Referencja zaokrągla D/S do 2 miejsc; zachowujemy precyzję Double aplikacji.
        val vectors = listOf(
            doubleArrayOf(5.0, 10.0, 10.0, 1.0, 6.70, 2.87),
            doubleArrayOf(5.0, 10.0, 10.0, 2.0, 5.85, 15.51),
            doubleArrayOf(5.0, 10.0, 10.0, 3.0, 5.00, 29.01),
            doubleArrayOf(5.0, 10.0, 10.0, 4.0, 4.15, 59.61),
            doubleArrayOf(6.81, 0.4, 7.0, 1.0, 8.49, 0.40),
            doubleArrayOf(9.5, 100.0, 50.0, 1.0, 10.00, 7.93),
            doubleArrayOf(9.5, 100.0, 50.0, 4.0, 8.60, 147.42)
        )
        val lastReview = SrsConstants.DAY_MS
        for (vector in vectors) {
            val (d, s, days, rating, expectedD) = vector
            val expectedS = vector[5]
            val current =
                SrsState(difficulty = d, stability = s, repetitions = 3, state = 2, lastReviewTimestamp = lastReview)
            val now = lastReview + (days * SrsConstants.DAY_MS).toLong()
            val result = calculateFsrs(current, rating.toInt(), now)
            assertEquals(expectedD, result.difficulty, 0.0051)
            assertEquals(expectedS, result.stability, 0.0051)
            assertEquals(now + (result.stability * SrsConstants.DAY_MS).toLong(), result.nextReviewTimestamp)
            if (rating == 1.0) assertTrue(result.stability <= current.stability)
        }
    }

    @Test
    fun `SrsState sets defaults`() {
        val state = SrsState()
        assertEquals(5.0, state.difficulty, 0.001)
        assertEquals(0.0, state.stability, 0.001)
        assertEquals(0, state.repetitions)
        assertEquals(0, state.state)
    }

    @Test
    fun `getCardCategory returns new for state 0`() {
        assertEquals(SrsCardCategory.NEW, getCardCategory(SrsState()))
    }

    @Test
    fun `getCardCategory returns learning for state 1`() {
        assertEquals(SrsCardCategory.LEARNING, getCardCategory(SrsState().copy(state = 1)))
    }

    @Test
    fun `getCardCategory returns learning for state 3`() {
        assertEquals(SrsCardCategory.LEARNING, getCardCategory(SrsState().copy(state = 3)))
    }

    @Test
    fun `getCardCategory returns review for state 2 with repetitions 2`() {
        assertEquals(SrsCardCategory.REVIEW, getCardCategory(SrsState().copy(state = 2, repetitions = 2)))
    }

    @Test
    fun `getCardCategory returns mastered for state 2 with repetitions 3`() {
        assertEquals(SrsCardCategory.MASTERED, getCardCategory(SrsState().copy(state = 2, repetitions = 3)))
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
        val state = SrsState()
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
            nextReviewTimestamp = now - 365 * dayMs
        )
        val result = calculateFsrs(extremeState, 4, now)
        assertTrue(result.nextReviewTimestamp in 0..Long.MAX_VALUE)
        assertTrue(result.difficulty <= 10)
        assertTrue(result.stability <= 3650)
        assertTrue(result.nextReviewTimestamp - result.lastReviewTimestamp <= 3650 * dayMs + 1000)
    }

    @Test
    fun `calculateFsrs rating 1 on new card gives state 1`() {
        val result = calculateFsrs(SrsState(), 1, System.currentTimeMillis())
        assertEquals(1, result.state)
    }

    @Test
    fun `calculateFsrs on existing card rating 1 gives relearning state 3`() {
        val existing = SrsState(
            difficulty = 5.0,
            stability = 10.0,
            repetitions = 5,
            state = 2,
            lastReviewTimestamp = System.currentTimeMillis() - 86_400_000L,
            nextReviewTimestamp = System.currentTimeMillis()
        )
        val result = calculateFsrs(existing, 1, System.currentTimeMillis())
        assertEquals(3, result.state)
    }
}
