package com.raycoll.app.domain.steps

import com.raycoll.app.domain.model.AutoRateFromAnswerStep
import com.raycoll.app.domain.model.ListenAndCheckStep
import com.raycoll.app.domain.model.NextCardStep
import com.raycoll.app.domain.model.ShowRatingsStep
import com.raycoll.app.domain.model.StudyMode

/**
 * Strukturalne problemy walidacji — UI mapuje je na zlokalizowane komunikaty.
 * `blocking` = odpowiednik severity 'error' z validateModeSteps.ts: tylko blokujące
 * problemy wyłączają zapis; NoTerminalStep/AutoRateWithoutStt to ostrzeżenia (pokazywane,
 * ale NIE blokujące — parytet z hasBlockingStepIssue).
 */
sealed interface ModeIssue {
    val blocking: Boolean
    data object NameRequired : ModeIssue { override val blocking = true }
    data object NoSteps : ModeIssue { override val blocking = true }
    data class PageOutOfRange(val stepNumber: Int, val pageNumber: Int) : ModeIssue { override val blocking = true }
    data object NoTerminalStep : ModeIssue { override val blocking = false }
    data object AutoRateWithoutStt : ModeIssue { override val blocking = false }
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
        // auto_rate_from_answer czyta wynik ostatniego listen_and_check — ostrzegaj,
        // gdy występuje PRZED pierwszym STT (parytet z validateModeSteps.ts, który
        // porównuje pozycje, nie samą obecność).
        val firstListenIndex = expanded.indexOfFirst { it is ListenAndCheckStep }
        val orphanAutoRate = expanded.withIndex().any { (index, step) ->
            step is AutoRateFromAnswerStep && (firstListenIndex == -1 || index < firstListenIndex)
        }
        if (orphanAutoRate) issues.add(ModeIssue.AutoRateWithoutStt)
        return issues
    }
}
