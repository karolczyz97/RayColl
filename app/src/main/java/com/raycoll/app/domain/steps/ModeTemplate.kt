package com.raycoll.app.domain.steps

import com.raycoll.app.domain.model.CompoundParams
import com.raycoll.app.domain.model.CompoundStep
import com.raycoll.app.domain.model.ModeStep
import com.raycoll.app.domain.seed.BuiltInMode
import com.raycoll.app.domain.seed.seedModeFor

/**
 * Punkty startowe nowego trybu; [builtIn] to tryb wbudowany, od którego szablon bierze nazwę i opis.
 * CLASSIC i AUDIO składają się z kroków złożonych (edytowalnych bloków; AUDIO to uproszczone Audio:
 * jedna próba nasłuchu, bez powtórki), pozostałe kopiują kroki wbudowanych trybów. BLANK = pusty.
 */
enum class ModeTemplate(val builtIn: BuiltInMode?, val steps: List<ModeStep>) {
    CLASSIC(
        BuiltInMode.CLASSIC,
        listOf(
            CompoundStep(params = CompoundParams.PresentFront()),
            CompoundStep(params = CompoundParams.FlipReveal())
        )
    ),
    AUDIO(
        BuiltInMode.LISTEN_SPEAK,
        listOf(
            CompoundStep(params = CompoundParams.PresentFront()),
            CompoundStep(params = CompoundParams.ListenGrade())
        )
    ),
    LISTEN_ONLY(BuiltInMode.LISTEN_ONLY, builtInSteps(BuiltInMode.LISTEN_ONLY)),
    BROWSE(BuiltInMode.BROWSE, builtInSteps(BuiltInMode.BROWSE)),
    AUTO_REVEAL(BuiltInMode.AUTO_REVEAL, builtInSteps(BuiltInMode.AUTO_REVEAL)),
    BLANK(null, emptyList())
}

private fun builtInSteps(mode: BuiltInMode): List<ModeStep> = checkNotNull(seedModeFor(mode.id)).steps
