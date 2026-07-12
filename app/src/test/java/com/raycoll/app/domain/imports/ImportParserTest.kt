package com.raycoll.app.domain.imports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Port testów parsera importu z "RayColl - Kopie" i chudej wersji (spec = csv.ts / headerDetection.ts). */
class ImportParserTest {

    @Test
    fun `detectSeparator picks best candidate, default semicolon`() {
        assertEquals("tab", detectSeparator("front\tback\nhello\tczesc"))
        assertEquals("comma", detectSeparator("﻿front,back\r\nhello,czesc"))
        assertEquals("semicolon", detectSeparator("a;b;c\nd,e,f\ng;h;i"))
        assertEquals("semicolon", detectSeparator("hello\nworld"))
        assertEquals("semicolon", detectSeparator("a;b;c\n1;2;3\nx;y;z"))
        assertEquals("tab", detectSeparator("a\tb\tc\n1\t2\t3"))
        assertEquals("semicolon", detectSeparator("\"a;b\"\n1;2\n3;4"))
    }

    @Test
    fun `parseCsv handles quotes, multiline cells, multichar separators and padding`() {
        assertEquals(
            listOf(listOf("front", "back"), listOf("hello", "czesc")),
            parseCsv("front,back\nhello,czesc", "comma", 2)
        )
        // BOM + CRLF + podwojony cudzysłów w środku
        assertEquals(
            listOf(listOf("front", "back"), listOf("he said \"hi\"", "czesc")),
            parseCsv("﻿front;back\r\n\"he said \"\"hi\"\"\";czesc", "semicolon", 2)
        )
        // newline w komórce w cudzysłowie
        assertEquals(
            listOf(listOf("line1\nline2", "translation"), listOf("next", "row")),
            parseCsv("\"line1\nline2\";translation\nnext;row", "semicolon", 2)
        )
        // separator w cudzysłowie
        assertEquals(
            listOf(listOf("has;semicolon", "also;here"), listOf("foo", "bar")),
            parseCsv("\"has;semicolon\";\"also;here\"\nfoo;bar", "semicolon", 2)
        )
        // nadmiar kolumn zostaje
        assertEquals(listOf(listOf("a", "b", "c", "d")), parseCsv("a;b;c;d", "semicolon", 2))
        // padding do pageCount
        assertEquals(listOf(listOf("a", "b", "", "")), parseCsv("a;b", "semicolon", 4))
        // wiersze o różnej liczbie kolumn
        assertEquals(listOf(listOf("a", "b"), listOf("c", "d", "e")), parseCsv("a;b\nc;d;e", "semicolon", 2))
        // separator wieloznakowy
        assertEquals(
            listOf(listOf("front", "back"), listOf("hello", "czesc")),
            parseCsv("front::back\nhello::czesc", "::", 2)
        )
        assertEquals(listOf(listOf("a:b", "c:d")), parseCsv("a:b::c:d", "::", 2))
    }

    @Test
    fun `HeaderDetector detectPageCount uses max columns of data rows, min 2`() {
        assertEquals(2, HeaderDetector.detectPageCount("front;back\nexample;translation", ";"))
        assertEquals(5, HeaderDetector.detectPageCount("h1;h2\n1;2;3;4;5", ";", skipFirstRow = true))
        assertEquals(5, HeaderDetector.detectPageCount("1;2;3;4;5", ";"))
        assertEquals(10, HeaderDetector.detectPageCount("h1;h2\n1;2;3;4;5;6;7;8;9;0", ";", skipFirstRow = true))
        assertEquals(21, HeaderDetector.detectPageCount("1;2;3;4;5;6;7;8;9;0;11;12;13;14;15;16;17;18;19;20;21", ";"))
        assertEquals(3, HeaderDetector.detectPageCount("a;\"line\nbreak\";c\nd;e;f", ";"))
    }

    @Test
    fun `HeaderDetector detectFirstRowHeader scores keyword rows`() {
        assertTrue(HeaderDetector.detectFirstRowHeader("Phrase;Translation;Example\nhello;czesc;example", "semicolon", 3).isLikelyHeader)
        assertFalse(HeaderDetector.detectFirstRowHeader("hello;czesc\nbye;pa", "semicolon", 2).isLikelyHeader)
        assertTrue(HeaderDetector.detectFirstRowHeader("word;translation\nhello;cześć", "semicolon").isLikelyHeader)
    }

    @Test
    fun `normalizeImportCell trims`() {
        assertEquals("hello", normalizeImportCell(" hello "))
        assertEquals("", normalizeImportCell(""))
        assertEquals("a  b", normalizeImportCell("  a  b  "))
        assertEquals("no_space", normalizeImportCell("no_space"))
    }

    @Test
    fun `LanguageDetector detects language from header tokens`() {
        assertEquals("en-US", LanguageDetector.detectLangFromHeader("translation"))
        assertEquals("pl-PL", LanguageDetector.detectLangFromHeader("słowo"))
        assertEquals("en-US", LanguageDetector.detectLangFromHeader("word"))
        assertEquals("en-US", LanguageDetector.detectLangFromHeader("phrase"))
        assertEquals("es-ES", LanguageDetector.detectLangFromHeader("palabra"))
        assertEquals("de-DE", LanguageDetector.detectLangFromHeader("ubersetzung"))
        assertEquals("it-IT", LanguageDetector.detectLangFromHeader("it"))
        assertEquals("en-US", LanguageDetector.detectLangFromHeader("cokolwiek innego"))
    }

    @Test
    fun `validateImportPayload rejects bad payloads and accepts valid`() {
        fun payload(
            name: String = "Test",
            languages: List<String> = listOf("en-US", "pl-PL"),
            pageNames: List<String> = listOf("A", "B"),
            cards: List<List<String>> = listOf(listOf("a", "b")),
        ) = ImportPayload(deckName = name, pageNames = pageNames, pageLanguages = languages, cards = cards)

        // poprawny — nie rzuca
        validateImportPayload(payload())

        // pusty język (nie pasuje do LANG_CODE_REGEX)
        assertThrows(ImportValidationException::class.java) {
            validateImportPayload(payload(languages = listOf("en-US", "")))
        }
        // liczba języków != liczba stron
        assertThrows(ImportValidationException::class.java) {
            validateImportPayload(payload(languages = listOf("en-US"), pageNames = listOf("A", "B", "C"), cards = listOf(listOf("a", "b", "c"))))
        }
        // pusta nazwa talii
        assertThrows(ImportValidationException::class.java) {
            validateImportPayload(payload(name = "   "))
        }
        // niepoprawny tag języka
        assertThrows(ImportValidationException::class.java) {
            validateImportPayload(payload(languages = listOf("not a lang", "pl-PL")))
        }
        // karta z mniejszą liczbą pól niż stron
        assertThrows(ImportValidationException::class.java) {
            validateImportPayload(payload(cards = listOf(listOf("hello"))))
        }
        // wiersz całkiem pusty
        assertThrows(ImportValidationException::class.java) {
            validateImportPayload(payload(cards = listOf(listOf("", " "))))
        }
        // brak kart
        assertThrows(ImportValidationException::class.java) {
            validateImportPayload(payload(cards = emptyList()))
        }
        // za mało stron (< 2)
        assertThrows(ImportValidationException::class.java) {
            validateImportPayload(payload(languages = listOf("en-US"), pageNames = listOf("Only"), cards = listOf(listOf("one"))))
        }
        // za dużo stron (> 20)
        val manyNames = (1..21).map { "Page $it" }
        val manyLangs = (1..21).map { "en-US" }
        val manyCardPages = (1..21).map { "data$it" }
        assertThrows(ImportValidationException::class.java) {
            validateImportPayload(payload(languages = manyLangs, pageNames = manyNames, cards = listOf(manyCardPages)))
        }
    }
}
