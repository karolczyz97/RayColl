package com.raycoll.app.ui.imports

import android.content.Context
import android.os.Looper
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.raycoll.app.data.files.DocumentFiles
import com.raycoll.app.testing.FakeClock
import com.raycoll.app.testing.TestRepos
import com.raycoll.app.testing.VmTestHarness
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** Ekran importu: wklejony tekst, edycja komórki podglądu i import tego, co widać. */
@RunWith(RobolectricTestRunner::class)
class ImportScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var repos: TestRepos

    @Before
    fun setup() {
        repos = VmTestHarness.create(context, FakeClock())
    }

    @After
    fun teardown() {
        repos.db.close()
    }

    /** Opóźnienia przetwarzania (150/300 ms) biegną na zegarze głównego wątku Robolectric. */
    private fun settle() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
        compose.waitForIdle()
    }

    private fun textField(text: String) = compose.onNode(hasSetTextAction() and hasText(text))

    private fun cell(value: String) = compose.onNode(
        hasSetTextAction() and SemanticsMatcher("komórka $value") { node ->
            node.config.getOrElseNullable(SemanticsProperties.EditableText) { null }?.text == value
        }
    )

    @Test
    fun `typed csv, an edited preview cell and the import button produce the deck shown on screen`() {
        var finished = false
        val vm = ImportViewModel(repos.deckRepository, DocumentFiles(context), Dispatchers.Unconfined)
        compose.setContent { ImportScreen(onFinished = { finished = true }, viewModel = vm) }

        textField("Deck name").performTextInput("Imported")
        textField("Paste CSV / TSV text here").performTextInput("Word;Translation\nhello;cześć\ncat;kot")
        settle()
        assertEquals(2, vm.uiState.value.rows.size)
        val list = compose.onNode(hasScrollToNodeAction())
        list.performScrollToNode(hasText("Preview (2)"))

        list.performScrollToNode(hasSetTextAction() and hasText("cat"))
        cell("cat").performTextReplacement("kitten")
        compose.onNode(hasText("Import flashcards") and hasClickAction()).performClick()
        settle()
        compose.waitUntil(5_000) {
            settle()
            finished
        }

        val deck = runBlocking { repos.localStore.snapshot() }.groups.single()
        assertEquals("Imported", deck.name)
        assertEquals(listOf("Word", "Translation"), deck.pageNames)
        assertEquals(listOf(listOf("hello", "cześć"), listOf("kitten", "kot")), deck.cards.map { it.pages })
    }

    @Test
    fun `import button stays on screen and says why it is disabled`() {
        val vm = ImportViewModel(repos.deckRepository, DocumentFiles(context), Dispatchers.Unconfined)
        compose.setContent { ImportScreen(onFinished = {}, viewModel = vm) }
        val importButton = compose.onNode(hasText("Import flashcards") and hasClickAction())

        textField("Paste CSV / TSV text here").performTextInput("Word;Translation\nhello;cześć")
        settle()
        importButton.assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithText("Enter a deck name.").assertIsDisplayed()

        textField("Deck name").performTextInput("Named")
        settle()
        compose.onNodeWithText("Enter a deck name.").assertDoesNotExist()
        importButton.assertIsEnabled()
    }

    @Test
    fun `preview rows are headed by the page names`() {
        val vm = ImportViewModel(repos.deckRepository, DocumentFiles(context), Dispatchers.Unconfined)
        compose.setContent { ImportScreen(onFinished = {}, viewModel = vm) }

        textField("Paste CSV / TSV text here").performTextInput("Word;Translation\nhello;cześć")
        settle()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Preview (1)"))

        compose.onNode(hasText("Word") and !hasSetTextAction()).assertExists()
        compose.onNode(hasText("Translation") and !hasSetTextAction()).assertExists()
    }
}
