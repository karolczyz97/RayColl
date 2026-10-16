package com.raycoll.app.ui.navigation

import android.app.Activity
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ScreenResultTest {
    private val open = OpenForResult<Unit> { _, _ -> Intent() }

    @Test
    fun `result of a closed screen reaches the screen that opened it`() {
        val archived = ScreenResult.Archived("deck")
        assertEquals(archived, open.parseResult(Activity.RESULT_OK, screenResultIntent(archived)))
        assertEquals(
            ScreenResult.Imported,
            open.parseResult(Activity.RESULT_OK, screenResultIntent(ScreenResult.Imported))
        )
    }

    @Test
    fun `screen left with back returns no result`() {
        assertNull(open.parseResult(Activity.RESULT_CANCELED, null))
        assertNull(open.parseResult(Activity.RESULT_OK, screenResultIntent(null)))
    }
}
