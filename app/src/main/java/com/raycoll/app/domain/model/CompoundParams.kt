package com.raycoll.app.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class CompoundStepKind {
    @SerialName("present_front") present_front,
    @SerialName("flip_reveal") flip_reveal,
    @SerialName("show_all_grade") show_all_grade,
    @SerialName("speak_pause_next") speak_pause_next,
    @SerialName("auto_flip") auto_flip,
    @SerialName("listen_grade") listen_grade,
    @SerialName("grade_after_listen") grade_after_listen,
    @SerialName("auto_pass_next") auto_pass_next,
    @SerialName("fail_next") fail_next,
}

@Serializable
data class CompoundPause(val kind: String, val ms: Int = 1000, val page: Int = 0, val multiplier: Int = 1)

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
sealed interface CompoundParams {
    val kind: CompoundStepKind
}

@Serializable @SerialName("present_front") data class PresentFrontParams(override val kind: CompoundStepKind = CompoundStepKind.present_front, val page: Int = 0, val speak: Boolean = true) : CompoundParams
@Serializable @SerialName("flip_reveal") data class FlipRevealParams(override val kind: CompoundStepKind = CompoundStepKind.flip_reveal, val revealStyle: String = "all") : CompoundParams
@Serializable @SerialName("show_all_grade") data class ShowAllGradeParams(override val kind: CompoundStepKind = CompoundStepKind.show_all_grade) : CompoundParams
@Serializable @SerialName("speak_pause_next") data class SpeakPauseNextParams(override val kind: CompoundStepKind = CompoundStepKind.speak_pause_next, val page: Int = 0, val nextPage: Int = 1, val multiplier: Int = 1) : CompoundParams
@Serializable @SerialName("auto_flip") data class AutoFlipParams(override val kind: CompoundStepKind = CompoundStepKind.auto_flip, val questionPage: Int = 0, val answerPage: Int = 1, val multiplier: Int = 1, val speakQuestion: Boolean = true, val speakAnswer: Boolean = false) : CompoundParams
@Serializable @SerialName("listen_grade") data class ListenGradeParams(override val kind: CompoundStepKind = CompoundStepKind.listen_grade, val answerPage: Int = 1, val threshold: Int = 70, val onCorrect: CompoundBranch = CompoundBranch(), val onWrong: CompoundBranch = CompoundBranch(rate = "fixed", fixedRating = 1, markFailed = true), val manualFallback: Boolean = true) : CompoundParams
@Serializable @SerialName("grade_after_listen") data class GradeAfterListenParams(override val kind: CompoundStepKind = CompoundStepKind.grade_after_listen, val onCorrect: CompoundBranch = CompoundBranch(), val onWrong: CompoundBranch = CompoundBranch(rate = "fixed", fixedRating = 1, markFailed = true), val manualFallback: Boolean = true) : CompoundParams
@Serializable @SerialName("auto_pass_next") data class AutoPassNextParams(override val kind: CompoundStepKind = CompoundStepKind.auto_pass_next, val rating: Int = 3) : CompoundParams
@Serializable @SerialName("fail_next") data class FailNextParams(override val kind: CompoundStepKind = CompoundStepKind.fail_next) : CompoundParams
