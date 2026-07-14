package com.raycoll.app.domain.steps

import com.raycoll.app.domain.model.CompoundParams
import com.raycoll.app.domain.model.CompoundStep
import com.raycoll.app.domain.model.ModeStep

// Szablony budują kroki compound (edytowalne bloki z parametrami), nie surowe
// listy atomowe — parytet z createModeFromTemplate w modeTemplates.ts.
// Runtime po ekspansji jest identyczny z krokami trybów seedowych.
object ModeTemplates {
    fun classicTemplate(): List<ModeStep> = listOf(
        CompoundStep(params = CompoundParams.PresentFront()),
        CompoundStep(params = CompoundParams.FlipReveal()),
    )

    fun audioTemplate(): List<ModeStep> = listOf(
        CompoundStep(params = CompoundParams.PresentFront()),
        CompoundStep(params = CompoundParams.ListenGrade()),
    )

    fun blankTemplate(): List<ModeStep> = emptyList()
}
