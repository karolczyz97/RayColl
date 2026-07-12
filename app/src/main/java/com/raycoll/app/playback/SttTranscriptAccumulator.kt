package com.raycoll.app.playback

data class SttTranscriptAccumulator(
    private val segments: MutableList<String> = mutableListOf(),
    private var interim: String = "",
) {
    val fullText: String get() {
        val base = segments.joinToString(" ")
        return if (interim.isNotEmpty()) "$base $interim".trim() else base
    }

    fun addFinal(segment: String) {
        if (segment.isNotBlank()) segments.add(segment.trim())
        interim = ""
    }

    fun addInterim(text: String) {
        interim = text
    }

    fun clear() {
        segments.clear()
        interim = ""
    }

    val isEmpty: Boolean get() = segments.isEmpty() && interim.isBlank()
}
