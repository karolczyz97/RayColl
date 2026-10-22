package com.raycoll.app.domain.imports

import com.raycoll.app.domain.model.PageLimits

/** Tekst i ustawienia, z którymi ma zostać przetworzony (null = wykryj automatycznie). */
internal data class ParseInput(val text: String, val separator: CsvSeparator?, val hasHeader: Boolean?)

/** Wynik przetworzenia tekstu — bez ręcznych nazw i języków stron. */
internal data class ParsedSource(
    val text: String,
    val separator: CsvSeparator,
    val hasHeader: Boolean,
    /** Komórki nagłówka; null, gdy pierwszy wiersz nie jest nagłówkiem. */
    val header: List<String>?,
    val pageCount: Int,
    val rows: List<List<String>>
)

/** Separator, nagłówek, liczba stron i wiersze dla tekstu — czysta funkcja, poza wątkiem UI. */
internal fun parseSource(input: ParseInput): ParsedSource {
    val text = input.text
    if (text.isBlank()) {
        return ParsedSource(
            text = text,
            separator = input.separator ?: CsvSeparator.DEFAULT,
            hasHeader = input.hasHeader ?: false,
            header = null,
            pageCount = PageLimits.MIN_PAGE_COUNT,
            rows = emptyList()
        )
    }
    val separator = input.separator ?: detectSeparator(text)
    val allRows = parseRows(text, separator)
    val hasHeader = input.hasHeader ?: HeaderDetector.looksLikeHeaderRow(allRows)
    val pageCount = HeaderDetector.detectPageCount(allRows, skipFirstRow = hasHeader)
    return ParsedSource(
        text = text,
        separator = separator,
        hasHeader = hasHeader,
        header = allRows.firstOrNull()?.takeIf { hasHeader },
        pageCount = pageCount,
        rows = (if (hasHeader) allRows.drop(1) else allRows).let { rows ->
            if (pageCount > PageLimits.MAX_STORED_PAGE_COUNT) {
                rows
            } else {
                rows.map { row -> List(pageCount) { row.getOrElse(it) { "" } } }
            }
        }
    )
}
