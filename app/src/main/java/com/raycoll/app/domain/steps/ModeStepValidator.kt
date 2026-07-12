package com.raycoll.app.domain.steps

import com.raycoll.app.domain.model.*

object ModeStepValidator {
    fun validate(steps: List<ModeStep>, pageCount: Int): List<String> {
        val errors = mutableListOf<String>()
        if (steps.isEmpty()) { errors.add("Add at least one step."); return errors }

        val expanded = expandModeSteps(steps)
        val hasTerminal = expanded.any { it is ShowRatings || it is NextCard }
        if (!hasTerminal) errors.add("No terminal step: add Show Ratings or Next Card.")
        if (expanded.any { it is AutoRateFromAnswer } && expanded.none { it is ListenAndCheck }) {
            errors.add("Auto-rate from answer needs an earlier Check speech (STT) step.")
        }

        steps.forEachIndexed { index, step ->
            when (step) {
                is ShowPage -> if (step.pageIndex >= pageCount) errors.add("Step ${index + 1} uses page ${step.pageIndex + 1}, which is out of range.")
                is SpeakPage -> if (step.pageIndex >= pageCount) errors.add("Step ${index + 1} uses page ${step.pageIndex + 1}, which is out of range.")
                is ListenAndCheck -> if (step.pageIndex >= pageCount) errors.add("Step ${index + 1} uses page ${step.pageIndex + 1}, which is out of range.")
                is DynamicPause -> if (step.nextPageIndex >= pageCount) errors.add("Step ${index + 1} uses page ${step.nextPageIndex + 1}, which is out of range.")
                is CompoundStep -> validateCompound(step, pageCount, index, errors)
                else -> {}
            }
        }
        return errors
    }

    private fun validateCompound(step: CompoundStep, pageCount: Int, index: Int, errors: MutableList<String>) {
        when (val p = step.params) {
            is PresentFrontParams -> if (p.page >= pageCount) errors.add("Step ${index + 1} uses page ${p.page + 1}, which is out of range.")
            is SpeakPauseNextParams -> { if (p.page >= pageCount) errors.add("Step ${index + 1} uses page ${p.page + 1} out of range."); if (p.nextPage >= pageCount) errors.add("Step ${index + 1} uses page ${p.nextPage + 1} out of range.") }
            is AutoFlipParams -> { if (p.questionPage >= pageCount || p.answerPage >= pageCount) errors.add("Step ${index + 1}: page out of range.") }
            is ListenGradeParams -> if (p.answerPage >= pageCount) errors.add("Step ${index + 1}: page ${p.answerPage + 1} out of range.")
            else -> {}
        }
    }
}
