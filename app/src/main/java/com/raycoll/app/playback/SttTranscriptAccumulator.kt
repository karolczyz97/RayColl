package com.raycoll.app.playback

/** Składa transkrypcję z finalnych fragmentów i bieżącego (niedokończonego) fragmentu. */
class SttTranscriptAccumulator {
    private val finalSegments = mutableListOf<String>()
    private var partial = ""

    fun addPartial(text: String) {
        partial = text
    }

    fun addFinal(text: String) {
        finalSegments += text
        partial = ""
    }

    /** Wszystko, co usłyszano: finalne fragmenty + bieżący, bez pustych. */
    fun combined(): String = (finalSegments + partial).filter { it.isNotBlank() }.joinToString(" ") { it.trim() }
}
