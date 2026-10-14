package com.raycoll.app.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechChunksTest {
    @Test
    fun `long text keeps all content and prefers sentence boundaries`() {
        val text = "First sentence. Second sentence is longer. Third sentence."
        val chunks = speechChunks(text, 30)
        assertEquals(text, chunks.joinToString(""))
        assertTrue(chunks.all { it.isNotEmpty() && it.length <= 30 })
        assertEquals("First sentence.", chunks.first())
    }

    @Test
    fun `long words and emoji stay within the synthesis limit without broken surrogates`() {
        val text = "a".repeat(3_999) + "😀" + "b".repeat(5_000)
        val chunks = speechChunks(text, 4_000)
        assertEquals(text, chunks.joinToString(""))
        assertTrue(chunks.all { it.length <= 4_000 })
        assertFalse(chunks.any { it.last().isHighSurrogate() || it.first().isLowSurrogate() })
        assertTrue(speechChunks("", 4_000).isEmpty())
    }
}
