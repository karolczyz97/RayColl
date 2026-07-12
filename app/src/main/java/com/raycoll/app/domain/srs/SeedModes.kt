package com.raycoll.app.domain.srs

import com.raycoll.app.domain.model.StudyMode

fun isBuiltInModeSourceId(value: String): Boolean =
    value == "classic" || value == "listen-speak"

private val SEED_MODES: List<StudyMode> = listOf(
    StudyMode(
        id = "classic",
        name = "Klasyczny",
        isBuiltIn = true,
        builtInSourceId = "classic",
        updatedAt = 0,
        steps = listOf(
            com.raycoll.app.domain.model.ShowPageStep(pageIndex = 0),
            com.raycoll.app.domain.model.SpeakPageStep(pageIndex = 0),
            com.raycoll.app.domain.model.WaitForTapToRevealStep(),
            com.raycoll.app.domain.model.ShowAllPagesStep(),
            com.raycoll.app.domain.model.ShowRatingsStep(),
        ),
    ),
    StudyMode(
        id = "listen-speak",
        name = "Audio",
        isBuiltIn = true,
        builtInSourceId = "listen-speak",
        updatedAt = 0,
        steps = listOf(
            com.raycoll.app.domain.model.ShowPageStep(pageIndex = 0),
            com.raycoll.app.domain.model.SpeakPageStep(pageIndex = 0),
            com.raycoll.app.domain.model.ListenAndCheckStep(pageIndex = 1, successThreshold = 70),
            com.raycoll.app.domain.model.FeedbackSuccessStep(condition = com.raycoll.app.domain.model.StepCondition.CORRECT),
            com.raycoll.app.domain.model.ShowAllPagesStep(condition = com.raycoll.app.domain.model.StepCondition.CORRECT),
            com.raycoll.app.domain.model.AutoRateFromAnswerStep(condition = com.raycoll.app.domain.model.StepCondition.CORRECT),
            com.raycoll.app.domain.model.NextCardStep(condition = com.raycoll.app.domain.model.StepCondition.CORRECT),
            com.raycoll.app.domain.model.FeedbackErrorStep(condition = com.raycoll.app.domain.model.StepCondition.WRONG),
            com.raycoll.app.domain.model.ShowAllPagesStep(condition = com.raycoll.app.domain.model.StepCondition.WRONG),
            com.raycoll.app.domain.model.MarkFailedStep(condition = com.raycoll.app.domain.model.StepCondition.WRONG),
            com.raycoll.app.domain.model.AutoRateFixedStep(rating = 1, condition = com.raycoll.app.domain.model.StepCondition.WRONG),
            com.raycoll.app.domain.model.NextCardStep(condition = com.raycoll.app.domain.model.StepCondition.WRONG),
            com.raycoll.app.domain.model.ShowAllPagesStep(),
            com.raycoll.app.domain.model.ShowRatingsStep(),
        ),
    ),
)

fun createSeedModes(): List<StudyMode> = SEED_MODES.map { mode ->
    mode.copy(steps = mode.steps.map { it })
}
