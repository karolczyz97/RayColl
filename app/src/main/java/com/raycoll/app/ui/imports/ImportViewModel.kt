package com.raycoll.app.ui.imports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raycoll.app.data.repository.DeckRepository
import com.raycoll.app.domain.imports.detectSeparator
import com.raycoll.app.domain.imports.parseCsv
import com.raycoll.app.domain.imports.detectPageCount
import com.raycoll.app.domain.imports.detectFirstRowHeader
import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.model.createNewSrsState
import com.raycoll.app.core.Ids
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val deckRepository: DeckRepository,
) : ViewModel() {
    fun import(name: String, text: String) {
        viewModelScope.launch {
            val sep = detectSeparator(text)
            val rows = parseCsv(text, sep, 20)
            val hasHeader = detectFirstRowHeader(rows)
            val dataRows = if (hasHeader) rows.drop(1) else rows
            val pageCount = detectPageCount(rows, hasHeader)
            val pageNames = if (hasHeader) rows[0].take(pageCount) else (1..pageCount).map { "Page $it" }
            val pageLangs = pageNames.map { "en-US" }
            val cards = dataRows.map { row ->
                Flashcard(id = Ids.newId(), pages = row.take(pageCount), srsState = createNewSrsState(), contentUpdatedAt = 0, srsUpdatedAt = 0)
            }
            deckRepository.addDeckWithCards(name, pageLangs, pageNames, cards)
        }
    }
}
