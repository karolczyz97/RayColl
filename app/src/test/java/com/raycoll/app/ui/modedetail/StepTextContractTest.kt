package com.raycoll.app.ui.modedetail

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.raycoll.app.R
import com.raycoll.app.domain.model.DynamicPauseStep
import com.raycoll.app.domain.steps.ModeIssue
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class StepTextContractTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `compiled delete descriptions retain locale quotation marks around the name`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        listOf(
            "en" to ('“' to '”'),
            "pl" to ('„' to '”'),
            "de" to ('„' to '“'),
            "es" to ('«' to '»'),
            "it" to ('«' to '»')
        ).forEach { (language, quotes) ->
            val localized = context.createConfigurationContext(
                Configuration(context.resources.configuration).apply {
                    setLocale(Locale.forLanguageTag(language))
                }
            )
            val message = localized.getString(R.string.dialog_delete_permanent_desc, "NAME")
            assertTrue("$language: $message", "${quotes.first}NAME${quotes.second}" in message)
        }
    }

    @Test
    @Config(qualifiers = "de")
    fun `validation names the same steps offered by the editor and pause preserves decimal precision`() {
        val issues = mutableListOf<String>()
        val labels = mutableListOf<String>()
        var title = ""
        compose.setContent {
            val resources = LocalResources.current
            labels.clear()
            labels += resources.getString(R.string.step_show_ratings)
            labels += resources.getString(R.string.step_next_card)
            labels += resources.getString(R.string.step_auto_rate_from_answer)
            labels += resources.getString(R.string.step_type_listen_and_check)
            issues.clear()
            issues += issueText(ModeIssue.NoTerminalStep)
            issues += issueText(ModeIssue.AutoRateWithoutStt)
            title = stepTitle(DynamicPauseStep(pauseMultiplier = 1.25))
        }
        compose.waitForIdle()
        assertTrue(labels.take(2).all { it in issues[0] })
        assertTrue(labels.drop(2).all { it in issues[1] })
        assertTrue(title, "1,25" in title)
        assertEquals("1.25", formatMultiplier(1.25, Locale.ENGLISH))
    }
}
