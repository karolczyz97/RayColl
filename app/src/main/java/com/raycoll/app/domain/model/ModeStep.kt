package com.raycoll.app.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface ModeStep

@Serializable
sealed interface AtomicStep : ModeStep {
    val condition: StepCondition?
}

enum class StepCondition { correct, wrong }

// 17 atomic step types
@Serializable @SerialName("show_page") data class ShowPage(val pageIndex: Int = 0, override val condition: StepCondition? = null) : AtomicStep
@Serializable @SerialName("show_all_pages") data class ShowAllPages(override val condition: StepCondition? = null) : AtomicStep
@Serializable @SerialName("wait_for_tap_to_reveal_next") data class WaitForTapToRevealNext(override val condition: StepCondition? = null) : AtomicStep
@Serializable @SerialName("wait_for_tap_to_reveal") data class WaitForTapToReveal(override val condition: StepCondition? = null) : AtomicStep
@Serializable @SerialName("show_ratings") data class ShowRatings(override val condition: StepCondition? = null) : AtomicStep
@Serializable @SerialName("speak_page") data class SpeakPage(val pageIndex: Int = 0, override val condition: StepCondition? = null) : AtomicStep
@Serializable @SerialName("speak_all_pages") data class SpeakAllPages(override val condition: StepCondition? = null) : AtomicStep
@Serializable @SerialName("dynamic_pause") data class DynamicPause(val nextPageIndex: Int = 0, val pauseMultiplier: Int = 1, override val condition: StepCondition? = null) : AtomicStep
@Serializable @SerialName("wait") data class Wait(val ms: Int = 500, override val condition: StepCondition? = null) : AtomicStep
@Serializable @SerialName("listen_and_check") data class ListenAndCheck(val pageIndex: Int = 0, val successThreshold: Int = 70, override val condition: StepCondition? = null) : AtomicStep
@Serializable @SerialName("feedback_success") data class FeedbackSuccess(override val condition: StepCondition? = null) : AtomicStep
@Serializable @SerialName("feedback_error") data class FeedbackError(override val condition: StepCondition? = null) : AtomicStep
@Serializable @SerialName("auto_rate_from_answer") data class AutoRateFromAnswer(override val condition: StepCondition? = null) : AtomicStep
@Serializable @SerialName("auto_rate_fixed") data class AutoRateFixed(val rating: Int = 3, override val condition: StepCondition? = null) : AtomicStep
@Serializable @SerialName("mark_failed") data class MarkFailed(override val condition: StepCondition? = null) : AtomicStep
@Serializable @SerialName("wait_for_tap") data class WaitForTap(override val condition: StepCondition? = null) : AtomicStep
@Serializable @SerialName("next_card") data class NextCard(override val condition: StepCondition? = null) : AtomicStep

// Compound step
@Serializable @SerialName("compound") data class CompoundStep(val version: Int = 1, val params: CompoundParams) : ModeStep
