package com.raycoll.app.domain.steps

import com.raycoll.app.domain.model.ModeStep
import com.raycoll.app.domain.model.StudyConstants
import com.raycoll.app.domain.model.StepCondition

enum class StepCategory { DISPLAY, AUDIO, TIMING, RATING, FLOW }

data class StepFieldSpec(val kind: String, val default: Double, val min: Double = 0.0, val max: Double = Double.MAX_VALUE)

data class StepDefinition(
    val type: String,
    val category: StepCategory,
    val fields: Map<String, StepFieldSpec>,
)

object StepRegistry {
    private val PAGE_FIELD = StepFieldSpec("page", 0.0)
    private val THRESHOLD_FIELD = StepFieldSpec("number", StudyConstants.STEP_DEFAULT_THRESHOLD.toDouble(), 0.0, 100.0)
    private val MULTIPLIER_FIELD = StepFieldSpec("number", 1.0, 0.0, StudyConstants.MAX_PAUSE_MULTIPLIER.toDouble())
    private val MS_FIELD = StepFieldSpec("number", StudyConstants.STEP_DEFAULT_WAIT_MS.toDouble(), 0.0)
    private val RATING_FIELD = StepFieldSpec("number", StudyConstants.STEP_DEFAULT_RATING.toDouble(), 1.0, 4.0)

    val definitions: List<StepDefinition> = listOf(
        StepDefinition("show_page", StepCategory.DISPLAY, mapOf("pageIndex" to PAGE_FIELD)),
        StepDefinition("show_all_pages", StepCategory.DISPLAY, emptyMap()),
        StepDefinition("wait_for_tap_to_reveal_next", StepCategory.DISPLAY, emptyMap()),
        StepDefinition("wait_for_tap_to_reveal", StepCategory.DISPLAY, emptyMap()),
        StepDefinition("speak_page", StepCategory.AUDIO, mapOf("pageIndex" to PAGE_FIELD)),
        StepDefinition("speak_all_pages", StepCategory.AUDIO, emptyMap()),
        StepDefinition("listen_and_check", StepCategory.AUDIO, mapOf("pageIndex" to PAGE_FIELD, "successThreshold" to THRESHOLD_FIELD)),
        StepDefinition("feedback_success", StepCategory.AUDIO, emptyMap()),
        StepDefinition("feedback_error", StepCategory.AUDIO, emptyMap()),
        StepDefinition("dynamic_pause", StepCategory.TIMING, mapOf("nextPageIndex" to PAGE_FIELD, "pauseMultiplier" to MULTIPLIER_FIELD)),
        StepDefinition("wait", StepCategory.TIMING, mapOf("ms" to MS_FIELD)),
        StepDefinition("show_ratings", StepCategory.RATING, emptyMap()),
        StepDefinition("auto_rate_from_answer", StepCategory.RATING, emptyMap()),
        StepDefinition("auto_rate_fixed", StepCategory.RATING, mapOf("rating" to RATING_FIELD)),
        StepDefinition("mark_failed", StepCategory.RATING, emptyMap()),
        StepDefinition("wait_for_tap", StepCategory.FLOW, emptyMap()),
        StepDefinition("next_card", StepCategory.FLOW, emptyMap()),
    )

    fun clampStepNumberField(stepType: String, fieldName: String, value: Any?): Double {
        val def = definitions.find { it.type == stepType } ?: return 0.0
        val spec = def.fields[fieldName] ?: return 0.0
        val numValue = when (value) {
            is Number -> value.toDouble()
            else -> spec.default
        }
        if (numValue.isNaN()) return spec.default
        return numValue.coerceIn(spec.min, spec.max)
    }

    fun getStepPageIndex(step: ModeStep): Int? = when (step) {
        is com.raycoll.app.domain.model.ShowPageStep -> step.pageIndex
        is com.raycoll.app.domain.model.SpeakPageStep -> step.pageIndex
        is com.raycoll.app.domain.model.ListenAndCheckStep -> step.pageIndex
        is com.raycoll.app.domain.model.DynamicPauseStep -> step.nextPageIndex
        else -> null
    }

    val allTypeStrings: Set<String> = definitions.map { it.type }.toSet() + setOf("compound")
}
