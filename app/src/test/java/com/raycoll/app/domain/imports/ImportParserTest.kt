package com.raycoll.app.domain.imports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportParserTest {

    @Test
    fun `spreadsheet tabs win over commas inside both sentence columns`() {
        val text = List(5) { "Wenn ich Zeit habe, gehe ich\tIf I have time, I go" }.joinToString("\n")
        assertEquals(CsvSeparator.TAB, detectSeparator(text))
        assertEquals(2, parseRows(text, detectSeparator(text)).first().size)
        assertEquals(CsvSeparator.COMMA, detectSeparator("word; hint,translation,example\none; two,three,four"))
    }

    @Test
    fun `only unnamed trailing empty columns are discarded`() {
        fun rows(text: String) = parseRows(text, CsvSeparator.SEMICOLON)
        assertEquals(2, HeaderDetector.detectPageCount(rows("a;b;\nc;d;")))
        assertEquals(3, HeaderDetector.detectPageCount(rows("a;;c\nd;;e")))
        assertEquals(3, HeaderDetector.detectPageCount(rows("front;back;notes\na;b;\nc;d;"), true))
        assertEquals(2, HeaderDetector.detectPageCount(rows("front;back;\na;b;\nc;d;"), true))
    }

    @Test
    fun `quotes within unquoted cells do not absorb following rows`() {
        assertEquals(
            listOf(listOf("12\" pizza", "b"), listOf("c", "d")),
            parseRows("12\" pizza;b\nc;d", CsvSeparator.SEMICOLON)
        )
        assertEquals(listOf(listOf("helloworld", "b")), parseRows("\"hello\"world;b", CsvSeparator.SEMICOLON))
        assertEquals(listOf(listOf("hello;world", "b")), parseRows("  \"hello;world\";b", CsvSeparator.SEMICOLON))
    }

    @Test
    fun `detectSeparator picks the most consistent candidate and defaults to semicolon`() {
        assertEquals(CsvSeparator.TAB, detectSeparator("front\tback\nhello\tczesc"))
        assertEquals(CsvSeparator.COMMA, detectSeparator("\uFEFFfront,back\r\nhello,czesc"))
        assertEquals(CsvSeparator.SEMICOLON, detectSeparator("a;b;c\nd,e,f\ng;h;i"))
        assertEquals(CsvSeparator.SEMICOLON, detectSeparator("hello\nworld"))
        assertEquals(CsvSeparator.SEMICOLON, detectSeparator("\"a;b\"\n1;2\n3;4"))
        assertEquals(CsvSeparator.PIPE, detectSeparator("front|back\nhello|czesc"))
    }

    @Test
    fun `parseRows handles BOM CRLF quotes and multiline cells`() {
        assertEquals(
            listOf(listOf("front", "back"), listOf("hello", "czesc")),
            parseRows("front,back\nhello,czesc", CsvSeparator.COMMA)
        )
        assertEquals(
            listOf(listOf("front", "back"), listOf("he said \"hi\"", "czesc")),
            parseRows("\uFEFFfront;back\r\n\"he said \"\"hi\"\"\";czesc", CsvSeparator.SEMICOLON)
        )
        assertEquals(
            listOf(listOf("line1\nline2", "translation"), listOf("next", "row")),
            parseRows("\"line1\nline2\";translation\nnext;row", CsvSeparator.SEMICOLON)
        )
        assertEquals(
            listOf(listOf("has;semicolon", "also;here"), listOf("foo", "bar")),
            parseRows("\"has;semicolon\";\"also;here\"\nfoo;bar", CsvSeparator.SEMICOLON)
        )
    }

    @Test
    fun `parseRows retains extra columns and omits blank rows`() {
        assertEquals(listOf(listOf("a", "b", "c", "d")), parseRows("a;b;c;d", CsvSeparator.SEMICOLON))
        assertEquals(
            listOf(listOf("a", "b"), listOf("c", "d", "e")),
            parseRows("a;b\n ; \nc;d;e", CsvSeparator.SEMICOLON)
        )
        assertEquals(listOf(listOf("a", "b")), parseRows("a|b", CsvSeparator.PIPE))
    }

    @Test
    fun `HeaderDetector uses the widest data row with minimum two pages`() {
        fun rows(text: String) = parseRows(text, CsvSeparator.SEMICOLON)
        assertEquals(2, HeaderDetector.detectPageCount(rows("front;back\nexample;translation")))
        assertEquals(5, HeaderDetector.detectPageCount(rows("h1;h2\n1;2;3;4;5"), skipFirstRow = true))
        assertEquals(5, HeaderDetector.detectPageCount(rows("1;2;3;4;5")))
        assertEquals(21, HeaderDetector.detectPageCount(rows((1..21).joinToString(";"))))
        assertEquals(3, HeaderDetector.detectPageCount(rows("a;\"line\nbreak\";c\nd;e;f")))
    }

    @Test
    fun `HeaderDetector recognizes labels rather than ordinary values`() {
        fun rows(text: String) = parseRows(text, CsvSeparator.SEMICOLON)
        assertTrue(
            HeaderDetector.looksLikeHeaderRow(rows("Phrase;Translation;Example\nhello;czesc;example"))
        )
        assertFalse(HeaderDetector.looksLikeHeaderRow(rows("hello;czesc\nbye;pa")))
        assertTrue(HeaderDetector.looksLikeHeaderRow(rows("word;translation\nhello;cześć")))
    }

    @Test
    fun `parser trims cells without collapsing internal spaces`() {
        assertEquals(
            listOf(listOf("hello", "a  b", "no_space", "")),
            parseRows(" hello ;  a  b  ;no_space;", CsvSeparator.SEMICOLON)
        )
    }

    @Test
    fun `encoded edited cells round trip through every supported separator`() {
        val cells = listOf("semi;tab\tcomma,pipe|", "he said \"hi\"", "line1\nline2", "")
        CsvSeparator.entries.forEach { separator ->
            val text = cells.joinToString(separator.char.toString()) { encodeCell(it, separator) }
            assertEquals(listOf(cells), parseRows(text, separator))
        }
    }

    @Test
    fun `LanguageDetector recognizes translated labels and language codes`() {
        mapOf(
            "translation" to "en-US",
            "Polish translation" to "pl-PL",
            "German" to "de-DE",
            "German fraza" to "de-DE",
            "Spanish example" to "es-ES",
            "Italian word" to "it-IT",
            "słowo" to "pl-PL",
            "word" to "en-US",
            "phrase" to "en-US",
            "palabra" to "es-ES",
            "ubersetzung" to "de-DE",
            "it" to "it-IT",
            "cokolwiek innego" to "en-US"
        ).forEach { (header, language) ->
            assertEquals(language, LanguageDetector.detectLanguage(header))
        }
    }

    @Test
    fun `validation returns typed problems and accepts normalized partial rows`() {
        fun payload(
            name: String = "Test",
            languages: List<String> = listOf("en-US", "pl-PL"),
            names: List<String> = listOf("A", "B"),
            cards: List<List<String>> = listOf(listOf("a", "b"))
        ) = ImportPayload(name, names, languages, cards)
        assertNull(validateImportPayload(payload()))
        assertEquals(ImportProblem.InvalidLanguage(""), validateImportPayload(payload(languages = listOf("en-US", ""))))
        assertEquals(ImportProblem.InvalidPageCount, validateImportPayload(payload(languages = listOf("en-US"))))
        assertEquals(ImportProblem.DeckNameRequired, validateImportPayload(payload(name = "   ")))
        assertEquals(
            ImportProblem.InvalidLanguage("not a lang"),
            validateImportPayload(payload(languages = listOf("not a lang", "pl-PL")))
        )
        assertNull(validateImportPayload(payload(cards = listOf(listOf("hello", "")))))
        assertEquals(ImportProblem.EmptyRow(1), validateImportPayload(payload(cards = listOf(listOf("", " ")))))
        assertEquals(ImportProblem.NoCards, validateImportPayload(payload(cards = emptyList())))
        assertEquals(
            ImportProblem.InvalidPageCount,
            validateImportPayload(payload(languages = listOf("en-US"), names = listOf("Only")))
        )
        assertEquals(
            ImportProblem.InvalidPageCount,
            validateImportPayload(
                payload(
                    languages = List(21) {
                        "en-US"
                    },
                    names = List(21) { "Page $it" },
                    cards = listOf(List(21) { "data$it" })
                )
            )
        )
    }

    @Test
    fun `HeaderDetector recognizes Polish header with the dotless l`() {
        fun rows(text: String) = parseRows(text, CsvSeparator.SEMICOLON)
        assertTrue(HeaderDetector.looksLikeHeaderRow(rows("Słowo;Tłumaczenie\nkot;cat")))
        assertEquals("slowo", HeaderDetector.normalizeHeaderToken("Słowo"))
    }

    @Test
    fun `encodeCell quotes carriage returns so a cell never splits rows`() {
        val encoded = encodeCell("a\rb", CsvSeparator.SEMICOLON)
        assertEquals("\"a\rb\"", encoded)
        assertEquals(1, parseRows(encoded + ";x", CsvSeparator.SEMICOLON).size)
    }

    @Test
    fun `parseRows stops after maxRows non-blank rows`() {
        val text = "a;b\n\nc;d\ne;f\ng;h"
        assertEquals(listOf(listOf("a", "b"), listOf("c", "d")), parseRows(text, CsvSeparator.SEMICOLON, 2))
        assertEquals(4, parseRows(text, CsvSeparator.SEMICOLON).size)
    }

    @Test
    fun `detectSeparator on a long text matches the result for its first rows`() {
        val long = (1..5000).joinToString("\n") { "front $it|back $it" }
        assertEquals(CsvSeparator.PIPE, detectSeparator(long))
        assertEquals(detectSeparator(long.lineSequence().take(5).joinToString("\n")), detectSeparator(long))
    }
}
