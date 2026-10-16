package com.raycoll.app.ui.decksettings

import android.content.Context
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.raycoll.app.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DeleteDeckImeTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `keyboard done deletes only after the complete confirmation word`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val token = context.getString(R.string.delete_confirm_token)
        var deleted = 0
        compose.setContent { DeleteDeckDialog("Deck", onConfirm = { deleted++ }, onDismiss = {}) }
        val field = compose.onNode(hasSetTextAction())
        field.performTextReplacement(token.dropLast(1))
        field.performImeAction()
        assertEquals(0, deleted)
        field.performTextReplacement(token)
        field.performImeAction()
        assertEquals(1, deleted)
    }
}
