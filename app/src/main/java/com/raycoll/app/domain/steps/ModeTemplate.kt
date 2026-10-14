package com.raycoll.app.domain.steps

import com.raycoll.app.domain.model.CompoundParams
import com.raycoll.app.domain.model.CompoundStep
import com.raycoll.app.domain.model.ModeStep

/**
 * Punkty startowe nowego trybu, zbudowane z kroków złożonych (edytowalnych bloków):
 * CLASSIC = wbudowany Klasyczny, AUDIO = uproszczone Audio (jedna próba nasłuchu, bez powtórki),
 * BLANK = pusty.
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
