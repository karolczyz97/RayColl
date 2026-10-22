package com.raycoll.app.ui.imports

import com.raycoll.app.domain.imports.encodeCell

/** Tekst źródłowy odbudowany z wierszy podglądu (nagłówek — oryginalne komórki nagłówka). */
internal fun buildSource(state: ImportUiState, header: List<String>?): String {
    val lines = buildList {
        if (state.hasHeader && header != null) add(List(state.pageCount) { header.getOrElse(it) { "" } })
        addAll(state.rows)
    }
    return lines.joinToString("\n") { row ->
        row.joinToString(state.separator.char.toString()) { encodeCell(it, state.separator) }
    }
}
