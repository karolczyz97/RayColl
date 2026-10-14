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
import com.raycoll.app.domain.model.pageIndex

/** Problemy trybu nauki; UI zamienia je na komunikaty. Tylko [blocking] wyłączają zapis. */
sealed interface ModeIssue {
    val blocking: Boolean

    data object NameRequired : ModeIssue {
        override val blocking = true
    }
    data object NoSteps : ModeIssue {
        override val blocking = true
    }
    data class PageOutOfRange(val stepNumber: Int, val pageNumber: Int) : ModeIssue {
        override val blocking = true
    }
    data object NoTerminalStep : ModeIssue {
        override val blocking = false
    }
    data object AutoRateWithoutStt : ModeIssue {
        override val blocking = false
    }
    data class NoFallbackAfterListen(val stepNumber: Int) : ModeIssue {
        override val blocking = false
    }
    data class ConditionAfterTapGate(val stepNumber: Int) : ModeIssue {
        override val blocking = false
    }
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
        val issues = mutableListOf<ModeIssue>()
        if (name.isBlank()) issues += ModeIssue.NameRequired
        steps.forEachIndexed { index, step ->
            val maxPage = CompoundExpander.expandStep(step).mapNotNull { it.pageIndex }.maxOrNull()
            if (maxPage != null && maxPage >= pageCount) {
                issues += ModeIssue.PageOutOfRange(stepNumber = index + 1, pageNumber = maxPage + 1)
            }
        }
        val ownedSteps = steps.flatMapIndexed { index, step -> CompoundExpander.expandStep(step).map { index to it } }
        val expanded = ownedSteps.map { it.second }
        if (expanded.none { it is ShowRatingsStep || it is NextCardStep }) issues += ModeIssue.NoTerminalStep
        // ocena "z odpowiedzi" czyta wynik nasłuchu, więc nasłuch musi być PRZED nią
        val firstListen = expanded.indexOfFirst { it is ListenAndCheckStep }
        val orphanAutoRate = expanded.withIndex().any { (index, step) ->
            step is AutoRateFromAnswerStep && (firstListen == -1 || index < firstListen)
        }
        if (orphanAutoRate) issues += ModeIssue.AutoRateWithoutStt
        steps.forEachIndexed { index, step ->
            val params = (step as? CompoundStep)?.params
            val missingFallback = when (params) {
                is CompoundParams.ListenGrade -> !params.manualFallback
                is CompoundParams.GradeAfterListen -> !params.manualFallback
                else -> false
            }
            val hasFallback = ownedSteps.any { (owner, atom) ->
                owner > index && atom.condition == null && (atom is ShowRatingsStep || atom is NextCardStep)
            }
            if (missingFallback && !hasFallback) issues += ModeIssue.NoFallbackAfterListen(index + 1)
        }
        var listened = false
        var gateAfterListen = false
        for ((owner, atom) in ownedSteps) {
            if (gateAfterListen && atom.condition != null) issues += ModeIssue.ConditionAfterTapGate(owner + 1)
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
        return issues.distinct()
    }
}
