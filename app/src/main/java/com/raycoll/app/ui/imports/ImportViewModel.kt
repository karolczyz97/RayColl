package com.raycoll.app.ui.imports

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raycoll.app.data.files.DocumentFiles
import com.raycoll.app.data.repository.DeckRepository
import com.raycoll.app.di.DefaultDispatcher
import com.raycoll.app.domain.imports.CsvSeparator
import com.raycoll.app.domain.imports.HeaderDetector
import com.raycoll.app.domain.imports.ImportPayload
import com.raycoll.app.domain.imports.ImportProblem
import com.raycoll.app.domain.imports.LanguageDetector
import com.raycoll.app.domain.imports.detectSeparator
import com.raycoll.app.domain.imports.encodeCell
import com.raycoll.app.domain.imports.parseRows
import com.raycoll.app.domain.imports.validateImportPayload
import com.raycoll.app.domain.model.DEFAULT_PAGE_LANGUAGE
import com.raycoll.app.domain.model.PageLimits
import com.raycoll.app.ui.components.UiEvents
import com.raycoll.app.ui.components.launchSaving
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Ile czekać po ostatnim wpisanym znaku, zanim tekst zostanie przetworzony. */
private const val PARSE_DEBOUNCE_MS = 150L

/** Ile czekać po edycji komórki podglądu, zanim tekst źródłowy zostanie odbudowany. */
private const val REBUILD_DEBOUNCE_MS = 300L

data class ImportUiState(
    val sourceText: String = "",
    val deckName: String = "",
    val separator: CsvSeparator = CsvSeparator.DEFAULT,
    val hasHeader: Boolean = false,
    /** Nazwy stron z nagłówka albo wpisane; puste = domyślna "Strona N". */
    val pageNames: List<String> = List(PageLimits.MIN_PAGE_COUNT) { "" },
    val pageLanguages: List<String> = List(PageLimits.MIN_PAGE_COUNT) { DEFAULT_PAGE_LANGUAGE },
    val rows: List<List<String>> = emptyList(),
    val importing: Boolean = false
) {
    val pageCount: Int get() = pageNames.size

    val canImport: Boolean get() = !importing && deckName.isNotBlank() &&
        rows.any { row -> row.any { it.isNotBlank() } }
}

sealed interface ImportEvent {
    data object Imported : ImportEvent

    data class Invalid(val problem: ImportProblem) : ImportEvent

    data object FileUnreadable : ImportEvent

    data object SaveFailed : ImportEvent

    /** Kolumny się zmieniły: ręczne nazwy/języki niepasujących kolumn wróciły do wykrytych. */
    data object ColumnSettingsReset : ImportEvent
}

/** Tekst i ustawienia, z którymi ma zostać przetworzony (null = wykryj automatycznie). */
private data class ParseInput(val text: String, val separator: CsvSeparator?, val hasHeader: Boolean?)

/** Wynik przetworzenia tekstu — bez ręcznych nazw i języków stron. */
private data class ParsedSource(
    val text: String,
    val separator: CsvSeparator,
    val hasHeader: Boolean,
    /** Komórki nagłówka; null, gdy pierwszy wiersz nie jest nagłówkiem. */
    val header: List<String>?,
    val pageCount: Int,
    val rows: List<List<String>>
)

/**
 * Ręczna nazwa albo język kolumny. [headerCell] — nagłówek kolumny w chwili zmiany: gdy
 * ta kolumna ma potem inny nagłówek, kolumny zostały przestawione i ustawienie nie
 * przechodzi na cudzą kolumnę.
 */
private data class ColumnOverride(val value: String, val headerCell: String?)

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val deckRepository: DeckRepository,
    private val documentFiles: DocumentFiles,
    @DefaultDispatcher private val parseDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    private val eventSink = UiEvents<ImportEvent>()
    val events: Flow<ImportEvent> = eventSink.flow

    private var parseJob: Job? = null
    private var rebuildJob: Job? = null

    // ręczny wybór użytkownika wygrywa z automatycznym wykrywaniem
    private var separatorChosen = false
    private var headerChosen = false
    private val nameOverrides = mutableMapOf<Int, ColumnOverride>()
    private val languageOverrides = mutableMapOf<Int, ColumnOverride>()

    /** Nagłówek z ostatniego przetworzenia (wraca do tekstu przy jego odbudowie). */
    private var header: List<String>? = null

    /**
     * Komórki podglądu zmienione, a tekst źródłowy jeszcze nieodbudowany: źródłem prawdy
     * są wtedy wiersze, a spóźnione przetworzenie starego tekstu jest odrzucane.
     */
    private var rowsEdited = false

    fun setDeckName(name: String) = _uiState.update { it.copy(deckName = name) }

    /** Pisanie w polu: tekst od razu (kursor nie skacze), przetwarzanie chwilę później. */
    fun onSourceTextChanged(text: String) {
        discardRowEdits()
        _uiState.update { it.copy(sourceText = text) }
        reparse(debounce = true)
    }

    fun loadFile(uri: Uri) {
        viewModelScope.launch {
            val text = documentFiles.readText(uri)
            if (text == null) {
                eventSink.send(ImportEvent.FileUnreadable)
            } else {
                discardRowEdits()
                _uiState.update { it.copy(sourceText = text) }
                reparse(debounce = false)
            }
        }
    }

    fun setSeparator(separator: CsvSeparator) {
        separatorChosen = true
        flushRowEdits()
        _uiState.update { it.copy(separator = separator) }
        reparse(debounce = false)
    }

    fun setHasHeader(value: Boolean) {
        headerChosen = true
        flushRowEdits()
        _uiState.update { it.copy(hasHeader = value) }
        reparse(debounce = false)
    }

    fun setPageName(index: Int, name: String) {
        if (index !in 0 until _uiState.value.pageCount) return
        nameOverrides[index] = ColumnOverride(name, headerCell(index))
        _uiState.update { state ->
            state.copy(pageNames = state.pageNames.mapIndexed { i, old -> if (i == index) name else old })
        }
    }

    fun setPageLanguage(index: Int, language: String) {
        if (index !in 0 until _uiState.value.pageCount) return
        languageOverrides[index] = ColumnOverride(language, headerCell(index))
        _uiState.update { state ->
            state.copy(pageLanguages = state.pageLanguages.mapIndexed { i, old -> if (i == index) language else old })
        }
    }

    /** Edycja komórki w podglądzie: wiersze od razu, tekst źródłowy po chwili i poza wątkiem UI. */
    fun updateCell(rowIndex: Int, columnIndex: Int, value: String) = editRows { rows ->
        val row = rows.getOrNull(rowIndex) ?: return@editRows
        if (columnIndex !in row.indices) return@editRows
        rows[rowIndex] = row.mapIndexed { i, old -> if (i == columnIndex) value else old }
    }

    fun deleteRow(rowIndex: Int) = editRows { rows -> if (rowIndex in rows.indices) rows.removeAt(rowIndex) }

    /**
     * Import tego, co widać na ekranie: najnowszy tekst (także wpisany przed końcem opóźnienia)
     * jest przetwarzany poza wątkiem UI z ręcznymi ustawieniami kolumn, a edycje komórek
     * podglądu są brane wprost z wierszy. Kolejne kliknięcie w trakcie importu nic nie robi.
     * [defaultPageNames] — nazwy (w języku aplikacji) dla stron, których użytkownik nie nazwał.
     */
    fun runImport(defaultPageNames: List<String>) {
        if (_uiState.value.importing) return
        parseJob?.cancel()
        _uiState.update { it.copy(importing = true) }
        viewModelScope.launchSaving(onFailure = { eventSink.send(ImportEvent.SaveFailed) }) {
            try {
                if (!rowsEdited) {
                    val input = parseInput()
                    applyParsed(withContext(parseDispatcher) { parseSource(input) })
                }
                val state = _uiState.value
                val payload = ImportPayload(
                    deckName = state.deckName.trim(),
                    pageNames = state.pageNames.mapIndexed { i, name ->
                        name.ifBlank { defaultPageNames.getOrElse(i) { "" } }
                    },
                    pageLanguages = state.pageLanguages,
                    cards = state.rows.filter { row -> row.any { it.isNotBlank() } }
                )
                val problem = validateImportPayload(payload)
                if (problem != null) {
                    eventSink.send(ImportEvent.Invalid(problem))
                    return@launchSaving
                }
                deckRepository.addDeckWithCards(
                    payload.deckName,
                    payload.pageLanguages,
                    payload.pageNames,
                    payload.cards
                )
                eventSink.send(ImportEvent.Imported)
            } finally {
                _uiState.update { it.copy(importing = false) }
            }
        }
    }

    private fun reparse(debounce: Boolean) {
        parseJob?.cancel()
        parseJob = viewModelScope.launch {
            if (debounce) delay(PARSE_DEBOUNCE_MS)
            val input = parseInput()
            val parsed = withContext(parseDispatcher) { parseSource(input) }
            // wynik dla nieaktualnego tekstu (użytkownik pisze dalej) albo sprzed edycji komórek jest odrzucany
            if (!rowsEdited && _uiState.value.sourceText == parsed.text) applyParsed(parsed)
        }
    }

    private fun parseInput(): ParseInput {
        val state = _uiState.value
        return ParseInput(
            text = state.sourceText,
            separator = state.separator.takeIf { separatorChosen },
            hasHeader = state.hasHeader.takeIf { headerChosen }
        )
    }

    /** Wykryte wartości + ręczne ustawienia tych kolumn, które nadal do nich pasują. */
    private fun applyParsed(parsed: ParsedSource) {
        if (parsed.text.isBlank()) {
            // pusty tekst: nic do pokazania, ustawienia stron zostają do kolejnego wklejenia
            _uiState.update { it.copy(sourceText = parsed.text, rows = emptyList()) }
            return
        }
        header = parsed.header
        val droppedNames = dropStaleOverrides(nameOverrides, parsed)
        val droppedLanguages = dropStaleOverrides(languageOverrides, parsed)
        _uiState.update { state ->
            state.copy(
                sourceText = parsed.text,
                separator = parsed.separator,
                hasHeader = parsed.hasHeader,
                pageNames = List(parsed.pageCount) { i ->
                    nameOverrides[i]?.value ?: parsed.header?.getOrNull(i)?.takeIf { it.isNotBlank() } ?: ""
                },
                pageLanguages = List(parsed.pageCount) { i ->
                    languageOverrides[i]?.value
                        ?: parsed.header?.getOrNull(i)?.let(LanguageDetector::detectLanguage)
                        ?: DEFAULT_PAGE_LANGUAGE
                },
                rows = parsed.rows
            )
        }
        if (droppedNames || droppedLanguages) eventSink.send(ImportEvent.ColumnSettingsReset)
    }

    /**
     * Usuwa ustawienia kolumn, których już nie ma, i tych, których nagłówek się zmienił
     * (kolumny przestawione). Bez nagłówka kolumny da się porównać tylko po pozycji.
     */
    private fun dropStaleOverrides(overrides: MutableMap<Int, ColumnOverride>, parsed: ParsedSource): Boolean {
        val stale = overrides.filter { (index, override) ->
            val movedColumn = override.headerCell != null && parsed.header != null &&
                parsed.header.getOrNull(index) != override.headerCell
            index >= parsed.pageCount || movedColumn
        }.keys
        overrides -= stale
        return stale.isNotEmpty()
    }

    private fun headerCell(index: Int): String? = header?.takeIf { _uiState.value.hasHeader }?.getOrNull(index)

    private fun editRows(change: (MutableList<List<String>>) -> Unit) {
        parseJob?.cancel()
        rowsEdited = true
        _uiState.update { it.copy(rows = it.rows.toMutableList().apply(change)) }
        rebuildJob?.cancel()
        rebuildJob = viewModelScope.launch {
            delay(REBUILD_DEBOUNCE_MS)
            val state = _uiState.value
            val header = header
            val text = withContext(parseDispatcher) { buildSource(state, header) }
            // edycja w trakcie odbudowy anuluje to zadanie i planuje własne
            rowsEdited = false
            _uiState.update { it.copy(sourceText = text) }
        }
    }

    /** Przed zmianą separatora albo nagłówka tekst musi już zawierać edycje komórek. */
    private fun flushRowEdits() {
        if (!rowsEdited) return
        rebuildJob?.cancel()
        rowsEdited = false
        val text = buildSource(_uiState.value, header)
        _uiState.update { it.copy(sourceText = text) }
    }

    /** Użytkownik zmienia sam tekst źródłowy: wygrywa nowy tekst. */
    private fun discardRowEdits() {
        rebuildJob?.cancel()
        rowsEdited = false
    }
}

/** Tekst źródłowy odbudowany z wierszy podglądu (nagłówek — oryginalne komórki nagłówka). */
private fun buildSource(state: ImportUiState, header: List<String>?): String {
    val lines = buildList {
        if (state.hasHeader && header != null) add(List(state.pageCount) { header.getOrElse(it) { "" } })
        addAll(state.rows)
    }
    return lines.joinToString("\n") { row ->
        row.joinToString(state.separator.char.toString()) { encodeCell(it, state.separator) }
    }
}

/** Separator, nagłówek, liczba stron i wiersze dla tekstu — czysta funkcja, poza wątkiem UI. */
private fun parseSource(input: ParseInput): ParsedSource {
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
        rows = (if (hasHeader) allRows.drop(1) else allRows).map { row -> List(pageCount) { row.getOrElse(it) { "" } } }
    )
}
