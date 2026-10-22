package com.raycoll.app.domain.seed

import com.raycoll.app.domain.model.AutoRateFixedStep
import com.raycoll.app.domain.model.AutoRateFromAnswerStep
import com.raycoll.app.domain.model.DEFAULT_MODE_ID
import com.raycoll.app.domain.model.DynamicPauseStep
import com.raycoll.app.domain.model.FeedbackErrorStep
import com.raycoll.app.domain.model.FeedbackSuccessStep
import com.raycoll.app.domain.model.ListenAndCheckStep
import com.raycoll.app.domain.model.MarkFailedStep
import com.raycoll.app.domain.model.ModeStep
import com.raycoll.app.domain.model.NextCardStep
import com.raycoll.app.domain.model.Ratings
import com.raycoll.app.domain.model.ShowAllPagesStep
import com.raycoll.app.domain.model.ShowPageStep
import com.raycoll.app.domain.model.ShowRatingsStep
import com.raycoll.app.domain.model.SpeakPageStep
import com.raycoll.app.domain.model.StepCondition.CORRECT
import com.raycoll.app.domain.model.StepCondition.WRONG
import com.raycoll.app.domain.model.StudyConstants
import com.raycoll.app.domain.model.StudyMode
import com.raycoll.app.domain.model.WaitForTapStep
import com.raycoll.app.domain.model.WaitStep

/**
 * Wbudowane tryby nauki w kolejności, w jakiej pokazują je listy. Nazwy to wartości domyślne — UI pokazuje
 * ich tłumaczenie, dopóki użytkownik ich nie zmieni.
 */
enum class BuiltInMode(val id: String, val defaultName: String) {
    CLASSIC(DEFAULT_MODE_ID, "Klasyczny"),
    LISTEN_SPEAK("listen-speak", "Audio"),
    LISTEN_ONLY("listen-only", "Odsłuch"),
    BROWSE("browse", "Przegląd"),
    AUTO_REVEAL("auto-reveal", "Samo odkrywa");

    companion object {
        fun fromId(id: String?): BuiltInMode? = entries.firstOrNull { it.id == id }
    }
}

fun isBuiltInModeSourceId(value: String): Boolean = BuiltInMode.fromId(value) != null

/** Czas odpowiedzi w trybach z automatyczną pauzą: wielokrotność długości właśnie przeczytanej strony. */
private const val LISTEN_ONLY_PAUSE_MULTIPLIER = 2.0
private const val AUTO_REVEAL_PAUSE_MULTIPLIER = 2.0

private fun builtIn(mode: BuiltInMode, vararg steps: ModeStep) = StudyMode(
    id = mode.id,
    name = mode.defaultName,
    isBuiltIn = true,
    builtInSourceId = mode.id,
    steps = steps.toList()
)

val SEED_MODES: List<StudyMode> = listOf(
    builtIn(
        BuiltInMode.CLASSIC,
        ShowPageStep(pageIndex = 0),
        SpeakPageStep(pageIndex = 0),
        WaitForTapStep(),
        ShowAllPagesStep(),
        ShowRatingsStep()
    ),
    builtIn(
        BuiltInMode.LISTEN_SPEAK,
        ShowPageStep(pageIndex = 0),
        SpeakPageStep(pageIndex = 0),
        // pierwsza próba
        ListenAndCheckStep(pageIndex = 1),
        FeedbackSuccessStep(condition = CORRECT),
        ShowAllPagesStep(condition = CORRECT),
        AutoRateFromAnswerStep(condition = CORRECT),
        WaitStep(condition = CORRECT, ms = StudyConstants.PAUSE_AFTER_CORRECT_MS),
        NextCardStep(condition = CORRECT),
        FeedbackErrorStep(condition = WRONG),
        WaitStep(condition = WRONG, ms = StudyConstants.PAUSE_BEFORE_RETRY_MS),
        // druga próba po błędzie; trafienie = HARD niezależnie od dopasowania (odpowiedź była już raz zła)
        ListenAndCheckStep(condition = WRONG, pageIndex = 1),
        FeedbackSuccessStep(condition = CORRECT),
        ShowAllPagesStep(condition = CORRECT),
        AutoRateFixedStep(condition = CORRECT, rating = Ratings.HARD),
        WaitStep(condition = CORRECT, ms = StudyConstants.PAUSE_AFTER_CORRECT_MS),
        NextCardStep(condition = CORRECT),
        FeedbackErrorStep(condition = WRONG),
        ShowAllPagesStep(condition = WRONG),
        SpeakPageStep(condition = WRONG, pageIndex = 1),
        MarkFailedStep(condition = WRONG),
        AutoRateFixedStep(condition = WRONG, rating = Ratings.AGAIN),
        WaitStep(condition = WRONG, ms = StudyConstants.PAUSE_AFTER_WRONG_MS),
        NextCardStep(condition = WRONG),
        // pominięty nasłuch / błąd mikrofonu: ocena ręczna
        ShowAllPagesStep(),
        ShowRatingsStep()
    ),
    // bez ocen i bez dotykania: czas na odpowiedź to wielokrotność długości właśnie przeczytanej strony
    builtIn(
        BuiltInMode.LISTEN_ONLY,
        ShowPageStep(pageIndex = 0),
        SpeakPageStep(pageIndex = 0),
        DynamicPauseStep(nextPageIndex = 0, pauseMultiplier = LISTEN_ONLY_PAUSE_MULTIPLIER),
        ShowPageStep(pageIndex = 1),
        SpeakPageStep(pageIndex = 1),
        DynamicPauseStep(nextPageIndex = 1, pauseMultiplier = LISTEN_ONLY_PAUSE_MULTIPLIER),
        NextCardStep()
    ),
    // dotyk odkrywa odpowiedź i przechodzi dalej, harmonogram powtórek zostaje bez zmian
    builtIn(
        BuiltInMode.BROWSE,
        ShowPageStep(pageIndex = 0),
        SpeakPageStep(pageIndex = 0),
        WaitForTapStep(),
        ShowAllPagesStep(),
        SpeakPageStep(pageIndex = 1),
        WaitForTapStep(),
        NextCardStep()
    ),
    builtIn(
        BuiltInMode.AUTO_REVEAL,
        ShowPageStep(pageIndex = 0),
        SpeakPageStep(pageIndex = 0),
        DynamicPauseStep(nextPageIndex = 0, pauseMultiplier = AUTO_REVEAL_PAUSE_MULTIPLIER),
        ShowAllPagesStep(),
        SpeakPageStep(pageIndex = 1),
        ShowRatingsStep()
    )
)

fun seedModeFor(sourceId: String?): StudyMode? = SEED_MODES.firstOrNull { it.builtInSourceId == sourceId }
