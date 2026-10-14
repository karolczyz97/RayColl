package com.raycoll.app.ui.imports

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.raycoll.app.data.files.DocumentFiles
import com.raycoll.app.domain.imports.CsvSeparator
import com.raycoll.app.domain.imports.ImportProblem
import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.testing.FakeClock
import com.raycoll.app.testing.TestRepos
import com.raycoll.app.testing.VmTestHarness
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ImportViewModelTest {

    @Test
    fun `import with 21 columns is rejected without truncating data`() = runTest(dispatcher) {
        val vm = vm()
        vm.setDeckName("Too wide")
        vm.setHasHeader(false)
        vm.onSourceTextChanged((1..21).joinToString(";") { "cell $it" })
        advanceUntilIdle()
        assertEquals(21, vm.uiState.value.pageCount)
        vm.runImport(pageNames)
        advanceUntilIdle()
        assertEquals(ImportEvent.Invalid(ImportProblem.InvalidPageCount), vm.events.first())
        assertTrue(repos.localStore.snapshot().groups.isEmpty())
    }
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repos: TestRepos
    private lateinit var files: DocumentFiles
    private val pageNames = List(20) { "Page ${it + 1}" }

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        repos = VmTestHarness.create(context, FakeClock())
        files = DocumentFiles(context)
    }

    @After
    fun teardown() {
        repos.db.close()
        Dispatchers.resetMain()
    }

    private fun vm() = ImportViewModel(repos.deckRepository, files, dispatcher)

    @Test
    fun `source text detects semicolon and row count`() = runTest(dispatcher) {
        val vm = vm()
        vm.onSourceTextChanged("hello;world\nfoo;bar")
        advanceUntilIdle()
        assertEquals(CsvSeparator.SEMICOLON, vm.uiState.value.separator)
        assertEquals(2, vm.uiState.value.pageCount)
        assertEquals(2, vm.uiState.value.rows.size)
    }

    @Test
    fun `source text detects tab`() = runTest(dispatcher) {
        val vm = vm()
        vm.onSourceTextChanged("hello\tworld\nfoo\tbar")
        advanceUntilIdle()
        assertEquals(CsvSeparator.TAB, vm.uiState.value.separator)
    }

    @Test
    fun `source text detects comma`() = runTest(dispatcher) {
        val vm = vm()
        vm.onSourceTextChanged("hello,world\nfoo,bar")
        advanceUntilIdle()
        assertEquals(CsvSeparator.COMMA, vm.uiState.value.separator)
    }

    @Test
    fun `source text detects header and names`() = runTest(dispatcher) {
        val vm = vm()
        vm.onSourceTextChanged("Phrase;Translation\nhello;world")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.hasHeader)
        assertEquals(listOf("Phrase", "Translation"), vm.uiState.value.pageNames)
    }

    @Test
    fun `source text detects three pages`() = runTest(dispatcher) {
        val vm = vm()
        vm.onSourceTextChanged("a;b;c\n1;2;3\n4;5;6")
        advanceUntilIdle()
        assertEquals(3, vm.uiState.value.pageCount)
    }

    @Test
    fun `manual separator survives subsequent typing`() = runTest(dispatcher) {
        val vm = vm()
        vm.onSourceTextChanged("a;b;c")
        advanceUntilIdle()
        vm.setSeparator(CsvSeparator.COMMA)
        vm.onSourceTextChanged("x;y;z;w")
        advanceUntilIdle()
        assertEquals(CsvSeparator.COMMA, vm.uiState.value.separator)
    }

    @Test
    fun `manual header override is preserved`() = runTest(dispatcher) {
        val vm = vm()
        vm.onSourceTextChanged("phrase;translation\nhello;world")
        advanceUntilIdle()
        vm.setHasHeader(false)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.hasHeader)
        assertEquals(2, vm.uiState.value.rows.size)
    }

    @Test
    fun `editing a preview cell also changes source text`() = runTest(dispatcher) {
        val vm = vm()
        vm.onSourceTextChanged("hello;world\nfoo;bar")
        advanceUntilIdle()
        vm.updateCell(0, 0, "CHANGED;quoted")
        assertEquals("podgląd od razu", "CHANGED;quoted", vm.uiState.value.rows[0][0])
        advanceUntilIdle()
        assertTrue(vm.uiState.value.sourceText.contains("\"CHANGED;quoted\""))
        assertEquals("CHANGED;quoted", vm.uiState.value.rows[0][0])
    }

    @Test
    fun `deleting a row changes preview and source text`() = runTest(dispatcher) {
        val vm = vm()
        vm.onSourceTextChanged("row1a;row1b\nrow2a;row2b\nrow3a;row3b")
        advanceUntilIdle()
        vm.deleteRow(1)
        assertEquals(2, vm.uiState.value.rows.size)
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.rows.size)
        assertFalse(vm.uiState.value.sourceText.contains("row2a"))
    }

    @Test
    fun `missing deck name emits a validation problem and writes nothing`() = runTest(dispatcher) {
        val vm = vm()
        vm.onSourceTextChanged("hello;world")
        vm.runImport(pageNames)
        assertEquals(ImportEvent.Invalid(ImportProblem.DeckNameRequired), vm.events.first())
        assertFalse(vm.uiState.value.importing)
        assertTrue(repos.localStore.snapshot().groups.isEmpty())
    }

    @Test
    fun `empty input emits NoCards`() = runTest(dispatcher) {
        val vm = vm()
        vm.setDeckName("Deck")
        vm.runImport(pageNames)
        assertEquals(ImportEvent.Invalid(ImportProblem.NoCards), vm.events.first())
    }

    @Test
    fun `successful import creates a deck and emits Imported`() = runTest(dispatcher) {
        val vm = vm()
        vm.setDeckName("Angielski")
        vm.onSourceTextChanged("Phrase;Translation\nhello;cześć\nthanks;dzięki")
        vm.runImport(pageNames)
        advanceUntilIdle()
        assertEquals(ImportEvent.Imported, vm.events.first())
        // po udanym imporcie flaga zostaje: zdarzenie może czekać za komunikatem, a drugi klik nie doda talii
        assertTrue(vm.uiState.value.importing)
        val deck = repos.localStore.snapshot().groups.single()
        assertEquals("Angielski", deck.name)
        assertEquals(listOf(listOf("hello", "cześć"), listOf("thanks", "dzięki")), deck.cards.map { it.pages })
    }

    @Test
    fun `a validation problem does not prevent the next import`() = runTest(dispatcher) {
        val vm = vm()
        vm.onSourceTextChanged("hello;world")
        vm.runImport(pageNames)
        assertEquals(ImportEvent.Invalid(ImportProblem.DeckNameRequired), vm.events.first())
        vm.setDeckName("Recovered")
        vm.runImport(pageNames)
        advanceUntilIdle()
        assertEquals(ImportEvent.Imported, vm.events.first())
    }

    @Test
    fun `canImport requires name and nonempty rows`() = runTest(dispatcher) {
        val vm = vm()
        assertFalse(vm.uiState.value.canImport)
        vm.setDeckName("X")
        vm.onSourceTextChanged("hello;world")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.canImport)
    }

    @Test
    fun `large import pads uneven rows to detected page count`() = runTest(dispatcher) {
        val vm = vm()
        val csv = buildString {
            append("Word;Translation;Example\n")
            repeat(300) { i ->
                append(if (i % 2 == 0) "row_${i}_col0;row_${i}_col1;row_${i}_col2\n" else "row_${i}_col0\n")
            }
        }
        vm.onSourceTextChanged(csv)
        advanceUntilIdle()
        assertEquals(3, vm.uiState.value.pageCount)
        assertEquals(300, vm.uiState.value.rows.size)
        assertEquals(listOf("row_1_col0", "", ""), vm.uiState.value.rows[1])
    }

    @Test
    fun `editing a missing preview column is a safe no-op`() = runTest(dispatcher) {
        val vm = vm()
        vm.onSourceTextChanged("a;b\nc;d")
        advanceUntilIdle()
        vm.updateCell(0, 3, "outside")
        assertEquals(listOf("a", "b"), vm.uiState.value.rows[0])
    }

    @Test
    fun `typing is immediate but parsing waits 150 milliseconds`() = runTest(dispatcher) {
        val vm = vm()
        vm.onSourceTextChanged("hello;world")
        assertEquals("hello;world", vm.uiState.value.sourceText)
        runCurrent()
        advanceTimeBy(149)
        runCurrent()
        assertTrue(vm.uiState.value.rows.isEmpty())
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(listOf("hello", "world")), vm.uiState.value.rows)
    }

    @Test
    fun `rapid typing parses the latest text only`() = runTest(dispatcher) {
        val vm = vm()
        vm.onSourceTextChanged("a;b")
        vm.onSourceTextChanged("a;b\nc;d")
        vm.onSourceTextChanged("a;b\nc;d\ne;f")
        assertTrue(vm.uiState.value.rows.isEmpty())
        advanceUntilIdle()
        assertEquals(3, vm.uiState.value.rows.size)
    }

    @Test
    fun `import before debounce parses the latest source text`() = runTest(dispatcher) {
        val vm = vm()
        vm.setDeckName("DebounceDeck")
        vm.onSourceTextChanged("Phrase;Translation\ncat;kot")
        vm.runImport(pageNames)
        advanceUntilIdle()
        assertEquals(ImportEvent.Imported, vm.events.first())
        assertEquals(listOf("cat", "kot"), repos.localStore.snapshot().groups.single().cards.single().pages)
    }

    @Test
    fun `parsing does not overwrite a concurrently edited deck name`() = runTest(dispatcher) {
        val vm = vm()
        vm.onSourceTextChanged("Phrase;Translation\ncat;kot")
        vm.setDeckName("ConcurrentDeckName")
        advanceUntilIdle()
        assertEquals("ConcurrentDeckName", vm.uiState.value.deckName)
        assertEquals(listOf(listOf("cat", "kot")), vm.uiState.value.rows)
    }

    // --- ręczne ustawienia kolumn (nazwa/język) ---

    @Test
    fun `import trims names and uses localized defaults for whitespace only names`() = runTest(dispatcher) {
        val vm = vm()
        vm.setDeckName("  Talia  ")
        vm.onSourceTextChanged("hello;cześć")
        advanceUntilIdle()
        vm.setPageName(0, "  Pytanie  ")
        vm.setPageName(1, "   ")
        vm.runImport(listOf("Strona 1", "Strona 2"))
        advanceUntilIdle()
        assertEquals(ImportEvent.Imported, vm.events.first())
        val deck = repos.localStore.snapshot().groups.single()
        assertEquals("Talia", deck.name)
        assertEquals(listOf("Pytanie", "Strona 2"), deck.pageNames)
    }

    private suspend fun TestScope.importedDeck(vm: ImportViewModel): FlashcardGroup {
        vm.runImport(pageNames)
        advanceUntilIdle()
        assertEquals(ImportEvent.Imported, vm.events.first())
        return repos.localStore.snapshot().groups.single()
    }

    @Test
    fun `manual language and name of a header column are imported as shown`() = runTest(dispatcher) {
        val vm = vm()
        vm.setDeckName("Deck")
        vm.onSourceTextChanged("Word;Translation\nhello;cześć")
        advanceUntilIdle()
        assertEquals(listOf("en-US", "en-US"), vm.uiState.value.pageLanguages)
        vm.setPageLanguage(1, "pl-PL")
        vm.setPageName(1, "Polski")

        val deck = importedDeck(vm)
        assertEquals(listOf("en-US", "pl-PL"), deck.pageLanguages)
        assertEquals(listOf("Word", "Polski"), deck.pageNames)
    }

    @Test
    fun `manual column settings survive further typing and the final parse`() = runTest(dispatcher) {
        val vm = vm()
        vm.setDeckName("Deck")
        vm.onSourceTextChanged("Word;Translation\nhello;cześć")
        advanceUntilIdle()
        vm.setPageLanguage(1, "pl-PL")
        vm.onSourceTextChanged("Word;Translation\nhello;cześć\ncat;kot")
        advanceUntilIdle()
        assertEquals("pl-PL", vm.uiState.value.pageLanguages[1])
        // klik od razu po kolejnej literze: finalne przetworzenie też zachowuje wybór
        vm.onSourceTextChanged("Word;Translation\nhello;cześć\ncat;kot\ndog;pies")
        val deck = importedDeck(vm)
        assertEquals(listOf("en-US", "pl-PL"), deck.pageLanguages)
        assertEquals(3, deck.cards.size)
    }

    @Test
    fun `manual settings without header stay on their column`() = runTest(dispatcher) {
        val vm = vm()
        vm.setDeckName("Deck")
        vm.onSourceTextChanged("hello;cześć\ncat;kot")
        advanceUntilIdle()
        assertFalse(vm.uiState.value.hasHeader)
        vm.setPageLanguage(0, "de-DE")
        vm.setPageName(0, "Niemiecki")
        vm.onSourceTextChanged("hello;cześć\ncat;kot\ndog;pies")

        val deck = importedDeck(vm)
        assertEquals(listOf("de-DE", "en-US"), deck.pageLanguages)
        assertEquals("Niemiecki", deck.pageNames[0])
    }

    @Test
    fun `reordered header columns drop settings instead of moving them to another column`() = runTest(dispatcher) {
        val vm = vm()
        vm.onSourceTextChanged("Word;Translation\nhello;cześć")
        advanceUntilIdle()
        vm.setPageLanguage(1, "pl-PL")
        vm.onSourceTextChanged("Translation;Word\ncześć;hello")
        advanceUntilIdle()
        assertEquals(ImportEvent.ColumnSettingsReset, vm.events.first())
        assertEquals("język wykryty z nowego nagłówka", "en-US", vm.uiState.value.pageLanguages[1])
    }

    @Test
    fun `removed column drops its settings`() = runTest(dispatcher) {
        val vm = vm()
        vm.onSourceTextChanged("a;b;c\n1;2;3")
        advanceUntilIdle()
        vm.setPageLanguage(2, "it-IT")
        vm.setPageLanguage(0, "de-DE")
        vm.onSourceTextChanged("a;b\n1;2")
        advanceUntilIdle()
        assertEquals(ImportEvent.ColumnSettingsReset, vm.events.first())
        assertEquals(listOf("de-DE", "en-US"), vm.uiState.value.pageLanguages)
    }

    @Test
    fun `separator change drops settings of columns that no longer match`() = runTest(dispatcher) {
        val vm = vm()
        vm.setDeckName("Deck")
        vm.onSourceTextChanged("Word,Example;Translation\nhello,hi;cześć")
        advanceUntilIdle()
        assertEquals(CsvSeparator.SEMICOLON, vm.uiState.value.separator)
        vm.setPageLanguage(1, "pl-PL")

        // przecinek: nagłówek drugiej kolumny jest inny — ustawienie nie przechodzi na nią
        vm.setSeparator(CsvSeparator.COMMA)
        advanceUntilIdle()
        assertEquals(ImportEvent.ColumnSettingsReset, vm.events.first())
        assertEquals(listOf("Word", "Example;Translation"), vm.uiState.value.pageNames)
        assertEquals("en-US", vm.uiState.value.pageLanguages[1])

        // powrót do średnika i ponowny wybór — import używa tego, co widać
        vm.setSeparator(CsvSeparator.SEMICOLON)
        advanceUntilIdle()
        vm.setPageLanguage(1, "pl-PL")
        val deck = importedDeck(vm)
        assertEquals("pl-PL", deck.pageLanguages[1])
        assertEquals(listOf(listOf("hello,hi", "cześć")), deck.cards.map { it.pages })
    }

    // --- finalne przetwarzanie i edycja podglądu ---

    @Test
    fun `final parse runs on the parse dispatcher and a second click does not import twice`() = runTest(dispatcher) {
        val parseDispatcher = StandardTestDispatcher(testScheduler, name = "parse")
        val vm = ImportViewModel(repos.deckRepository, files, parseDispatcher)
        vm.setDeckName("Deck")
        vm.onSourceTextChanged("Word;Translation\ncat;kot")
        vm.runImport(pageNames)
        vm.runImport(pageNames)
        // kliknięcie tylko zaznacza import; przetwarzanie czeka na dispatcher parsowania
        assertTrue(vm.uiState.value.importing)
        assertFalse(vm.uiState.value.canImport)
        assertTrue(vm.uiState.value.rows.isEmpty())
        advanceUntilIdle()
        assertEquals(ImportEvent.Imported, vm.events.first())
        assertEquals(1, repos.localStore.snapshot().groups.size)
        assertEquals(listOf("cat", "kot"), repos.localStore.snapshot().groups.single().cards.single().pages)
        assertTrue(vm.uiState.value.importing)
    }

    @Test
    fun `a click after a successful import does not add the deck again`() = runTest(dispatcher) {
        val vm = vm()
        vm.setDeckName("Once")
        vm.onSourceTextChanged("hello;world")
        vm.runImport(pageNames)
        advanceUntilIdle()
        // Imported czeka w kanale (ekran pokazuje komunikat), a przycisk jest jeszcze klikalny
        assertFalse(vm.uiState.value.canImport)
        vm.runImport(pageNames)
        advanceUntilIdle()
        assertEquals(1, repos.localStore.snapshot().groups.size)
    }

    @Test
    fun `a failed import re-enables the button`() = runTest(dispatcher) {
        val vm = vm()
        vm.onSourceTextChanged("hello;world")
        vm.runImport(pageNames)
        advanceUntilIdle()
        assertEquals(ImportEvent.Invalid(ImportProblem.DeckNameRequired), vm.events.first())
        assertFalse(vm.uiState.value.importing)
    }

    @Test
    fun `cell edits are imported even before the source text is rebuilt`() = runTest(dispatcher) {
        val vm = vm()
        vm.setDeckName("Deck")
        vm.onSourceTextChanged("a;b\nc;d\ne;f")
        advanceUntilIdle()
        vm.updateCell(0, 1, "B!")
        vm.deleteRow(1)
        val deck = importedDeck(vm)
        assertEquals(listOf(listOf("a", "B!"), listOf("e", "f")), deck.cards.map { it.pages })
    }

    @Test
    fun `a late parse result does not revert cell edits`() = runTest(dispatcher) {
        val vm = vm()
        vm.onSourceTextChanged("a;b\nc;d")
        advanceUntilIdle()
        vm.onSourceTextChanged("a;b\nc;d\n") // przetworzenie zaplanowane, jeszcze nie wykonane
        vm.updateCell(1, 0, "C!")
        advanceUntilIdle()
        assertEquals(listOf(listOf("a", "b"), listOf("C!", "d")), vm.uiState.value.rows)
        assertEquals("a;b\nC!;d", vm.uiState.value.sourceText)
    }

    @Test
    fun `rebuilt text keeps the original header when a page was renamed`() = runTest(dispatcher) {
        val vm = vm()
        vm.onSourceTextChanged("Word;Translation\nhello;cześć")
        advanceUntilIdle()
        vm.setPageName(1, "Polski")
        vm.setPageLanguage(1, "pl-PL")
        vm.updateCell(0, 0, "hi")
        advanceUntilIdle()
        assertEquals("Word;Translation\nhi;cześć", vm.uiState.value.sourceText)
        vm.setSeparator(CsvSeparator.SEMICOLON)
        advanceUntilIdle()
        assertEquals(listOf("Word", "Polski"), vm.uiState.value.pageNames)
        assertEquals("pl-PL", vm.uiState.value.pageLanguages[1])
    }
}
