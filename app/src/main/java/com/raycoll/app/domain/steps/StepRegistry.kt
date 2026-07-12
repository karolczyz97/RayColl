package com.raycoll.app.domain.steps

import com.raycoll.app.domain.model.*

object StepRegistry {
    val atomicStepTypes: List<String> = listOf(
        "show_page", "show_all_pages", "wait_for_tap_to_reveal_next", "wait_for_tap_to_reveal",
        "show_ratings", "speak_page", "speak_all_pages", "dynamic_pause", "wait", "listen_and_check",
        "feedback_success", "feedback_error", "auto_rate_from_answer", "auto_rate_fixed",
        "mark_failed", "wait_for_tap", "next_card",
    )

    fun clampStepNumberField(stepType: String, param: String, raw: Int): Int = when (stepType to param) {
        "dynamic_pause" to "pauseMultiplier" -> raw.coerceIn(0, 5)
        "auto_rate_fixed" to "rating" -> raw.coerceIn(1, 4)
        "wait" to "ms" -> raw.coerceAtLeast(0)
        "listen_and_check" to "successThreshold" -> raw.coerceIn(0, 100)
        else -> raw
    }

    fun getStepPageIndex(step: AtomicStep): Int? = when (step) {
        is ShowPage -> step.pageIndex
        is SpeakPage -> step.pageIndex
        is ListenAndCheck -> step.pageIndex
        is DynamicPause -> step.nextPageIndex
        else -> null
    }

    fun isAtomicStepType(type: String): Boolean = type in atomicStepTypes

    fun getStepDefault(stepType: String): Int = when (stepType) {
        "show_page" -> 0
        "speak_page" -> 0
        "dynamic_pause" -> 1
        "wait" -> 500
        "listen_and_check" -> 70
        "auto_rate_fixed" -> 3
        else -> 0
    }
}
