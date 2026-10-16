package com.raycoll.app.domain.srs

import com.raycoll.app.domain.model.SrsConstants
import com.raycoll.app.domain.model.SrsState
import com.raycoll.app.domain.model.SrsStates
import kotlin.math.abs
import kotlin.math.exp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SrsEngineTest {

    @Test
    fun `existing cards match FSRS-6 reference vectors`() {
        // py-fsrs 6.3.2 (Scheduler: wagi FSRS6_DEFAULT_PARAMETERS z fsrs-rs, retencja 0.9,
        // bez kroków nauki, bez fuzz), stan Review z podanymi D/S, ocena po `days` dniach.
        val vectors = listOf(
            doubleArrayOf(5.0, 10.0, 10.0, 1.0, 8.341762369296838, 1.3919869729546932),
            doubleArrayOf(5.0, 10.0, 10.0, 2.0, 6.665995369296838, 23.246875110466814),
            doubleArrayOf(5.0, 10.0, 10.0, 3.0, 4.9902283692968386, 32.02672948198672),
            doubleArrayOf(5.0, 10.0, 10.0, 4.0, 3.3144613692968385, 51.253861646812936),
            doubleArrayOf(6.81, 0.4, 7.0, 1.0, 8.936697061296838, 0.22093576169418752),
            doubleArrayOf(9.5, 100.0, 50.0, 1.0, 9.820881769296838, 3.3707083159624385),
            doubleArrayOf(9.5, 100.0, 50.0, 4.0, 9.318151669296837, 141.24860996428777)
        )
        val lastReview = SrsConstants.DAY_MS
        for (vector in vectors) {
            val (d, s, days, rating, expectedD) = vector
            val expectedS = vector[5]
            val current =
                SrsState(difficulty = d, stability = s, repetitions = 3, state = 2, lastReviewTimestamp = lastReview)
            val now = lastReview + (days * SrsConstants.DAY_MS).toLong()
            val result = calculateFsrs(current, rating.toInt(), now)
            assertRelative(expectedD, result.difficulty)
            assertRelative(expectedS, result.stability)
            assertIntervalEqualsStability(now, result)
            if (rating == 1.0) assertTrue(result.stability <= current.stability)
        }
    }

    @Test
    fun `new card review sequence matches FSRS-6 reference`() {
        // Ta sama konfiguracja py-fsrs co wyżej; każda ocena w dniu wyznaczonym przez py-fsrs.
        // Ręczna kontrola dwóch pierwszych kroków (DECAY = -0.1542, FACTOR = 0.9^(1/DECAY) - 1 = 0.98034649):
        //  1) Good na nowej: S = w2 = 2.3065; D = w4 - e^(2*w5) + 1 = 6.4133 - 5.29519603 + 1 = 2.11810397
        //  2) Good po 2 dniach: R = (1 + 0.98034649*2/2.3065)^-0.1542 = 0.90949326
        //     S = 2.3065 * (1 + e^w8 * (11 - D) * S^-w9 * (e^((1-R)*w10) - 1))
        //       = 2.3065 * (1 + 6.50258636 * 8.88189603 * 0.87002544 * 0.07470195) = 2.3065 * 4.75366674 = 10.96433234
        //     D0(4) = 6.4133 - e^(3*w5) + 1 = -4.77163070 (cel rewersji, bez przycięcia)
        //     D = w7 * D0(4) + (1 - w7) * D = 0.001 * -4.77163070 + 0.999 * 2.11810397 = 2.11121424
        val steps = listOf(
            // rating, dni od poprzedniej oceny, oczekiwane S, oczekiwane D
            doubleArrayOf(3.0, 0.0, 2.3065, 2.118103970459016),
            doubleArrayOf(3.0, 2.0, 10.964332335820698, 2.111214235785395),
            doubleArrayOf(1.0, 11.0, 1.5383372358832694, 7.392238132342694),
            doubleArrayOf(3.0, 2.0, 4.8762601340710345, 7.38007426350719),
            doubleArrayOf(2.0, 5.0, 9.347924991129831, 8.24599957687359),
            doubleArrayOf(4.0, 9.0, 26.76785968026755, 7.6451227411813)
        )
        var state = SrsState()
        var now = 1000 * SrsConstants.DAY_MS
        for ((rating, days, expectedS, expectedD) in steps) {
            now += (days * SrsConstants.DAY_MS).toLong()
            state = calculateFsrs(state, rating.toInt(), now)
            assertRelative(expectedS, state.stability)
            assertRelative(expectedD, state.difficulty)
            assertIntervalEqualsStability(now, state)
        }
        assertEquals(steps.size, state.repetitions)
    }

    @Test
    fun `lapse caps stability below previous stability`() {
        val w = SrsConstants.W
        val current =
            SrsState(difficulty = 1.0, stability = 3000.0, repetitions = 9, state = 2, lastReviewTimestamp = 1L)
        val result = calculateFsrs(current, 1, 1L + SrsConstants.DAY_MS)
        assertTrue(result.stability <= current.stability / exp(w[17] * w[18]) + 1e-9)
    }

    @Test
    fun `higher rating never gives lower stability or higher difficulty`() {
        val current = SrsState(difficulty = 5.0, stability = 10.0, repetitions = 4, state = 2, lastReviewTimestamp = 1L)
        val now = 1L + 10 * SrsConstants.DAY_MS
        val results = (1..4).map { calculateFsrs(current, it, now) }
        results.zipWithNext().forEach { (lower, higher) ->
            assertTrue(higher.stability > lower.stability)
            assertTrue(higher.difficulty < lower.difficulty)
        }
    }

    private fun assertRelative(expected: Double, actual: Double) {
        assertEquals(expected, actual, abs(expected) * 1e-6)
    }

    // Przy retencji 0.9 odstęp FSRS równa się stabilności (z dokładnością do zaokrąglenia ms).
    private fun assertIntervalEqualsStability(now: Long, result: SrsState) {
        val expected = now + (result.stability * SrsConstants.DAY_MS).toLong()
        assertTrue(abs(expected - result.nextReviewTimestamp) <= 1)
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
    fun `mapMatchToRating with default threshold 70`() {
        assertEquals(4, mapMatchToRating(90, 70))
        assertEquals(4, mapMatchToRating(85, 70))
        assertEquals(3, mapMatchToRating(70, 70))
        assertEquals(1, mapMatchToRating(50, 70))
        assertEquals(1, mapMatchToRating(20, 70))
    }

    @Test
    fun `mapMatchToRating with threshold`() {
        assertEquals(1, mapMatchToRating(75, 90))
        assertEquals(3, mapMatchToRating(90, 90))
        assertEquals(4, mapMatchToRating(95, 90))
    }

    @Test
    fun `mapMatchToRating handles extreme thresholds`() {
        assertEquals(4, mapMatchToRating(100, 100))
        assertEquals(1, mapMatchToRating(99, 100))
        assertEquals(4, mapMatchToRating(100, 250))
        assertEquals(3, mapMatchToRating(0, 0))
        assertEquals(4, mapMatchToRating(50, -10))
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

    @Test
    fun `getCardCategory maps every srs state`() {
        assertEquals(SrsCardCategory.NEW, getCardCategory(SrsState(state = SrsStates.NEW)))
        assertEquals(SrsCardCategory.LEARNING, getCardCategory(SrsState(state = SrsStates.LEARNING)))
        assertEquals(SrsCardCategory.LEARNING, getCardCategory(SrsState(state = SrsStates.RELEARNING)))
        assertEquals(SrsCardCategory.REVIEW, getCardCategory(SrsState(state = SrsStates.REVIEW, repetitions = 1)))
        assertEquals(SrsCardCategory.MASTERED, getCardCategory(SrsState(state = SrsStates.REVIEW, repetitions = 3)))
        assertEquals(SrsCardCategory.NEW, getCardCategory(SrsState(state = 42)))
    }
}
