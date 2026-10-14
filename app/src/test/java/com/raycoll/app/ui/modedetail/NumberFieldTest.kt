package com.raycoll.app.ui.modedetail

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NumberFieldTest {

    @get:Rule
    val compose = createComposeRule()

    private var multiplier by mutableStateOf(1.0)
    private var count by mutableStateOf(3)
    private var multiplierValid = true

    private fun setContent() = compose.setContent {
        NumberField(
            label = "multiplier",
            value = multiplier,
            range = 0.0..5.0,
            numberFormat = DecimalNumberFormat,
            onChange = { multiplier = it },
            onValidityChange = { multiplierValid = it }
        )
        NumberField(
            label = "count",
            value = count,
            range = 1..100,
            numberFormat = IntNumberFormat,
            onChange = { count = it }
        )
    }

    private fun field(label: String) = compose.onNode(hasSetTextAction() and hasText(label))

    private fun editableText(label: String): String =
        field(label).fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    private fun selection(label: String): TextRange =
        field(label).fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange]

    @Test
    fun `zamiana 1 na 2,5 znak po znaku nie przeskakuje kursorem ani nie dopisuje zera`() {
        setContent()
        field("multiplier").performTextClearance()
        assertEquals("", editableText("multiplier"))
        assertEquals("puste pole nie zmienia modelu", 1.0, multiplier, 0.0)
        assertFalse(multiplierValid)

        field("multiplier").performTextInput("2")
        assertEquals(2.0, multiplier, 0.0)
        assertEquals("2", editableText("multiplier"))

        field("multiplier").performTextInput(",")
        assertEquals("2,", editableText("multiplier"))
        assertEquals(TextRange(2), selection("multiplier"))

        field("multiplier").performTextInput("5")
        assertEquals("2,5", editableText("multiplier"))
        assertEquals(TextRange(3), selection("multiplier"))
        assertEquals(2.5, multiplier, 0.0)
        assertTrue(multiplierValid)
    }

    @Test
    fun `liczba spoza zakresu pokazuje blad, nie trafia do modelu i po zatwierdzeniu jest przycinana`() {
        setContent()
        field("multiplier").performTextReplacement("50")
        compose.waitForIdle()
        assertEquals("50", editableText("multiplier"))
        assertEquals(1.0, multiplier, 0.0)
        assertFalse(multiplierValid)
        field("multiplier").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error))

        field("multiplier").performImeAction()
        compose.waitForIdle()
        assertEquals(5.0, multiplier, 0.0)
        assertEquals("5", editableText("multiplier"))
        assertTrue(multiplierValid)
    }

    @Test
    fun `niepoprawny tekst po zatwierdzeniu wraca do wartosci modelu`() {
        setContent()
        field("count").performTextClearance()
        assertEquals(3, count)
        field("count").performImeAction()
        compose.waitForIdle()
        assertEquals("3", editableText("count"))
        assertEquals(3, count)
    }

    @Test
    fun `zmiana z zewnatrz odswieza nieaktywne pole`() {
        setContent()
        field("count").assertTextContains("3")
        count = 42
        compose.waitForIdle()
        assertEquals("42", editableText("count"))
        multiplier = 0.25
        compose.waitForIdle()
        assertEquals("0.25", editableText("multiplier"))
    }
}
