package com.raycoll.app.domain.steps

import com.raycoll.app.domain.model.AutoRateFromAnswerStep
import com.raycoll.app.domain.model.ListenAndCheckStep
import com.raycoll.app.domain.model.NextCardStep
import com.raycoll.app.domain.model.ShowRatingsStep
import com.raycoll.app.domain.model.StudyMode

/** Strukturalne problemy walidacji — UI mapuje je na zlokalizowane komunikaty. */
sealed interface ModeIssue {
    data object NameRequired : ModeIssue
    data object NoSteps : ModeIssue
    data class PageOutOfRange(val stepNumber: Int, val pageNumber: Int) : ModeIssue
    data object NoTerminalStep : ModeIssue
    data object AutoRateWithoutStt : ModeIssue
}

object ModeStepValidator {
    fun validate(mode: StudyMode, pageCount: Int): List<ModeIssue> {
        val issues = mutableListOf<ModeIssue>()
        if (mode.name.isBlank()) issues.add(ModeIssue.NameRequired)
        if (mode.steps.isEmpty()) {
            issues.add(ModeIssue.NoSteps)
            return issues
        }
        val expanded = CompoundExpander.expandModeSteps(mode.steps)
        for ((index, step) in mode.steps.withIndex()) {
            val pageIndex = StepRegistry.getStepPageIndex(step)
            if (pageIndex != null && pageIndex >= pageCount) {
                issues.add(ModeIssue.PageOutOfRange(stepNumber = index + 1, pageNumber = pageIndex + 1))
            }
        }
        val hasTerminal = expanded.any { it is ShowRatingsStep || it is NextCardStep }
        if (!hasTerminal) issues.add(ModeIssue.NoTerminalStep)
        val hasAutoRate = expanded.any { it is AutoRateFromAnswerStep }
        val hasStt = expanded.any { it is ListenAndCheckStep }
        if (hasAutoRate && !hasStt) issues.add(ModeIssue.AutoRateWithoutStt)
        return issues
    }
}
