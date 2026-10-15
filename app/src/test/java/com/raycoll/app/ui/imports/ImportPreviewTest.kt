package com.raycoll.app.ui.imports

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.raycoll.app.ui.theme.RayCollTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "de-w360dp-h800dp")
class ImportPreviewTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `two columns stay side by side and three five and twenty stack with editable labels`() {
        val count = mutableStateOf(2)
        var edited = -1
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                RayCollTheme(useSystemColors = false) {
                    Box(Modifier.width(360.dp).height(600.dp)) {
                        LazyColumn {
                            item {
                                PreviewRow(
                                    rowNumber = 7,
                                    cells = List(count.value) { "value$it" },
                                    pageNames = List(count.value) { "Übersetzung $it" },
                                    enabled = true,
                                    onCellChange = { index, _ -> edited = index },
                                    onDelete = {}
                                )
                            }
                        }
                    }
                }
            }
        }
        fun field(index: Int) = hasSetTextAction() and hasText("value$index")
        val first = compose.onNode(field(0)).getUnclippedBoundsInRoot()
        val second = compose.onNode(field(1)).getUnclippedBoundsInRoot()
        assertEquals(first.top, second.top)
        for (columns in listOf(3, 5, 20)) {
            count.value = columns
            compose.waitForIdle()
            val top = compose.onNode(field(0)).getUnclippedBoundsInRoot()
            val below = compose.onNode(field(1)).getUnclippedBoundsInRoot()
            assertTrue(below.top > top.top)
            assertTrue(top.right <= 360.dp)
            val list = compose.onNode(hasScrollToNodeAction())
            list.performScrollToNode(field(columns - 1))
            compose.onNode(field(columns - 1)).assertIsDisplayed().performTextReplacement("changed")
            assertEquals(columns - 1, edited)
        }
    }
}
