package com.raycoll.app.domain.steps

import com.raycoll.app.domain.model.ModeStep
import com.raycoll.app.domain.model.StudyMode

object ModeStepValidator {
    fun validate(mode: StudyMode, pageCount: Int): List<String> {
        val errors = mutableListOf<String>()
        if (mode.name.isBlank()) errors.add("Mode name is required")
        if (mode.steps.isEmpty()) {
            errors.add("Add at least one step")
            return errors
        }
        val expanded = CompoundExpander.expandModeSteps(mode.steps)
        for ((index, step) in mode.steps.withIndex()) {
            val pageIndex = StepRegistry.getStepPageIndex(step)
            if (pageIndex != null && pageIndex >= pageCount) {
                errors.add("Step ${index + 1} uses page ${pageIndex + 1}, which is out of range")
            }
        }
        val hasTerminal = expanded.any { it is com.raycoll.app.domain.model.ShowRatingsStep || it is com.raycoll.app.domain.model.NextCardStep }
        if (!hasTerminal) errors.add("No terminal step: add \"Show rating buttons\" or \"Next card\"")
        val hasAutoRate = expanded.any { it is com.raycoll.app.domain.model.AutoRateFromAnswerStep }
        val hasStt = expanded.any { it is com.raycoll.app.domain.model.ListenAndCheckStep }
        if (hasAutoRate && !hasStt) {
            errors.add("\"Auto-rate from answer\" needs an earlier \"Check speech (STT)\" step")
        }
        return errors
    }
}
