package com.raycoll.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PersistedTextFieldTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `blur retains latest edit until the database confirms it then accepts external changes`() {
        val stored = mutableStateOf("Old")
        val written = mutableListOf<String>()
        compose.setContent {
            Column {
                PersistedTextField(stored.value, onValueChange = { written += it }, label = "Name")
                OutlinedTextField(value = "", onValueChange = {}, label = { Text("Other") })
            }
        }
        field().performClick().performTextReplacement("Intermediate")
        field().performTextReplacement("  Latest  ")
        otherField().performClick()
        field().assertIsNotFocused()
        compose.mainClock.advanceTimeBy(1_000)
        assertField("  Latest  ")

        compose.runOnIdle { stored.value = "Intermediate" }
        assertField("  Latest  ")
        compose.runOnIdle { stored.value = "Latest" }
        assertField("Latest")
        compose.runOnIdle { stored.value = "Remote" }
        assertField("Remote")
        assertEquals(listOf("Intermediate", "  Latest  "), written)
    }

    @Test
    fun `invalid name explains the error and reverts to the saved name on blur`() {
        compose.setContent {
            Column {
                PersistedTextField(
                    storedValue = "Saved",
                    onValueChange = {},
                    label = "Name",
                    isError = String::isBlank,
                    errorText = "Name required"
                )
                OutlinedTextField(value = "", onValueChange = {}, label = { Text("Other") })
            }
        }
        field().performClick().performTextReplacement("")
        compose.onNodeWithText("Name required").assertIsDisplayed()
        otherField().performClick()
        field().assertIsNotFocused()
        assertField("Saved")
    }

    private fun field() = compose.onNode(hasSetTextAction() and hasText("Name"))

    private fun otherField() = compose.onNode(hasSetTextAction() and hasText("Other"))

    private fun assertField(expected: String) {
        compose.waitUntil(5_000) {
            field().fetchSemanticsNode().config[SemanticsProperties.EditableText].text == expected
        }
        assertEquals(expected, field().fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
    }
}
