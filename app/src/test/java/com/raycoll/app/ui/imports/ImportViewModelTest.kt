package com.raycoll.app.ui.imports

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import com.raycoll.app.ui.VmTestHarness
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class ImportViewModelTest {

    @get:Rule val instantExecutorRule = InstantTaskExecutorRule()

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repos: com.raycoll.app.ui.TestRepos

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        repos = VmTestHarness.create(ApplicationProvider.getApplicationContext(), com.raycoll.app.ui.FakeClock())
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
        repos.db.close()
    }

    private fun vm() = ImportViewModel(repos.deckRepository)

    @Test
    fun `setSourceText auto-detekcja separatora semicolon`() {
        val vm = vm()
        vm.setSourceText("hello;world\nfoo;bar")
        val s = vm.uiState.value
        assertEquals("semicolon", s.separatorKey)
        assertEquals(2, s.pageCount)
        assertEquals(2, s.dataRows.size)
    }

    @Test
    fun `setSourceText auto-detekcja separatora tab`() {
        val vm = vm()
        vm.setSourceText("hello\tworld\nfoo\tbar")
        assertEquals("tab", vm.uiState.value.separatorKey)
    }

    @Test
    fun `setSourceText auto-detekcja separatora comma`() {
        val vm = vm()
        vm.setSourceText("hello,world\nfoo,bar")
        assertEquals("comma", vm.uiState.value.separatorKey)
    }

    @Test
    fun `setSourceText detekcja nagłówka`() {
        val vm = vm()
        vm.setSourceText("Phrase;Translation\nhello;world")
        val s = vm.uiState.value
        assertTrue("Nagłówek powinien być wykryty", s.hasHeader)
        assertEquals(listOf("Phrase", "Translation"), s.pageNames)
    }

    @Test
    fun `setSourceText detekcja liczby stron 3`() {
        val vm = vm()
        vm.setSourceText("a;b;c\n1;2;3\n4;5;6")
        assertEquals(3, vm.uiState.value.pageCount)
    }

    @Test
    fun `setSeparator ręczny override nie jest nadpisywany`() {
        val vm = vm()
        vm.setSourceText("a;b;c")
        vm.setSeparator("comma")
        vm.setSourceText("x;y;z;w")
        assertEquals("comma", vm.uiState.value.separatorKey)
    }

    @Test
    fun `setHasHeader ręczny override`() {
        val vm = vm()
        vm.setSourceText("phrase;translation\nhello;world")
        vm.setHasHeader(false)
        assertFalse(vm.uiState.value.hasHeader)
    }

    @Test
    fun `updateCell aktualizuje tekst źródłowy`() {
        val vm = vm()
        vm.setSourceText("hello;world\nfoo;bar")
        vm.updateCell(0, 0, "CHANGED")
        assertTrue(vm.uiState.value.sourceText.contains("CHANGED"))
        assertEquals("CHANGED", vm.uiState.value.dataRows[0][0])
    }

    @Test
    fun `deleteRow usuwa wiersz i aktualizuje tekst`() {
        val vm = vm()
        vm.setSourceText("row1a;row1b\nrow2a;row2b\nrow3a;row3b")
        vm.deleteRow(1)
        val s = vm.uiState.value
        assertEquals(2, s.dataRows.size)
        assertFalse(s.sourceText.contains("row2a"))
    }

    @Test
    fun `runImport pusta nazwa ustawia errorMsg`() = runTest(dispatcher) {
        val vm = vm()
        vm.setSourceText("hello;world\nfoo;bar")
        var successCalled = false
        vm.runImport { successCalled = true }
        advanceUntilIdle()
        assertFalse(successCalled)
        assertNotNull(vm.uiState.value.errorMsg)
    }

    @Test
    fun `runImport brak wierszy ustawia errorMsg`() = runTest(dispatcher) {
        val vm = vm()
        vm.setDeckName("Nowa Talia")
        vm.setSourceText("")
        var successCalled = false
        vm.runImport { successCalled = true }
        advanceUntilIdle()
        assertFalse(successCalled)
    }

    @Test
    fun `runImport poprawne tworzy talię z kartami`() = runTest(dispatcher) {
        val vm = vm()
        vm.setDeckName("Angielski")
        vm.setSourceText("Phrase;Translation\nhello;cześć\nthanks;dzięki")
        var successCalled = false
        vm.runImport { successCalled = true }
        advanceUntilIdle()
        assertTrue(successCalled)
        assertNull(vm.uiState.value.errorMsg)
        val snapshot = repos.deckRepository.getSnapshot()
        assertEquals(1, snapshot.groups.size)
        assertEquals("Angielski", snapshot.groups[0].name)
        assertEquals(2, snapshot.groups[0].cards.size)
    }

    @Test
    fun `clearError czyści komunikat błędu`() = runTest(dispatcher) {
        val vm = vm()
        vm.setSourceText("hello;world")
        vm.runImport { }
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.errorMsg)
        vm.clearError()
        assertNull(vm.uiState.value.errorMsg)
    }

    @Test
    fun `canImport flaga`() {
        val vm = vm()
        assertFalse(vm.uiState.value.canImport)
        vm.setDeckName("X")
        vm.setSourceText("hello;world")
        assertTrue(vm.uiState.value.canImport)
    }
}
