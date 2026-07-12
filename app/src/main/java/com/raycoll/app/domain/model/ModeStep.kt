package com.raycoll.app.domain.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@Serializable
sealed interface ModeStep {
    val condition: StepCondition?
}

@Serializable
enum class StepCondition {
    @SerialName("correct") CORRECT,
    @SerialName("wrong") WRONG,
}

// --- 17 Atomic Steps ---

@Serializable @SerialName("show_page") data class ShowPageStep(override val condition: StepCondition? = null, val pageIndex: Int = 0) : ModeStep
@Serializable @SerialName("show_all_pages") data class ShowAllPagesStep(override val condition: StepCondition? = null) : ModeStep
@Serializable @SerialName("wait_for_tap_to_reveal_next") data class WaitForTapToRevealNextStep(override val condition: StepCondition? = null) : ModeStep
@Serializable @SerialName("wait_for_tap_to_reveal") data class WaitForTapToRevealStep(override val condition: StepCondition? = null) : ModeStep
@Serializable @SerialName("show_ratings") data class ShowRatingsStep(override val condition: StepCondition? = null) : ModeStep
@Serializable @SerialName("speak_page") data class SpeakPageStep(override val condition: StepCondition? = null, val pageIndex: Int = 0) : ModeStep
@Serializable @SerialName("speak_all_pages") data class SpeakAllPagesStep(override val condition: StepCondition? = null) : ModeStep
@Serializable @SerialName("dynamic_pause") data class DynamicPauseStep(override val condition: StepCondition? = null, val nextPageIndex: Int = 0, val pauseMultiplier: Double = 1.0) : ModeStep
@Serializable @SerialName("wait") data class WaitStep(override val condition: StepCondition? = null, val ms: Long = 500) : ModeStep
@Serializable @SerialName("listen_and_check") data class ListenAndCheckStep(override val condition: StepCondition? = null, val pageIndex: Int = 0, val successThreshold: Int = 70) : ModeStep
@Serializable @SerialName("feedback_success") data class FeedbackSuccessStep(override val condition: StepCondition? = null) : ModeStep
@Serializable @SerialName("feedback_error") data class FeedbackErrorStep(override val condition: StepCondition? = null) : ModeStep
@Serializable @SerialName("auto_rate_from_answer") data class AutoRateFromAnswerStep(override val condition: StepCondition? = null) : ModeStep
@Serializable @SerialName("auto_rate_fixed") data class AutoRateFixedStep(override val condition: StepCondition? = null, val rating: Int = 3) : ModeStep
@Serializable @SerialName("mark_failed") data class MarkFailedStep(override val condition: StepCondition? = null) : ModeStep
@Serializable @SerialName("wait_for_tap") data class WaitForTapStep(override val condition: StepCondition? = null) : ModeStep
@Serializable @SerialName("next_card") data class NextCardStep(override val condition: StepCondition? = null) : ModeStep

// --- Compound Step ---

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("kind")
sealed interface CompoundParams {
    @Serializable @SerialName("present_front") data class PresentFront(val page: Int = 0, val speak: Boolean = false) : CompoundParams
    @Serializable @SerialName("flip_reveal") data class FlipReveal(val revealStyle: String = "all") : CompoundParams
    @Serializable @SerialName("show_all_grade") data class ShowAllGrade(val dummy: Int = 0) : CompoundParams
    @Serializable @SerialName("speak_pause_next") data class SpeakPauseNext(val page: Int = 0, val nextPage: Int = 0, val multiplier: Double = 1.0) : CompoundParams
    @Serializable @SerialName("auto_flip") data class AutoFlip(val questionPage: Int = 0, val answerPage: Int = 1, val multiplier: Double = 1.0, val speakQuestion: Boolean = false, val speakAnswer: Boolean = false) : CompoundParams
    @Serializable @SerialName("listen_grade") data class ListenGrade(val answerPage: Int = 1, val threshold: Int = 70, val onCorrect: CompoundBranch = CompoundBranch(), val onWrong: CompoundBranch = CompoundBranch(rate = "fixed", fixedRating = 1, markFailed = true), val manualFallback: Boolean = false) : CompoundParams
    @Serializable @SerialName("grade_after_listen") data class GradeAfterListen(val onCorrect: CompoundBranch = CompoundBranch(), val onWrong: CompoundBranch = CompoundBranch(rate = "fixed", fixedRating = 1, markFailed = true), val manualFallback: Boolean = false) : CompoundParams
    @Serializable @SerialName("auto_pass_next") data class AutoPassNext(val rating: Int = 3) : CompoundParams
    @Serializable @SerialName("fail_next") data class FailNext(val dummy: Int = 0) : CompoundParams
}

@Serializable
data class CompoundBranch(
    val feedback: Boolean = true,
    val speakPage: Int? = null,
    val pause: CompoundPause? = null,
    val revealAll: Boolean = true,
    val markFailed: Boolean = false,
    val rate: String = "fromAnswer",
    val fixedRating: Int = 3,
    val advance: Boolean = true,
)

@Serializable
sealed interface CompoundPause {
    @Serializable @SerialName("fixed") data class Fixed(val ms: Long = 500) : CompoundPause
    @Serializable @SerialName("dynamic") data class Dynamic(val page: Int = 0, val multiplier: Double = 1.0) : CompoundPause
}

@Serializable
@SerialName("compound")
data class CompoundStep(
    override val condition: StepCondition? = null,
    val version: Int = 1,
    val params: CompoundParams,
) : ModeStep
