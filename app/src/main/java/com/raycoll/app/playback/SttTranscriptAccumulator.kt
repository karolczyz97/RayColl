package com.raycoll.app.playback

data class SttTranscript(val fullText: String, val partialText: String)

class SttTranscriptAccumulator {
    private val finalSegments = StringBuilder()
    private var partial = ""

    fun addPartial(text: String) { partial = text }
    fun addFinal(text: String) {
        if (finalSegments.isNotEmpty()) finalSegments.append(" ")
        finalSegments.append(text)
        partial = ""
    }
    fun current(): SttTranscript = SttTranscript(fullText = finalSegments.toString().trim(), partialText = partial)

    /** Finalne segmenty + bieżący partial — parytet z getText() w sttTranscriptAccumulator.ts. */
    fun combined(): String {
        val c = current()
        return listOf(c.fullText, c.partialText).filter { it.isNotBlank() }.joinToString(" ")
    }

    fun clear() { finalSegments.clear(); partial = "" }
}

class SttSessionTimers(
    private val softTimeoutMs: Long,
    private val hardTimeoutMs: Long,
) {
    private var lastResultTime: Long = 0
    private var startTime: Long = 0

    fun start(now: Long) { startTime = now; lastResultTime = now }
    fun onResult(now: Long) { lastResultTime = now }
    fun isSoftTimeout(now: Long): Boolean = now - lastResultTime > softTimeoutMs
    fun isHardTimeout(now: Long): Boolean = now - startTime > hardTimeoutMs
}
