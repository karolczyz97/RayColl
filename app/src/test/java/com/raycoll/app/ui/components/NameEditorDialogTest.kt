package com.raycoll.app.ui.components

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NameEditorDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `restored draft waits for a retained save but can be saved again after process restart`() {
        val saving = mutableStateOf(false)
        var writes = 0
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            NameEditorDialog(
                title = "Name",
                draftKey = "deck",
                initialValue = "Old",
                saving = saving.value,
                onSave = {
                    writes++
                    saving.value = true
                },
                onDismiss = {}
            )
        }
        compose.onNode(hasSetTextAction()).performTextReplacement("Draft")
        compose.onNodeWithText("Save").performClick()
        assertEquals(1, writes)
        // Odtworzenie ekranu: ViewModel nadal zapisuje, drugi zapis jest zablokowany.
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Save").assertIsNotEnabled()
        compose.onNodeWithText("Cancel").assertIsNotEnabled()

        // Nowy proces: starego zadania już nie ma, ale szkic wraca z zapisanego stanu.
        compose.runOnIdle { saving.value = false }
        restoration.emulateSavedInstanceStateRestore()
        assertEquals(
            "Draft",
            compose.onNode(hasSetTextAction()).fetchSemanticsNode().config[SemanticsProperties.EditableText].text
        )
        compose.onNodeWithText("Cancel").assertIsEnabled()
        compose.onNodeWithText("Save").assertIsEnabled().performClick()
        assertEquals(2, writes)
    }
}
