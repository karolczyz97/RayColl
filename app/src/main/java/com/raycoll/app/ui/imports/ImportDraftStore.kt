package com.raycoll.app.ui.imports

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import com.raycoll.app.data.files.DocumentFiles
import com.raycoll.app.domain.imports.CsvSeparator
import java.io.IOException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Plik szkicu w prywatnych plikach aplikacji (tekst CSV bywa za duży na zapisany stan ekranu). */
internal const val IMPORT_DRAFT_FILE = "import-draft.json"

/** W zapisanym stanie ekranu: szkic jest w [IMPORT_DRAFT_FILE]. */
internal const val DRAFT_SAVED_KEY = "importDraftSaved"

private const val TAG = "ImportViewModel"

/**
 * Ręczna nazwa albo język kolumny. [headerCell] — nagłówek kolumny w chwili zmiany: gdy
 * ta kolumna ma potem inny nagłówek, kolumny zostały przestawione i ustawienie nie
 * przechodzi na cudzą kolumnę.
 */
@Serializable
internal data class ColumnOverride(val value: String, val headerCell: String?)

/** Wszystko, co wpisał i wybrał użytkownik; po śmierci procesu z tego odtwarza się ekran. */
@Serializable
internal data class ImportDraft(
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

/**
 * Szkic importu w prywatnym pliku; znacznik w zapisanym stanie ekranu mówi, czy po śmierci procesu
 * jest co odtwarzać. Zapis i usuwanie idą po kolei — usunięcie nie wyprzedza trwającego zapisu.
 */
internal class ImportDraftStore(private val files: DocumentFiles, private val savedState: SavedStateHandle) {
    private val lock = Mutex()

    /** Import zakończony albo porzucony: szkic nie jest już zapisywany. */
    var closed = false
        private set

    /** Szkic wraca tylko do ekranu odtwarzanego po śmierci procesu; nowy import zaczyna od zera. */
    val shouldRestore: Boolean get() = savedState.get<Boolean>(DRAFT_SAVED_KEY) == true

    fun close() {
        closed = true
    }

    /** [draft] liczony dopiero pod blokadą — zapisuje się najświeższy stan. */
    suspend fun save(draft: () -> ImportDraft): Unit = lock.withLock {
        if (closed) return@withLock
        try {
            files.writePrivate(IMPORT_DRAFT_FILE, Json.encodeToString(draft()))
            savedState[DRAFT_SAVED_KEY] = true
        } catch (error: IOException) {
            // bez szkicu import działa dalej; przepadnie tylko po śmierci procesu
            Log.w(TAG, "Import draft not saved", error)
        }
    }

    suspend fun read(): ImportDraft? = files.readPrivate(IMPORT_DRAFT_FILE)?.let(::decodeDraft)

    suspend fun delete() = lock.withLock {
        savedState[DRAFT_SAVED_KEY] = false
        files.deletePrivate(IMPORT_DRAFT_FILE)
    }
}

/** Szkic w innym formacie (np. ze starszej wersji aplikacji) przepada — import zaczyna od zera. */
private fun decodeDraft(json: String): ImportDraft? = try {
    Json.decodeFromString<ImportDraft>(json)
} catch (e: IllegalArgumentException) {
    // także SerializationException
    Log.w(TAG, "Dropping unreadable import draft", e)
    null
}
