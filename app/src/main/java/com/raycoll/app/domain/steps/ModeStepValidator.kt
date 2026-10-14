package com.raycoll.app.domain.steps

import com.raycoll.app.domain.model.AutoRateFromAnswerStep
import com.raycoll.app.domain.model.CompoundParams
import com.raycoll.app.domain.model.CompoundStep
import com.raycoll.app.domain.model.ListenAndCheckStep
import com.raycoll.app.domain.model.ModeStep
import com.raycoll.app.domain.model.NextCardStep
import com.raycoll.app.domain.model.PageLimits
import com.raycoll.app.domain.model.ShowRatingsStep
import com.raycoll.app.domain.model.WaitForTapStep
import com.raycoll.app.domain.model.WaitForTapToRevealNextStep
import com.raycoll.app.domain.model.WaitForTapToRevealStep
import com.raycoll.app.domain.model.referencedPage

/** Problemy trybu nauki; UI zamienia je na komunikaty. Tylko [blocking] wyłączają zapis. */
sealed class ModeIssue(val blocking: Boolean) {
    data object NameRequired : ModeIssue(blocking = true)
    data object NoSteps : ModeIssue(blocking = true)
    data class PageOutOfRange(val stepNumber: Int, val pageNumber: Int) : ModeIssue(blocking = true)
    data object NoTerminalStep : ModeIssue(blocking = false)
    data object AutoRateWithoutStt : ModeIssue(blocking = false)
    data class NoFallbackAfterListen(val stepNumber: Int) : ModeIssue(blocking = false)
    data class ConditionAfterTapGate(val stepNumber: Int) : ModeIssue(blocking = false)
}

object ModeStepValidator {
    /**
     * [pageCount] = najwyższa liczba stron, jaką może mieć talia. Kroki złożone
     * sprawdzane są po rozwinięciu, z numerem kroku z edytora.
     */
    fun validate(
        name: String,
        steps: List<ModeStep>,
        pageCount: Int = PageLimits.MAX_VISIBLE_PAGE_COUNT
    ): List<ModeIssue> {
        if (steps.isEmpty()) {
            return listOfNotNull(ModeIssue.NameRequired.takeIf { name.isBlank() }, ModeIssue.NoSteps)
        }
        val expandedPerStep = steps.map(CompoundExpander::expandStep)
        // atomy kroków z numerem kroku z edytora, od którego pochodzą
        val ownedSteps = expandedPerStep.flatMapIndexed { index, atoms -> atoms.map { index to it } }
        val issues = mutableListOf<ModeIssue>()
        if (name.isBlank()) issues += ModeIssue.NameRequired
        issues += pageRangeIssues(expandedPerStep, pageCount)
        issues += terminalAndRateIssues(ownedSteps.map { it.second })
        issues += fallbackIssues(steps, ownedSteps)
        issues += tapGateIssues(ownedSteps)
        return issues.distinct()
    }

    private fun pageRangeIssues(expandedPerStep: List<List<ModeStep>>, pageCount: Int): List<ModeIssue> =
        expandedPerStep.mapIndexedNotNull { index, atoms ->
            val maxPage = atoms.mapNotNull { it.referencedPage }.maxOrNull()
            if (maxPage != null && maxPage >= pageCount) {
                ModeIssue.PageOutOfRange(stepNumber = index + 1, pageNumber = maxPage + 1)
            } else {
                null
            }
        }

    private fun terminalAndRateIssues(expanded: List<ModeStep>): List<ModeIssue> = buildList {
        if (expanded.none { it is ShowRatingsStep || it is NextCardStep }) add(ModeIssue.NoTerminalStep)
        // ocena "z odpowiedzi" czyta wynik nasłuchu, więc nasłuch musi być PRZED nią
        val firstListen = expanded.indexOfFirst { it is ListenAndCheckStep }
        val orphanAutoRate = expanded.withIndex().any { (index, step) ->
            step is AutoRateFromAnswerStep && (firstListen == -1 || index < firstListen)
        }
        if (orphanAutoRate) add(ModeIssue.AutoRateWithoutStt)
    }

    private fun fallbackIssues(steps: List<ModeStep>, ownedSteps: List<Pair<Int, ModeStep>>): List<ModeIssue> =
        steps.mapIndexedNotNull { index, step ->
            val params = (step as? CompoundStep)?.params
            val missingFallback = when (params) {
                is CompoundParams.ListenGrade -> !params.manualFallback
                is CompoundParams.GradeAfterListen -> !params.manualFallback
                else -> false
            }
            val hasFallback = ownedSteps.any { (owner, atom) ->
                owner > index && atom.condition == null && (atom is ShowRatingsStep || atom is NextCardStep)
            }
            if (missingFallback && !hasFallback) ModeIssue.NoFallbackAfterListen(index + 1) else null
        }

    private fun tapGateIssues(ownedSteps: List<Pair<Int, ModeStep>>): List<ModeIssue> = buildList {
        var listened = false
        var gateAfterListen = false
        for ((owner, atom) in ownedSteps) {
            if (gateAfterListen && atom.condition != null) add(ModeIssue.ConditionAfterTapGate(owner + 1))
            when (atom) {
                is ListenAndCheckStep -> {
                    listened = true
                    gateAfterListen = false
                }

                is WaitForTapStep, is WaitForTapToRevealStep, is WaitForTapToRevealNextStep ->
                    if (listened) gateAfterListen = true

                else -> Unit
            }
        }
    }
}
