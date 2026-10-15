package com.raycoll.app.domain.seed

import com.raycoll.app.domain.model.AutoRateFixedStep
import com.raycoll.app.domain.model.AutoRateFromAnswerStep
import com.raycoll.app.domain.model.DEFAULT_MODE_ID
import com.raycoll.app.domain.model.FeedbackErrorStep
import com.raycoll.app.domain.model.FeedbackSuccessStep
import com.raycoll.app.domain.model.ListenAndCheckStep
import com.raycoll.app.domain.model.MarkFailedStep
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

/** Wbudowane tryby nauki. Nazwy to wartości domyślne — UI pokazuje ich tłumaczenie, dopóki użytkownik ich nie zmieni. */
enum class BuiltInMode(val id: String, val defaultName: String) {
    CLASSIC(DEFAULT_MODE_ID, "Klasyczny"),
    LISTEN_SPEAK("listen-speak", "Audio");

    companion object {
        fun fromId(id: String?): BuiltInMode? = entries.firstOrNull { it.id == id }
    }
}

fun isBuiltInModeSourceId(value: String): Boolean = BuiltInMode.fromId(value) != null

val SEED_MODES: List<StudyMode> = listOf(
    StudyMode(
        id = BuiltInMode.CLASSIC.id,
        name = BuiltInMode.CLASSIC.defaultName,
        isBuiltIn = true,
        builtInSourceId = BuiltInMode.CLASSIC.id,
        steps = listOf(
            ShowPageStep(pageIndex = 0),
            SpeakPageStep(pageIndex = 0),
            WaitForTapStep(),
            ShowAllPagesStep(),
            ShowRatingsStep()
        )
    ),
    StudyMode(
        id = BuiltInMode.LISTEN_SPEAK.id,
        name = BuiltInMode.LISTEN_SPEAK.defaultName,
        isBuiltIn = true,
        builtInSourceId = BuiltInMode.LISTEN_SPEAK.id,
        steps = listOf(
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
        )
    )
)

fun seedModeFor(sourceId: String?): StudyMode? = SEED_MODES.firstOrNull { it.builtInSourceId == sourceId }
