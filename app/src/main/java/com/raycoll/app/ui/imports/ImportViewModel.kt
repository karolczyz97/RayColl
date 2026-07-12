package com.raycoll.app.ui.imports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raycoll.app.core.Ids
import com.raycoll.app.data.repository.DeckRepository
import com.raycoll.app.domain.imports.HeaderDetector
import com.raycoll.app.domain.imports.ImportPayload
import com.raycoll.app.domain.imports.ImportValidationException
import com.raycoll.app.domain.imports.LanguageDetector
import com.raycoll.app.domain.imports.detectSeparator
import com.raycoll.app.domain.imports.normalizeCsvText
import com.raycoll.app.domain.imports.parseFullText
import com.raycoll.app.domain.imports.resolveSep
import com.raycoll.app.domain.imports.validateImportPayload
import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.model.createNewSrsState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ImportUiState(
    val sourceText: String = "",
    val deckName: String = "",
    val separatorKey: String = "semicolon",
    val hasHeader: Boolean = false,
    val pageCount: Int = 2,
    val pageNames: List<String> = listOf("Page 1", "Page 2"),
    val pageLanguages: List<String> = listOf("en-US", "en-US"),
    val dataRows: List<List<String>> = emptyList(),
    val errorMsg: String? = null,
) {
    val canImport: Boolean
        get() = deckName.isNotBlank() && dataRows.any { row -> row.any { it.isNotBlank() } }
}

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val deckRepository: DeckRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    private var separatorManual = false
    private var headerManual = false

    fun setDeckName(name: String) {
        _uiState.value = _uiState.value.copy(deckName = name)
    }

    /** Nowy tekst źródłowy (wklejenie / plik / edycja): auto-detekcje o ile nie nadpisane ręcznie. */
    fun setSourceText(text: String) {
        val sepKey = if (separatorManual) _uiState.value.separatorKey else detectSeparator(text)
        recompute(text, sepKey, hasHeaderOverride = if (headerManual) _uiState.value.hasHeader else null)
    }

    fun setSeparator(sepKey: String) {
        separatorManual = true
        recompute(_uiState.value.sourceText, sepKey, hasHeaderOverride = if (headerManual) _uiState.value.hasHeader else null)
    }

    fun setHasHeader(value: Boolean) {
        headerManual = true
        recompute(_uiState.value.sourceText, _uiState.value.separatorKey, hasHeaderOverride = value)
    }

    fun setPageName(index: Int, name: String) {
        val names = _uiState.value.pageNames.toMutableList()
        if (index in names.indices) names[index] = name
        _uiState.value = _uiState.value.copy(pageNames = names)
    }

    fun setPageLanguage(index: Int, lang: String) {
        val langs = _uiState.value.pageLanguages.toMutableList()
        if (index in langs.indices) langs[index] = lang
        _uiState.value = _uiState.value.copy(pageLanguages = langs)
    }

    /** Edycja komórki w podglądzie — aktualizuje też tekst źródłowy (jedno źródło prawdy). */
    fun updateCell(rowIndex: Int, colIndex: Int, value: String) {
        val s = _uiState.value
        val rows = s.dataRows.map { it.toMutableList() }.toMutableList()
        val row = rows.getOrNull(rowIndex) ?: return
        while (row.size <= colIndex) row.add("")
        row[colIndex] = value
        applyRows(rows)
    }

    /** Usunięcie wiersza w podglądzie — aktualizuje też tekst źródłowy. */
    fun deleteRow(rowIndex: Int) {
        val s = _uiState.value
        val rows = s.dataRows.toMutableList()
        if (rowIndex in rows.indices) rows.removeAt(rowIndex)
        applyRows(rows.map { it.toMutableList() }.toMutableList())
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMsg = null)
    }

    fun runImport(onSuccess: () -> Unit) {
        val s = _uiState.value
        viewModelScope.launch {
            try {
                val payload = ImportPayload(
                    deckName = s.deckName.trim(),
                    pageNames = s.pageNames.take(s.pageCount),
                    pageLanguages = s.pageLanguages.take(s.pageCount),
                    cards = s.dataRows.map { row ->
                        val padded = row.toMutableList()
                        while (padded.size < s.pageCount) padded.add("")
                        padded.take(s.pageCount)
                    }.filter { row -> row.any { it.isNotBlank() } },
                )
                validateImportPayload(payload)
                val cards = payload.cards.map { pages ->
                    Flashcard(id = Ids.newId(), pages = pages, srsState = createNewSrsState(), contentUpdatedAt = 0, srsUpdatedAt = 0)
                }
                deckRepository.addDeckWithCards(payload.deckName, payload.pageLanguages, payload.pageNames, cards)
                onSuccess()
            } catch (e: ImportValidationException) {
                _uiState.value = _uiState.value.copy(errorMsg = e.message)
            }
        }
    }

    private fun recompute(text: String, sepKey: String, hasHeaderOverride: Boolean?) {
        val s = _uiState.value
        if (text.isBlank()) {
            _uiState.value = s.copy(sourceText = text, separatorKey = sepKey, dataRows = emptyList())
            return
        }
        val rows = parseFullText(normalizeCsvText(text), resolveSep(sepKey))
        val hasHeader = hasHeaderOverride
            ?: HeaderDetector.detectFirstRowHeader(text, sepKey).isLikelyHeader
        val pageCount = HeaderDetector.detectPageCount(text, sepKey, skipFirstRow = hasHeader)
            .coerceAtMost(com.raycoll.app.domain.model.PageLimits.MAX_STORED_PAGE_COUNT)
        val headerRow = if (hasHeader) rows.firstOrNull() else null
        val pageNames = (0 until pageCount).map { i ->
            headerRow?.getOrNull(i)?.takeIf { it.isNotBlank() } ?: defaultPageName(s, i)
        }
        val pageLanguages = (0 until pageCount).map { i ->
            headerRow?.getOrNull(i)?.let { LanguageDetector.detectLanguage(it) } ?: s.pageLanguages.getOrElse(i) { "en-US" }
        }
        val dataRows = (if (hasHeader) rows.drop(1) else rows).map { row ->
            val padded = row.toMutableList()
            while (padded.size < pageCount) padded.add("")
            padded.take(pageCount)
        }
        _uiState.value = s.copy(
            sourceText = text,
            separatorKey = sepKey,
            hasHeader = hasHeader,
            pageCount = pageCount,
            pageNames = pageNames,
            pageLanguages = pageLanguages,
            dataRows = dataRows,
        )
    }

    private fun defaultPageName(s: ImportUiState, i: Int): String =
        s.pageNames.getOrNull(i)?.takeIf { !it.startsWith("Page ") } ?: "Page ${i + 1}"

    /** Odbudowa tekstu źródłowego z podglądu (nagłówek zachowany, cudzysłowy escapowane). */
    private fun applyRows(rows: List<List<String>>) {
        val s = _uiState.value
        val sep = resolveSep(s.separatorKey)
        val allRows = buildList {
            if (s.hasHeader) add(s.pageNames.take(s.pageCount))
            addAll(rows)
        }
        val text = allRows.joinToString("\n") { row ->
            row.joinToString(sep) { cell -> encodeCell(cell, sep) }
        }
        _uiState.value = s.copy(sourceText = text, dataRows = rows)
    }

    private fun encodeCell(cell: String, sep: String): String =
        if (cell.contains(sep) || cell.contains('\n') || cell.contains('"')) {
            "\"" + cell.replace("\"", "\"\"") + "\""
        } else cell
}
