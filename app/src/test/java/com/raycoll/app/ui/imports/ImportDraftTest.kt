package com.raycoll.app.ui.imports

import android.content.Context
import android.net.Uri
import android.os.Looper
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import com.raycoll.app.R
import com.raycoll.app.data.files.DocumentFiles
import com.raycoll.app.testing.FakeClock
import com.raycoll.app.testing.VmTestHarness
import java.io.File
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class ImportDraftTest {
    @get:Rule val compose = createComposeRule()

    @get:Rule val folder = TemporaryFolder()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val repos = VmTestHarness.create(context, FakeClock())
    private lateinit var vm: ImportViewModel

    @Before fun setup() {
        vm = ImportViewModel(repos.deckRepository, DocumentFiles(context), Dispatchers.Unconfined)
        vm.setDeckName("Draft")
    }

    @Test
    fun `toolbar and system back both confirm before discarding and dialog restores`() {
        var finished = false
        lateinit var back: OnBackPressedDispatcher
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            back = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            ImportScreen(onFinished = { finished = true }, viewModel = vm)
        }
        compose.onNodeWithContentDescription(context.getString(R.string.btn_back)).performClick()
        compose.onNodeWithText(context.getString(R.string.import_discard_desc)).assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText(context.getString(R.string.import_discard_desc)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.btn_cancel)).performClick()
        assertFalse(finished)
        compose.runOnUiThread { back.onBackPressed() }
        compose.onNodeWithText(context.getString(R.string.import_discard_desc)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.mode_editor_discard_confirm)).performClick()
        org.junit.Assert.assertTrue(finished)
    }

    @Test
    fun `selected file does not replace an existing source until confirmed`() {
        val file = File(folder.root, "new.csv").apply { writeText("new;content") }
        val registry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(
                requestCode: Int,
                contract: ActivityResultContract<I, O>,
                input: I,
                options: ActivityOptionsCompat?
            ) {
                dispatchResult(requestCode, Uri.fromFile(file))
            }
        }
        val owner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry = registry
        }
        vm.onSourceTextChanged("old;content")
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                ImportScreen(onFinished = {}, viewModel = vm)
            }
        }
        val uploadLabel = context.getString(R.string.import_upload_file)
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(uploadLabel))
        compose.onNodeWithText(uploadLabel).performClick()
        compose.onNodeWithText(context.getString(R.string.import_replace_desc)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.btn_cancel)).performClick()
        assertEquals("old;content", vm.uiState.value.sourceText)
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(uploadLabel))
        compose.onNodeWithText(uploadLabel).performClick()
        compose.onNodeWithText(context.getString(R.string.import_replace_confirm)).performClick()
        compose.waitUntil(5_000) {
            shadowOf(Looper.getMainLooper()).idle()
            vm.uiState.value.sourceText == "new;content"
        }
    }

    @Test
    fun `overwide source is rejected before preview fields are rendered`() {
        vm.setHasHeader(false)
        vm.onSourceTextChanged((1..21).joinToString(";") { "cell$it" })
        compose.setContent { ImportScreen(onFinished = {}, viewModel = vm) }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200))
        compose.waitUntil(5_000) { vm.uiState.value.pageCount == 21 }
        compose.onNode(hasSetTextAction() and hasText("cell21")).assertDoesNotExist()
        assertEquals(21, vm.uiState.value.rows.single().size)
    }
}
