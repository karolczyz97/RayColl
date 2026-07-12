package com.raycoll.app.domain.imports

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ImportTest {
    @Test
    fun `parseCSV basic tab separated`() {
        val rows = parseCSV("a\tb\nc\td", "tab", 2)
        assertEquals(2, rows.size)
        assertEquals(listOf("a", "b"), rows[0])
        assertEquals(listOf("c", "d"), rows[1])
    }

    @Test
    fun `parseCSV semicolon with quotes`() {
        val rows = parseCSV("\"hello;world\";foo\nbar;baz", "semicolon", 2)
        assertEquals(2, rows.size)
        assertEquals("hello;world", rows[0][0])
        assertEquals("foo", rows[0][1])
    }

    @Test
    fun `serializeCSV round trip`() {
        val rows = listOf(listOf("a", "b"), listOf("c,d", "e"))
        val csv = serializeCSV(rows, "comma")
        assertEquals("\"c,d\",e", csv.lines()[1])
    }

    @Test
    fun `detectSeparator detects tab`() {
        assertEquals("tab", detectSeparator("a\tb\tc\nd\te\tf"))
    }

    @Test
    fun `detectSeparator detects semicolon`() {
        assertEquals("semicolon", detectSeparator("a;b;c\nd;e;f"))
    }

    @Test
    fun `detectFirstRowHeader detects header`() {
        val (isHeader, _) = detectFirstRowHeader("word;translation\nhello;cześć", "semicolon")
        assertTrue(isHeader)
    }

    @Test
    fun `detectFirstRowHeader returns false for data`() {
        val (isHeader, _) = detectFirstRowHeader("hello;world\nfoo;bar", "semicolon")
        assertFalse(isHeader)
    }

    @Test
    fun `detectLangFromHeader detects English`() {
        assertEquals("en-US", detectLangFromHeader("word"))
        assertEquals("en-US", detectLangFromHeader("phrase"))
    }

    @Test
    fun `detectLangFromHeader detects Polish`() {
        assertEquals("pl-PL", detectLangFromHeader("słowo"))
    }

    @Test
    fun `validateImportDeckPayload requires name`() {
        val ex = org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException::class.java) {
            validateImportDeckPayload(ImportDeckPayload("", listOf("en-US", "pl-PL"), listOf("Front", "Back"), listOf(listOf("a", "b"))))
        }
        assertTrue(ex.message!!.contains("name"))
    }

    @Test
    fun `validateImportDeckPayload requires non-empty cards`() {
        val ex = org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException::class.java) {
            validateImportDeckPayload(ImportDeckPayload("Test", listOf("en-US", "pl-PL"), listOf("Front", "Back"), emptyList()))
        }
        assertTrue(ex.message!!.contains("cards"))
    }

    @Test
    fun `validateImportDeckPayload passes valid input`() {
        val result = validateImportDeckPayload(ImportDeckPayload("Test", listOf("en-US", "pl-PL"), listOf("Front", "Back"), listOf(listOf("a", "b"))))
        assertEquals("Test", result.name)
        assertEquals(1, result.cards.size)
    }
}
