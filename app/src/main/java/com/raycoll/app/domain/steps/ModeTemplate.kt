package com.raycoll.app.domain.steps

import com.raycoll.app.domain.model.CompoundParams
import com.raycoll.app.domain.model.CompoundStep
import com.raycoll.app.domain.model.ModeStep

/**
 * Punkty startowe nowego trybu. Budują kroki złożone (edytowalne bloki), które po
 * rozwinięciu dają to samo co wbudowane tryby.
 */
enum class ModeTemplate(val steps: List<ModeStep>) {
    CLASSIC(
        listOf(
            CompoundStep(params = CompoundParams.PresentFront()),
            CompoundStep(params = CompoundParams.FlipReveal())
        )
    ),
    AUDIO(
        listOf(
            CompoundStep(params = CompoundParams.PresentFront()),
            CompoundStep(params = CompoundParams.ListenGrade())
        )
    ),
    BLANK(emptyList())
}
