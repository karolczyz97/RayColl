package com.raycoll.app.ui.imports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raycoll.app.data.repository.DeckRepository
import com.raycoll.app.domain.imports.*
import com.raycoll.app.domain.model.Flashcard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ImportUiState(
    val rawText: String = "", val separator: String = "semicolon", val skipFirstRow: Boolean = false,
    val pageCount: Int = 2, val pageNames: List<String> = listOf("Front", "Back"),
    val languages: List<String> = listOf("en-US", "en-US"), val preview: List<List<String>> = emptyList(),
    val deckName: String = "", val error: String? = null, val done: Boolean = false, val confidence: Double = 0.0,
)

@HiltViewModel
class ImportViewModel @Inject constructor(private val repo: DeckRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(ImportUiState()); val uiState: StateFlow<ImportUiState> = _uiState
    fun onTextChanged(text: String) {
        if (text.isBlank()) return
        val sep = detectSeparator(text); val (isHeader, conf) = detectFirstRowHeader(text, sep)
        val pc = detectPageCount(text, sep, isHeader); val rows = parseCSVRaw(text, sep)
        val dataRows = if (isHeader && rows.size > 1) rows.drop(1) else rows
        val names = if (isHeader && rows.isNotEmpty()) rows[0].take(pc).mapIndexed { i, h -> h.ifEmpty { "Page ${i+1}" } } else (1..pc).map { "Page $it" }
        val langs = if (isHeader && rows.isNotEmpty()) rows[0].take(pc).map { detectLangFromHeader(it) } else List(pc) { "en-US" }
        _uiState.value = ImportUiState(text, sep, isHeader, pc, names, langs, dataRows.take(500), confidence = conf)
    }
    fun setDeckName(n: String) { _uiState.value = _uiState.value.copy(deckName = n) }
    fun setSeparator(sep: String) { _uiState.value = _uiState.value.copy(separator = sep); onTextChanged(_uiState.value.rawText) }

    fun import() {
        val s = _uiState.value
        viewModelScope.launch {
            try {
                validateImportDeckPayload(ImportDeckPayload(s.deckName.ifEmpty { "Imported" }, s.languages, s.pageNames, s.preview))
                val data = repo.load()
                val (groups, _) = com.raycoll.app.domain.operations.addGroupWithCards(data.groups, s.deckName.ifEmpty { "Imported" }, s.languages, s.pageNames, s.preview, System.currentTimeMillis())
                repo.save(data.copy(groups = groups))
                _uiState.value = s.copy(done = true)
            } catch (e: Exception) { _uiState.value = s.copy(error = e.message) }
        }
    }
}
