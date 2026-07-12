package com.raycoll.app.domain.steps

import com.raycoll.app.domain.model.*

object ModeTemplates {
    data class Template(val id: String, val name: String, val steps: List<ModeStep>)

    val templates: List<Template> = listOf(
        Template("classic", "Classic", listOf(
            ShowPage(pageIndex = 0), SpeakPage(pageIndex = 0),
            WaitForTapToReveal(), ShowAllPages(), ShowRatings()
        )),
        Template("audio", "Audio", listOf(
            ShowPage(pageIndex = 0), SpeakPage(pageIndex = 0),
            ListenAndCheck(pageIndex = 1, successThreshold = 70),
            FeedbackSuccess(condition = StepCondition.correct),
            ShowAllPages(condition = StepCondition.correct),
            AutoRateFromAnswer(condition = StepCondition.correct),
            NextCard(condition = StepCondition.correct),
            FeedbackError(condition = StepCondition.wrong),
            ShowAllPages(condition = StepCondition.wrong),
            MarkFailed(condition = StepCondition.wrong),
            AutoRateFixed(rating = 1, condition = StepCondition.wrong),
            NextCard(condition = StepCondition.wrong),
            ShowAllPages(), ShowRatings()
        )),
        Template("show-only", "Show Only", listOf(ShowPage(0), SpeakPage(0), WaitForTapToReveal(), ShowAllPages(), ShowRatings())),
        Template("speak-pause", "Speak + Pause", listOf(
            ShowPage(0), SpeakPage(0), DynamicPause(nextPageIndex = 1, pauseMultiplier = 1),
            ShowPage(1), SpeakPage(1), DynamicPause(nextPageIndex = 0, pauseMultiplier = 1), NextCard()
        )),
    )

    fun getTemplate(id: String): Template? = templates.find { it.id == id }
}
