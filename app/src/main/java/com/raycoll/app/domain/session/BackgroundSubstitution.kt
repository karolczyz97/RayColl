package com.raycoll.app.domain.session

import com.raycoll.app.domain.model.*

object BackgroundSubstitution {
    fun isModeHandsFreeCapable(steps: List<AtomicStep>): Boolean =
        steps.isNotEmpty() && steps.none { it is ShowRatings || it is ListenAndCheck }

    fun substitute(steps: List<AtomicStep>, totalLen: Int): List<AtomicStep> =
        steps.map { step ->
            when (step) {
                is WaitForTap -> Wait(ms = kotlin.math.min(15000L, kotlin.math.max(1500L, (totalLen * 120L).coerceIn(1500L, 15000L))).toInt())
                is WaitForTapToReveal -> { val all = step.let { ShowAllPages() }; all } // reveal all compat
                is WaitForTapToRevealNext -> ShowAllPages()
                is ShowRatings -> ShowRatings() // auto-pause handled by caller
                else -> step
            }
        }
}
