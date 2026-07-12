package com.raycoll.app.domain.session

import com.raycoll.app.domain.model.ModeStep
import com.raycoll.app.domain.model.StudyConstants
import com.raycoll.app.domain.model.Flashcard

object BackgroundSubstitution {

    fun isModeHandsFreeCapable(steps: List<ModeStep>): Boolean {
        if (steps.isEmpty()) return false
        return steps.none { it is com.raycoll.app.domain.model.ShowRatingsStep } &&
               steps.none { it is com.raycoll.app.domain.model.ListenAndCheckStep }
    }

    fun calculateBgWaitMs(activePages: List<String>): Long {
        val totalChars = activePages.sumOf { it.length }
        return (totalChars * StudyConstants.BG_WAIT_MS_PER_CHAR).toLong()
            .coerceIn(StudyConstants.BG_WAIT_SUBSTITUTE_MIN, StudyConstants.BG_WAIT_SUBSTITUTE_MAX)
    }
}
