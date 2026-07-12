package com.raycoll.app.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class SttTranscriptAccumulatorTest {
    @Test
    fun `accumulator builds full text from final segments`() {
        val acc = SttTranscriptAccumulator()
        acc.addFinal("hello")
        acc.addFinal("world")
        val result = acc.current()
        assertEquals("hello world", result.fullText)
    }

    @Test
    fun `accumulator sets partial without affecting final`() {
        val acc = SttTranscriptAccumulator()
        acc.addFinal("hello")
        acc.addPartial("wor")
        val result = acc.current()
        assertEquals("hello", result.fullText)
        assertEquals("wor", result.partialText)
    }

    @Test
    fun `clear resets accumulator`() {
        val acc = SttTranscriptAccumulator()
        acc.addFinal("hello")
        acc.addPartial("world")
        acc.clear()
        val result = acc.current()
        assertEquals("", result.fullText)
        assertEquals("", result.partialText)
    }
}
