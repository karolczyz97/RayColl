package com.raycoll.app.domain.steps

import com.raycoll.app.domain.model.ModeStep
import com.raycoll.app.domain.model.ShowAllPagesStep
import com.raycoll.app.domain.model.ShowPageStep
import com.raycoll.app.domain.model.ShowRatingsStep
import com.raycoll.app.domain.model.SpeakPageStep
import com.raycoll.app.domain.model.WaitForTapToRevealStep
import com.raycoll.app.domain.srs.createSeedModes

object ModeTemplates {
    fun classicTemplate(): List<ModeStep> = listOf(
        ShowPageStep(pageIndex = 0),
        SpeakPageStep(pageIndex = 0),
        WaitForTapToRevealStep(),
        ShowAllPagesStep(),
        ShowRatingsStep(),
    )

    fun audioTemplate(): List<ModeStep> = createSeedModes().find { it.id == "listen-speak" }?.steps ?: emptyList()

    fun blankTemplate(): List<ModeStep> = emptyList()
}
