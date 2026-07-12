package com.raycoll.app.domain.steps

import com.raycoll.app.domain.model.*

fun expandCompound(step: CompoundStep): List<AtomicStep> = when (val p = step.params) {
    is PresentFrontParams -> {
        if (p.speak) listOf(ShowPage(pageIndex = p.page), SpeakPage(pageIndex = p.page))
        else listOf(ShowPage(pageIndex = p.page))
    }
    is FlipRevealParams -> {
        if (p.revealStyle == "next")
            listOf(WaitForTapToRevealNext(), ShowRatings())
        else listOf(WaitForTapToReveal(), ShowAllPages(), ShowRatings())
    }
    is ShowAllGradeParams -> listOf(ShowAllPages(), ShowRatings())
    is SpeakPauseNextParams -> listOf(
        SpeakPage(pageIndex = p.page),
        DynamicPause(nextPageIndex = p.nextPage, pauseMultiplier = p.multiplier),
        ShowPage(pageIndex = p.nextPage),
    )
    is AutoFlipParams -> {
        val steps = mutableListOf<AtomicStep>(ShowPage(pageIndex = p.questionPage))
        if (p.speakQuestion) steps.add(SpeakPage(pageIndex = p.questionPage))
        steps.add(DynamicPause(nextPageIndex = p.questionPage, pauseMultiplier = p.multiplier))
        steps.add(ShowAllPages())
        if (p.speakAnswer) steps.add(SpeakPage(pageIndex = p.answerPage))
        steps
    }
    is ListenGradeParams -> expandGrade(p, null)
    is GradeAfterListenParams -> expandGrade(null, p)
    is AutoPassNextParams -> listOf(AutoRateFixed(rating = p.rating), NextCard())
    is FailNextParams -> listOf(MarkFailed(), AutoRateFixed(rating = 1), NextCard())
}

private fun expandGrade(listen: ListenGradeParams?, gradeAfter: GradeAfterListenParams?): List<AtomicStep> {
    val steps = mutableListOf<AtomicStep>()
    if (listen != null) steps.add(ListenAndCheck(pageIndex = listen.answerPage, successThreshold = listen.threshold))

    val (correctBranch, wrongBranch, manualFallback) = when {
        listen != null -> Triple(listen.onCorrect, listen.onWrong, listen.manualFallback)
        gradeAfter != null -> Triple(gradeAfter.onCorrect, gradeAfter.onWrong, gradeAfter.manualFallback)
        else -> return steps
    }

    steps.addAll(expandBranch(correctBranch, StepCondition.correct))
    steps.addAll(expandBranch(wrongBranch, StepCondition.wrong))

    if (manualFallback) {
        steps.add(ShowAllPages())
        steps.add(ShowRatings())
    }
    return steps
}

private fun expandBranch(branch: CompoundBranch, condition: StepCondition): List<AtomicStep> {
    val steps = mutableListOf<AtomicStep>()
    if (branch.feedback) steps.add(
        if (condition == StepCondition.correct) FeedbackSuccess(condition = condition)
        else FeedbackError(condition = condition)
    )
    branch.speakPage?.let { steps.add(SpeakPage(pageIndex = it, condition = condition)) }
    branch.pause?.let { pause ->
        when (pause.kind) {
            "fixed" -> steps.add(Wait(ms = pause.ms, condition = condition))
            "dynamic" -> steps.add(DynamicPause(nextPageIndex = pause.page, pauseMultiplier = pause.multiplier, condition = condition))
        }
    }
    if (branch.revealAll) steps.add(ShowAllPages(condition = condition))
    if (branch.markFailed) steps.add(MarkFailed(condition = condition))
    when (branch.rate) {
        "fromAnswer" -> steps.add(AutoRateFromAnswer(condition = condition))
        "fixed" -> steps.add(AutoRateFixed(rating = branch.fixedRating, condition = condition))
    }
    if (branch.advance) steps.add(NextCard(condition = condition))
    return steps
}

fun expandModeSteps(steps: List<ModeStep>): List<AtomicStep> =
    steps.flatMap { step ->
        when (step) {
            is AtomicStep -> listOf(step)
            is CompoundStep -> expandCompound(step)
            else -> emptyList()
        }
    }

fun isAtomicStep(step: ModeStep): Boolean = step is AtomicStep

fun expandWithSource(steps: List<ModeStep>): List<Pair<AtomicStep, Int>> =
    steps.flatMapIndexed { idx, step ->
        val expanded = when (step) {
            is AtomicStep -> listOf(step)
            is CompoundStep -> expandCompound(step)
            else -> emptyList()
        }
        expanded.map { it to idx }
    }
