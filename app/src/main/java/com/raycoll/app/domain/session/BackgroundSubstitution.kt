package com.raycoll.app.domain.session

import com.raycoll.app.domain.model.ListenAndCheckStep
import com.raycoll.app.domain.model.ModeStep
import com.raycoll.app.domain.model.ShowRatingsStep
import com.raycoll.app.domain.model.StudyConstants

object BackgroundSubstitution {

    /**
     * Tryb może iść "bez rąk" w tle: ma kroki, nie wymaga ręcznej oceny ani mikrofonu.
     * [steps] to kroki już rozwinięte (atomowe).
     */
    fun isModeHandsFreeCapable(steps: List<ModeStep>): Boolean =
        steps.isNotEmpty() && steps.none { it is ShowRatingsStep || it is ListenAndCheckStep }

    /** Pauza zastępująca tapnięcie w tle: proporcjonalna do długości tekstu stron, w granicach. */
    fun calculateBgWaitMs(activePages: List<String>): Long =
        (activePages.sumOf { it.length } * StudyConstants.BG_WAIT_MS_PER_CHAR).toLong()
            .coerceIn(StudyConstants.BG_WAIT_SUBSTITUTE_MIN, StudyConstants.BG_WAIT_SUBSTITUTE_MAX)
}
