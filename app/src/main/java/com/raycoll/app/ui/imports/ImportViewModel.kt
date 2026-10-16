package com.raycoll.app.ui.imports

import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raycoll.app.data.files.DocumentFiles
import com.raycoll.app.data.repository.DeckRepository
import com.raycoll.app.di.DefaultDispatcher
import com.raycoll.app.domain.imports.CsvSeparator
import com.raycoll.app.domain.imports.ImportPayload
import com.raycoll.app.domain.imports.ImportProblem
import com.raycoll.app.domain.imports.LanguageDetector
import com.raycoll.app.domain.imports.validateImportPayload
import com.raycoll.app.domain.model.DEFAULT_PAGE_LANGUAGE
import com.raycoll.app.domain.model.PageLimits
import com.raycoll.app.ui.components.UiEvents
import com.raycoll.app.ui.components.launchSaving
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Ile czekać po ostatnim wpisanym znaku, zanim tekst zostanie przetworzony. */
private const val PARSE_DEBOUNCE_MS = 150L

/** Ile czekać po edycji komórki podglądu, zanim tekst źródłowy zostanie odbudowany. */
private const val REBUILD_DEBOUNCE_MS = 300L

/** Ile czekać po ostatniej zmianie, zanim szkic trafi do pliku. */
private const val DRAFT_SAVE_DEBOUNCE_MS = 500L

/** Plik szkicu w prywatnych plikach aplikacji (tekst CSV bywa za duży na zapisany stan ekranu). */
internal const val IMPORT_DRAFT_FILE = "import-draft.json"

/** W zapisanym stanie ekranu: szkic jest w [IMPORT_DRAFT_FILE]. */
internal const val DRAFT_SAVED_KEY = "importDraftSaved"

data class ImportUiState(
    val sourceText: String = "",
    val deckName: String = "",
    val separator: CsvSeparator = CsvSeparator.DEFAULT,
    val hasHeader: Boolean = false,
    /** Nazwy stron z nagłówka albo wpisane; puste = domyślna "Strona N". */
    val pageNames: List<String> = List(PageLimits.MIN_PAGE_COUNT) { "" },
    val pageLanguages: List<String> = List(PageLimits.MIN_PAGE_COUNT) { DEFAULT_PAGE_LANGUAGE },
    val rows: List<List<String>> = emptyList(),
    val importing: Boolean = false,
    val loadingFile: Boolean = false,
    val detectedPageCount: Int? = null
) {
    val pageCount: Int get() = detectedPageCount ?: pageNames.size
    val validPageCount: Boolean get() = pageCount in PageLimits.MIN_PAGE_COUNT..PageLimits.MAX_STORED_PAGE_COUNT
    val hasDraft: Boolean get() = sourceText.isNotBlank() || deckName.isNotBlank() || rows.isNotEmpty() ||
        pageNames.any { it.isNotBlank() } || pageLanguages.any { it != DEFAULT_PAGE_LANGUAGE } ||
        separator != CsvSeparator.DEFAULT || hasHeader
    val inputLocked: Boolean get() = importing || loadingFile

    val canImport: Boolean get() = !inputLocked && validPageCount && deckName.isNotBlank() &&
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

/**
 * Ręczna nazwa albo język kolumny. [headerCell] — nagłówek kolumny w chwili zmiany: gdy
 * ta kolumna ma potem inny nagłówek, kolumny zostały przestawione i ustawienie nie
 * przechodzi na cudzą kolumnę.
 */
@Serializable
private data class ColumnOverride(val value: String, val headerCell: String?)

/** Wszystko, co wpisał i wybrał użytkownik; po śmierci procesu z tego odtwarza się ekran. */
@Serializable
private data class ImportDraft(
    val sourceText: String,
    val deckName: String,
    /** null: wykrywany automatycznie. */
    val separator: CsvSeparator?,
    /** null: wykrywany automatycznie. */
    val hasHeader: Boolean?,
    val pageNames: List<String>,
    val pageLanguages: List<String>,
    val nameOverrides: Map<Int, ColumnOverride>,
    val languageOverrides: Map<Int, ColumnOverride>
)

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val deckRepository: DeckRepository,
    private val documentFiles: DocumentFiles,
    @DefaultDispatcher private val parseDispatcher: CoroutineDispatcher,
    private val savedState: SavedStateHandle
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

    /** Zapis i usuwanie pliku szkicu po kolei — usunięcie nie wyprzedza trwającego zapisu. */
    private val draftFile = Mutex()

    /** Import zakończony albo porzucony: szkic nie jest już zapisywany. */
    private var draftClosed = false

    init {
        viewModelScope.launch {
            // szkic wraca tylko do ekranu odtwarzanego po śmierci procesu; nowy import zaczyna od zera
            if (savedState.get<Boolean>(DRAFT_SAVED_KEY) == true) restoreDraft() else deleteDraft()
            _uiState.collectLatest { state ->
                when {
                    // nieudany import wraca z „importing = false” i szkic znów się zapisuje
                    state.importing || draftClosed -> Unit

                    // od razu: wyjście bez szkicu zamyka ekran bez pytania, zanim minęłoby opóźnienie
                    !state.hasDraft -> deleteDraft()

                    else -> {
                        delay(DRAFT_SAVE_DEBOUNCE_MS)
                        saveDraft()
                    }
                }
            }
        }
    }

    fun setDeckName(name: String) {
        if (_uiState.value.inputLocked) return
        _uiState.update { it.copy(deckName = name) }
    }

    /** Pisanie w polu: tekst od razu (kursor nie skacze), przetwarzanie chwilę później. */
    fun onSourceTextChanged(text: String) {
        if (_uiState.value.inputLocked) return
        discardRowEdits()
        _uiState.update { it.copy(sourceText = text) }
        reparse(debounce = true)
    }

    fun loadFile(uri: Uri) {
        if (_uiState.value.inputLocked) return
        _uiState.update { it.copy(loadingFile = true) }
        viewModelScope.launchSaving(onFailure = { eventSink.send(ImportEvent.FileUnreadable) }) {
            try {
                val text = documentFiles.readText(uri)
                if (text == null) {
                    eventSink.send(ImportEvent.FileUnreadable)
                } else {
                    discardRowEdits()
                    _uiState.update { it.copy(sourceText = text) }
                    reparse(debounce = false)
                }
            } finally {
                _uiState.update { it.copy(loadingFile = false) }
            }
        }
    }

    fun setSeparator(separator: CsvSeparator) {
        if (_uiState.value.inputLocked) return
        separatorChosen = true
        flushRowEdits()
        _uiState.update { it.copy(separator = separator) }
        reparse(debounce = false)
    }

    fun setHasHeader(value: Boolean) {
        if (_uiState.value.inputLocked) return
        headerChosen = true
        flushRowEdits()
        _uiState.update { it.copy(hasHeader = value) }
        reparse(debounce = false)
    }

    fun setPageName(index: Int, name: String) {
        if (_uiState.value.inputLocked) return
        if (index !in 0 until _uiState.value.pageCount) return
        nameOverrides[index] = ColumnOverride(name, headerCell(index))
        _uiState.update { state ->
            state.copy(pageNames = state.pageNames.mapIndexed { i, old -> if (i == index) name else old })
        }
    }

    fun setPageLanguage(index: Int, language: String) {
        if (_uiState.value.inputLocked) return
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
     * podglądu są brane wprost z wierszy. Kolejne kliknięcie w trakcie importu nic nie robi;
     * po udanym imporcie flaga zostaje do zamknięcia ekranu, więc zdarzenie czekające
     * za komunikatem nie pozwala dodać talii drugi raz.
     * [defaultPageNames] — nazwy (w języku aplikacji) dla stron, których użytkownik nie nazwał.
     */
    fun runImport(defaultPageNames: List<String>) {
        if (_uiState.value.inputLocked) return
        parseJob?.cancel()
        _uiState.update { it.copy(importing = true) }
        viewModelScope.launchSaving(onFailure = { eventSink.send(ImportEvent.SaveFailed) }) {
            var imported = false
            try {
                if (!rowsEdited) {
                    val input = parseInput()
                    applyParsed(withContext(parseDispatcher) { parseSource(input) })
                }
                val state = _uiState.value
                if (!state.validPageCount) {
                    eventSink.send(ImportEvent.Invalid(ImportProblem.InvalidPageCount))
                    return@launchSaving
                }
                val payload = ImportPayload(
                    deckName = state.deckName.trim(),
                    pageNames = state.pageNames.mapIndexed { i, name ->
                        name.trim().ifBlank { defaultPageNames.getOrElse(i) { "" }.trim() }
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
                imported = true
                draftClosed = true
                deleteDraft()
                eventSink.send(ImportEvent.Imported)
            } finally {
                if (!imported) _uiState.update { it.copy(importing = false) }
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
            _uiState.update { it.copy(sourceText = parsed.text, rows = emptyList(), detectedPageCount = null) }
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
                detectedPageCount = parsed.pageCount,
                pageNames = List(parsed.pageCount.coerceAtMost(PageLimits.MAX_STORED_PAGE_COUNT)) { i ->
                    nameOverrides[i]?.value ?: parsed.header?.getOrNull(i)?.takeIf { it.isNotBlank() } ?: ""
                },
                pageLanguages = List(parsed.pageCount.coerceAtMost(PageLimits.MAX_STORED_PAGE_COUNT)) { i ->
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
        if (_uiState.value.inputLocked || !_uiState.value.validPageCount) return
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

    /** Użytkownik porzuca import; ekran zaraz się zamknie, a usunięcie szkicu musi się dokończyć. */
    fun discardDraft() {
        draftClosed = true
        viewModelScope.launch(NonCancellable) { deleteDraft() }
    }

    private suspend fun saveDraft(): Unit = draftFile.withLock {
        if (draftClosed) return@withLock
        val state = _uiState.value
        val draft = ImportDraft(
            sourceText = state.sourceText,
            deckName = state.deckName,
            separator = state.separator.takeIf { separatorChosen },
            hasHeader = state.hasHeader.takeIf { headerChosen },
            pageNames = state.pageNames,
            pageLanguages = state.pageLanguages,
            nameOverrides = nameOverrides.toMap(),
            languageOverrides = languageOverrides.toMap()
        )
        try {
            documentFiles.writePrivate(IMPORT_DRAFT_FILE, Json.encodeToString(draft))
            savedState[DRAFT_SAVED_KEY] = true
        } catch (error: IOException) {
            // bez szkicu import działa dalej; przepadnie tylko po śmierci procesu
            Log.w("ImportViewModel", "Import draft not saved", error)
        }
    }

    private suspend fun restoreDraft() {
        val draft = documentFiles.readPrivate(IMPORT_DRAFT_FILE)?.let(::decodeDraft) ?: return
        separatorChosen = draft.separator != null
        headerChosen = draft.hasHeader != null
        nameOverrides += draft.nameOverrides
        languageOverrides += draft.languageOverrides
        _uiState.update { state ->
            state.copy(
                sourceText = draft.sourceText,
                deckName = draft.deckName,
                separator = draft.separator ?: state.separator,
                hasHeader = draft.hasHeader ?: state.hasHeader,
                pageNames = draft.pageNames,
                pageLanguages = draft.pageLanguages
            )
        }
        reparse(debounce = false)
    }

    private suspend fun deleteDraft() = draftFile.withLock {
        savedState[DRAFT_SAVED_KEY] = false
        documentFiles.deletePrivate(IMPORT_DRAFT_FILE)
    }

    /** Użytkownik zmienia sam tekst źródłowy: wygrywa nowy tekst. */
    private fun discardRowEdits() {
        rebuildJob?.cancel()
        rowsEdited = false
    }
}

/** Szkic w innym formacie (np. ze starszej wersji aplikacji) przepada — import zaczyna od zera. */
private fun decodeDraft(json: String): ImportDraft? = try {
    Json.decodeFromString<ImportDraft>(json)
} catch (_: IllegalArgumentException) {
    // także SerializationException
    null
}
