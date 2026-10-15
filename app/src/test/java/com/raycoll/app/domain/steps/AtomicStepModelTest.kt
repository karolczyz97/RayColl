package com.raycoll.app.domain.steps

import com.raycoll.app.domain.model.AutoRateFixedStep
import com.raycoll.app.domain.model.AutoRateFromAnswerStep
import com.raycoll.app.domain.model.DynamicPauseStep
import com.raycoll.app.domain.model.FeedbackErrorStep
import com.raycoll.app.domain.model.FeedbackSuccessStep
import com.raycoll.app.domain.model.ListenAndCheckStep
import com.raycoll.app.domain.model.MarkFailedStep
import com.raycoll.app.domain.model.ModeStep
import com.raycoll.app.domain.model.NextCardStep
import com.raycoll.app.domain.model.ShowAllPagesStep
import com.raycoll.app.domain.model.ShowPageStep
import com.raycoll.app.domain.model.ShowRatingsStep
import com.raycoll.app.domain.model.SpeakAllPagesStep
import com.raycoll.app.domain.model.SpeakPageStep
import com.raycoll.app.domain.model.WaitForTapStep
import com.raycoll.app.domain.model.WaitForTapToRevealNextStep
import com.raycoll.app.domain.model.WaitForTapToRevealStep
import com.raycoll.app.domain.model.WaitStep
import com.raycoll.app.domain.model.referencedPage
import org.junit.Assert.assertEquals
import org.junit.Test

class AtomicStepModelTest {
    @Test
    fun `all 17 atomic types survive JSON round trip`() {
        val steps = listOf(
            ShowPageStep(), ShowAllPagesStep(),
            WaitForTapToRevealNextStep(), WaitForTapToRevealStep(),
            ShowRatingsStep(), SpeakPageStep(), SpeakAllPagesStep(),
            DynamicPauseStep(), WaitStep(),
            ListenAndCheckStep(), FeedbackSuccessStep(),
            FeedbackErrorStep(), AutoRateFromAnswerStep(),
            AutoRateFixedStep(), MarkFailedStep(),
            WaitForTapStep(), NextCardStep()
        )
        val json = kotlinx.serialization.json.Json
        assertEquals(17, steps.size)
        steps.forEach { step ->
            val encoded = json.encodeToString(ModeStep.serializer(), step)
            assertEquals(step, json.decodeFromString(ModeStep.serializer(), encoded))
        }
    }

    @Test
    fun `normalization clamps threshold`() {
        val step = ListenAndCheckStep(successThreshold = 150)
        val normalized = com.raycoll.app.domain.normalize.normalizeModeStep(
            step
        ) as ListenAndCheckStep
        assertEquals(100, normalized.successThreshold)
    }

    @Test
    fun `wait defaults to 500 milliseconds`() {
        assertEquals(500L, WaitStep().ms)
    }

    @Test
    fun `page index is available for page-specific steps`() {
        assertEquals(2, ShowPageStep(pageIndex = 2).referencedPage)
    }

    @Test
    fun `page index is absent for show all pages`() {
        val step: ModeStep = ShowAllPagesStep()
        org.junit.Assert.assertNull(step.referencedPage)
    }
}
