package com.raycoll.app.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class SttTranscriptAccumulatorTest {
    @Test
    fun `final segments combine into a full transcript`() {
        val acc = SttTranscriptAccumulator()
        acc.addFinal("hello")
        acc.addFinal("world")
        assertEquals("hello world", acc.combined())
    }

    @Test
    fun `latest partial replaces the previous partial and preserves final segments`() {
        val acc = SttTranscriptAccumulator()
        acc.addFinal("hello")
        acc.addPartial("wo")
        acc.addPartial("world")
        assertEquals("hello world", acc.combined())
    }

    @Test
    fun `finalizing a partial does not duplicate it`() {
        val acc = SttTranscriptAccumulator()
        acc.addPartial("hello")
        acc.addFinal("hello")
        acc.addPartial("world")
        assertEquals("hello world", acc.combined())
        assertEquals("", SttTranscriptAccumulator().combined())
    }
}
