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
    @SerialName("correct")
    CORRECT,

    @SerialName("wrong")
    WRONG
}

// --- kroki atomowe (to, co faktycznie wykonuje silnik sesji) ---

@Serializable
@SerialName("show_page")
data class ShowPageStep(override val condition: StepCondition? = null, val pageIndex: Int = 0) : ModeStep

@Serializable
@SerialName("show_all_pages")
data class ShowAllPagesStep(override val condition: StepCondition? = null) : ModeStep

@Serializable
@SerialName("wait_for_tap_to_reveal_next")
data class WaitForTapToRevealNextStep(override val condition: StepCondition? = null) : ModeStep

@Serializable
@SerialName("wait_for_tap_to_reveal")
data class WaitForTapToRevealStep(override val condition: StepCondition? = null) : ModeStep

@Serializable
@SerialName("show_ratings")
data class ShowRatingsStep(override val condition: StepCondition? = null) : ModeStep

@Serializable
@SerialName("speak_page")
data class SpeakPageStep(override val condition: StepCondition? = null, val pageIndex: Int = 0) : ModeStep

@Serializable
@SerialName("speak_all_pages")
data class SpeakAllPagesStep(override val condition: StepCondition? = null) : ModeStep

@Serializable
@SerialName("dynamic_pause")
data class DynamicPauseStep(
    override val condition: StepCondition? = null,
    val nextPageIndex: Int = 0,
    val pauseMultiplier: Double = 1.0
) : ModeStep

@Serializable
@SerialName("wait")
data class WaitStep(override val condition: StepCondition? = null, val ms: Long = StudyConstants.STEP_DEFAULT_WAIT_MS) :
    ModeStep

@Serializable
@SerialName("listen_and_check")
data class ListenAndCheckStep(
    override val condition: StepCondition? = null,
    val pageIndex: Int = 0,
    val successThreshold: Int = StudyConstants.STEP_DEFAULT_THRESHOLD
) : ModeStep

@Serializable
@SerialName("feedback_success")
data class FeedbackSuccessStep(override val condition: StepCondition? = null) : ModeStep

@Serializable
@SerialName("feedback_error")
data class FeedbackErrorStep(override val condition: StepCondition? = null) : ModeStep

@Serializable
@SerialName("auto_rate_from_answer")
data class AutoRateFromAnswerStep(override val condition: StepCondition? = null) : ModeStep

@Serializable
@SerialName("auto_rate_fixed")
data class AutoRateFixedStep(override val condition: StepCondition? = null, val rating: Int = Ratings.GOOD) : ModeStep

@Serializable
@SerialName("mark_failed")
data class MarkFailedStep(override val condition: StepCondition? = null) : ModeStep

@Serializable
@SerialName("wait_for_tap")
data class WaitForTapStep(override val condition: StepCondition? = null) : ModeStep

@Serializable
@SerialName("next_card")
data class NextCardStep(override val condition: StepCondition? = null) : ModeStep

// --- krok złożony: edytowalny blok, rozwijany przed sesją na kroki atomowe ---

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("kind")
sealed interface CompoundParams {
    // Wartości domyślne służą edytorowi i konstruowaniu w kodzie; backup i Firestore są
    // walidowane ściśle (wszystkie pola muszą być obecne).
    @Serializable
    @SerialName("present_front")
    data class PresentFront(val page: Int = 0, val speak: Boolean = true) : CompoundParams

    @Serializable
    @SerialName("flip_reveal")
    data class FlipReveal(val revealStyle: RevealStyle = RevealStyle.ALL) : CompoundParams

    @Serializable
    @SerialName("show_all_grade")
    data object ShowAllGrade : CompoundParams

    @Serializable
    @SerialName("speak_pause_next")
    data class SpeakPauseNext(val page: Int = 0, val nextPage: Int = 1, val multiplier: Double = 1.0) : CompoundParams

    @Serializable
    @SerialName("auto_flip")
    data class AutoFlip(
        val questionPage: Int = 0,
        val answerPage: Int = 1,
        val multiplier: Double = 1.0,
        val speakQuestion: Boolean = true,
        val speakAnswer: Boolean = false
    ) : CompoundParams

    @Serializable
    @SerialName("listen_grade")
    data class ListenGrade(
        val answerPage: Int = 1,
        val threshold: Int = StudyConstants.STEP_DEFAULT_THRESHOLD,
        val onCorrect: CompoundBranch = CompoundBranch.DEFAULT_CORRECT,
        val onWrong: CompoundBranch = CompoundBranch.DEFAULT_WRONG,
        val manualFallback: Boolean = true
    ) : CompoundParams

    @Serializable
    @SerialName("grade_after_listen")
    data class GradeAfterListen(
        val onCorrect: CompoundBranch = CompoundBranch.DEFAULT_CORRECT,
        val onWrong: CompoundBranch = CompoundBranch.DEFAULT_WRONG,
        val manualFallback: Boolean = true
    ) : CompoundParams

    @Serializable
    @SerialName("auto_pass_next")
    data class AutoPassNext(val rating: Int = Ratings.GOOD) : CompoundParams

    @Serializable
    @SerialName("fail_next")
    data object FailNext : CompoundParams
}

/** Co robi gałąź "poprawna"/"błędna" kroku z nasłuchem. */
@Serializable
data class CompoundBranch(
    val feedback: Boolean = true,
    val speakPage: Int? = null,
    val pause: CompoundPause? = null,
    val revealAll: Boolean = true,
    val markFailed: Boolean = false,
    val rate: BranchRating = BranchRating.FROM_ANSWER,
    val fixedRating: Int = Ratings.GOOD,
    val advance: Boolean = true
) {
    companion object {
        val DEFAULT_CORRECT = CompoundBranch(pause = CompoundPause.Fixed(StudyConstants.PAUSE_AFTER_CORRECT_MS))
        val DEFAULT_WRONG = CompoundBranch(
            speakPage = 1,
            pause = CompoundPause.Fixed(StudyConstants.PAUSE_AFTER_WRONG_MS),
            rate = BranchRating.FIXED,
            fixedRating = Ratings.AGAIN,
            markFailed = true
        )
    }
}

/** Sposób oceny karty w gałęzi kroku z nasłuchem. */
@Serializable(with = BranchRating.Serializer::class)
enum class BranchRating(override val key: String) : KeyedEnum {
    FROM_ANSWER("fromAnswer"),
    FIXED("fixed"),
    NONE("none");

    object Serializer : KeyedEnumSerializer<BranchRating>("BranchRating", entries, FROM_ANSWER)
}

/** Odsłanianie stron w kroku "odwróć kartę": jedna następna albo wszystkie naraz. */
@Serializable(with = RevealStyle.Serializer::class)
enum class RevealStyle(override val key: String) : KeyedEnum {
    ALL("all"),
    NEXT("next");

    object Serializer : KeyedEnumSerializer<RevealStyle>("RevealStyle", entries, ALL)
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("kind")
sealed interface CompoundPause {
    @Serializable
    @SerialName("fixed")
    data class Fixed(val ms: Long = StudyConstants.STEP_DEFAULT_WAIT_MS) : CompoundPause

    @Serializable
    @SerialName("dynamic")
    data class Dynamic(val page: Int = 0, val multiplier: Double = 1.0) : CompoundPause
}

@Serializable
@SerialName("compound")
data class CompoundStep(
    override val condition: StepCondition? = null,
    val version: Int = 1,
    val params: CompoundParams
) : ModeStep

/** Strona, której dotyczy krok atomowy (null: krok nie dotyczy konkretnej strony). */
val ModeStep.referencedPage: Int?
    get() = when (this) {
        is ShowPageStep -> pageIndex

        is SpeakPageStep -> pageIndex

        is ListenAndCheckStep -> pageIndex

        is DynamicPauseStep -> nextPageIndex

        is ShowAllPagesStep,
        is WaitForTapToRevealNextStep,
        is WaitForTapToRevealStep,
        is ShowRatingsStep,
        is SpeakAllPagesStep,
        is WaitStep,
        is FeedbackSuccessStep,
        is FeedbackErrorStep,
        is AutoRateFromAnswerStep,
        is AutoRateFixedStep,
        is MarkFailedStep,
        is WaitForTapStep,
        is NextCardStep,
        is CompoundStep -> null
    }

/** Ten sam krok z innym warunkiem; krok złożony nie ma warunku (rozwinięcie i tak by go pominęło). */
fun ModeStep.withCondition(condition: StepCondition?): ModeStep = when (this) {
    is ShowPageStep -> copy(condition = condition)
    is ShowAllPagesStep -> copy(condition = condition)
    is WaitForTapToRevealNextStep -> copy(condition = condition)
    is WaitForTapToRevealStep -> copy(condition = condition)
    is ShowRatingsStep -> copy(condition = condition)
    is SpeakPageStep -> copy(condition = condition)
    is SpeakAllPagesStep -> copy(condition = condition)
    is DynamicPauseStep -> copy(condition = condition)
    is WaitStep -> copy(condition = condition)
    is ListenAndCheckStep -> copy(condition = condition)
    is FeedbackSuccessStep -> copy(condition = condition)
    is FeedbackErrorStep -> copy(condition = condition)
    is AutoRateFromAnswerStep -> copy(condition = condition)
    is AutoRateFixedStep -> copy(condition = condition)
    is MarkFailedStep -> copy(condition = condition)
    is WaitForTapStep -> copy(condition = condition)
    is NextCardStep -> copy(condition = condition)
    is CompoundStep -> this
}
