package com.raycoll.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class ScreenScaffoldWidthTest {
    @get:Rule val compose = createComposeRule()

    private fun showScreen() {
        compose.setContent {
            ScreenScaffold(title = "Screen") {
                Box(Modifier.fillMaxSize().testTag("content"))
            }
        }
    }

    @Test
    @Config(qualifiers = "w840dp-h1000dp")
    fun `large screen centers a layout limited to 640 dp`() {
        showScreen()
        val root = compose.onRoot().getUnclippedBoundsInRoot()
        val content = compose.onNodeWithTag("content").getUnclippedBoundsInRoot()
        assertTrue((root.right - root.left).value > 640f)
        assertEquals(640f, (content.right - content.left).value, 0.1f)
        assertEquals(((root.right - root.left).value - 640f) / 2f, content.left.value, 0.1f)
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp")
    fun `phone layout still occupies the available width`() {
        showScreen()
        val root = compose.onRoot().getUnclippedBoundsInRoot()
        val content = compose.onNodeWithTag("content").getUnclippedBoundsInRoot()
        assertTrue((root.right - root.left).value < 640f)
        assertEquals((root.right - root.left).value, (content.right - content.left).value, 0.1f)
        assertEquals(0f, content.left.value, 0.1f)
    }
}
