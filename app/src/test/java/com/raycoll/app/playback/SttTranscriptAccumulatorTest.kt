package com.raycoll.app.playback

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SttTranscriptAccumulatorTest {
    @Test
    fun `accumulator builds full text from final segments`() {
        val a = SttTranscriptAccumulator()
        a.addFinal("hello")
        a.addFinal("world")
        assertEquals("hello world", a.fullText)
    }

    @Test
    fun `accumulator includes interim text`() {
        val a = SttTranscriptAccumulator()
        a.addInterim("hello wor")
        assertEquals("hello wor", a.fullText)
    }

    @Test
    fun `accumulator replaces interim with final`() {
        val a = SttTranscriptAccumulator()
        a.addInterim("hel")
        a.addFinal("hello")
        assertEquals("hello", a.fullText)
    }

    @Test
    fun `accumulator clear resets everything`() {
        val a = SttTranscriptAccumulator()
        a.addFinal("test")
        a.addInterim("par")
        a.clear()
        assertTrue(a.isEmpty)
        assertEquals("", a.fullText)
    }

    @Test
    fun `accumulator ignores blank segments`() {
        val a = SttTranscriptAccumulator()
        a.addFinal("  ")
        a.addFinal("hello")
        assertEquals("hello", a.fullText)
    }

    @Test
    fun `accumulator isEmpty when empty`() {
        assertTrue(SttTranscriptAccumulator().isEmpty)
    }

    @Test
    fun `accumulator isEmpty false when has segments`() {
        val a = SttTranscriptAccumulator()
        a.addFinal("test")
        assertFalse(a.isEmpty)
    }
}
