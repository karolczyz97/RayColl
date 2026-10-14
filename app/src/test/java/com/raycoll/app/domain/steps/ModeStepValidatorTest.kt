package com.raycoll.app.domain.steps

import com.raycoll.app.domain.model.AutoRateFromAnswerStep
import com.raycoll.app.domain.model.CompoundParams
import com.raycoll.app.domain.model.CompoundStep
import com.raycoll.app.domain.model.ListenAndCheckStep
import com.raycoll.app.domain.model.NextCardStep
import com.raycoll.app.domain.model.ShowPageStep
import com.raycoll.app.domain.model.ShowRatingsStep
import com.raycoll.app.domain.model.StepCondition
import com.raycoll.app.domain.model.StudyMode
import com.raycoll.app.domain.model.WaitForTapStep
import com.raycoll.app.domain.model.pageIndex
import com.raycoll.app.domain.seed.SEED_MODES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModeStepValidatorTest {

    @Test
    fun `missing listening fallback warns unless a later unconditional terminal exists`() {
        val noFallback = CompoundStep(params = CompoundParams.ListenGrade(manualFallback = false))
        val issue = ModeIssue.NoFallbackAfterListen(1)
        assertTrue(ModeStepValidator.validate("Mode", listOf(noFallback)).contains(issue))
        assertFalse(issue.blocking)
        assertFalse(ModeStepValidator.validate("Mode", listOf(noFallback, ShowRatingsStep())).contains(issue))
        assertTrue(
            ModeStepValidator.validate("Mode", listOf(noFallback, NextCardStep(StepCondition.CORRECT))).contains(issue)
        )
        val grade = CompoundStep(params = CompoundParams.GradeAfterListen(manualFallback = false))
        assertTrue(
            ModeStepValidator.validate(
                "Mode",
                listOf(ListenAndCheckStep(), grade)
            ).contains(ModeIssue.NoFallbackAfterListen(2))
        )
    }

    @Test
    fun `condition after a tap gate maps the warning to the editable compound step`() {
        val steps =
            listOf(ListenAndCheckStep(), WaitForTapStep(), CompoundStep(params = CompoundParams.GradeAfterListen()))
        val issues = ModeStepValidator.validate("Mode", steps)
        assertEquals(
            listOf(ModeIssue.ConditionAfterTapGate(3)),
            issues.filterIsInstance<ModeIssue.ConditionAfterTapGate>()
        )
        assertFalse(issues.any { it.blocking })
        val freshListen = steps.take(2) + ListenAndCheckStep() + NextCardStep(StepCondition.CORRECT)
        assertTrue(ModeStepValidator.validate("Mode", freshListen).none { it is ModeIssue.ConditionAfterTapGate })
    }

    @Test
    fun `built in modes and templates do not receive new warnings`() {
        val modes = SEED_MODES.map { it.steps } + ModeTemplate.entries.map { it.steps }
        for (steps in modes) {
            assertTrue(
                ModeStepValidator.validate("Mode", steps).none {
                    it is ModeIssue.NoFallbackAfterListen || it is ModeIssue.ConditionAfterTapGate
                }
            )
        }
    }

    @Test
    fun `empty steps gives error`() {
        val mode = StudyMode(id = "m1", name = "Test", steps = emptyList())
        val errors = ModeStepValidator.validate(mode.name, mode.steps, 2)
        assertTrue(errors.any { it is ModeIssue.NoSteps })
    }

    @Test
    fun `blank name gives error`() {
        val mode = StudyMode(id = "m1", name = "", steps = listOf(ShowPageStep()))
        val errors = ModeStepValidator.validate(mode.name, mode.steps, 2)
        assertTrue(errors.any { it is ModeIssue.NameRequired })
    }

    @Test
    fun `page out of range gives error`() {
        val mode = StudyMode(
            id = "m1",
            name = "Test",
            steps = listOf(ShowPageStep(pageIndex = 5), ShowRatingsStep())
        )
        val errors = ModeStepValidator.validate(mode.name, mode.steps, 2)
        assertTrue(errors.any { it is ModeIssue.PageOutOfRange })
    }

    @Test
    fun `no terminal step gives error`() {
        val mode = StudyMode(id = "m1", name = "Test", steps = listOf(ShowPageStep()))
        val errors = ModeStepValidator.validate(mode.name, mode.steps, 2)
        assertTrue(errors.any { it is ModeIssue.NoTerminalStep })
    }

    @Test
    fun `no-terminal is a non-blocking warning`() {
        // Tryb bez terminala: ostrzeżenie POKAZANE, ale NIE blokuje zapisu.
        val mode = StudyMode(id = "m1", name = "Test", steps = listOf(ShowPageStep()))
        val issues = ModeStepValidator.validate(mode.name, mode.steps, 2)
        assertTrue(issues.any { it is ModeIssue.NoTerminalStep })
        assertFalse(issues.any { it.blocking })
    }

    @Test
    fun `auto_rate warning only when before first listen`() {
        // Ocena z odpowiedzi wymaga wcześniejszego kroku nasłuchu: ostrzegamy, gdy
        // stoi PRZED pierwszym listen_and_check (lub gdy STT w ogóle brak).
        val listenThenRate = StudyMode(
            id = "m1",
            name = "Test",
            steps = listOf(
                ListenAndCheckStep(pageIndex = 1),
                AutoRateFromAnswerStep(),
                NextCardStep()
            )
        )
        assertFalse(
            ModeStepValidator.validate(listenThenRate.name, listenThenRate.steps, 2).any {
                it is ModeIssue.AutoRateWithoutStt
            }
        )

        val rateBeforeListen = StudyMode(
            id = "m2",
            name = "Test",
            steps = listOf(
                AutoRateFromAnswerStep(),
                ListenAndCheckStep(pageIndex = 1),
                NextCardStep()
            )
        )
        assertTrue(
            ModeStepValidator.validate(rateBeforeListen.name, rateBeforeListen.steps, 2).any {
                it is ModeIssue.AutoRateWithoutStt
            }
        )

        val rateWithoutListen = StudyMode(
            id = "m3",
            name = "Test",
            steps = listOf(AutoRateFromAnswerStep(), NextCardStep())
        )
        assertTrue(
            ModeStepValidator.validate(rateWithoutListen.name, rateWithoutListen.steps, 2).any {
                it is ModeIssue.AutoRateWithoutStt
            }
        )
    }

    @Test
    fun `templates build editable compound steps`() {
        val classic = ModeTemplate.CLASSIC.steps
        assertEquals(2, classic.size)
        assertTrue((classic[0] as CompoundStep).params is CompoundParams.PresentFront)
        assertTrue((classic[1] as CompoundStep).params is CompoundParams.FlipReveal)

        val audio = ModeTemplate.AUDIO.steps
        assertEquals(2, audio.size)
        assertTrue((audio[0] as CompoundStep).params is CompoundParams.PresentFront)
        assertTrue((audio[1] as CompoundStep).params is CompoundParams.ListenGrade)

        val expandedClassic = CompoundExpander.expandModeSteps(classic)
        val seedClassic = com.raycoll.app.domain.seed.SEED_MODES.first { it.id == "classic" }.steps
        assertEquals(seedClassic, expandedClassic)

        val expandedAudio = CompoundExpander.expandModeSteps(audio)
        assertEquals(17, expandedAudio.size)
    }

    @Test
    fun `page out of range is blocking, name and steps too`() {
        val outOfRange = StudyMode(
            id = "m1",
            name = "Test",
            steps = listOf(ShowPageStep(pageIndex = 5), ShowRatingsStep())
        )
        assertTrue(ModeStepValidator.validate(outOfRange.name, outOfRange.steps, 2).any { it.blocking })
        val noName = StudyMode(
            id = "m2",
            name = "",
            steps = listOf(ShowPageStep(), ShowRatingsStep())
        )
        assertTrue(ModeStepValidator.validate(noName.name, noName.steps, 2).any { it.blocking })
    }
}
