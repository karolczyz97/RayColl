package com.raycoll.app.data.files

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Największy odczytywany plik tekstowy; największa realna kopia to kilka MB. */
internal const val MAX_TEXT_BYTES = 20 * 1024 * 1024

private const val READ_BUFFER_BYTES = 8 * 1024

/** Pliki wskazane przez użytkownika w systemowym oknie wyboru (tekst UTF-8). */
@Singleton
class DocumentFiles @Inject constructor(@ApplicationContext private val context: Context) {

    /**
     * Treść pliku (bez BOM) albo null, gdy nie da się go otworzyć ani przeczytać
     * albo jest większy niż [MAX_TEXT_BYTES] — to nie jest kopia ani plik importu.
     */
    suspend fun readText(uri: Uri): String? = io {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.readAtMost(MAX_TEXT_BYTES)?.decodeToString()?.removePrefix("\uFEFF")
        }
    }

    /** Zapisuje [text] w miejsce dotychczasowej treści; false, gdy pliku nie da się otworzyć ani zapisać. */
    suspend fun writeText(uri: Uri, text: String): Boolean = io {
        // "wt": od Androida 10 sam "w" nie zawsze obcina plik, a dłuższy stary plik zostawiłby ogon
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) }
    } != null

    private fun InputStream.readAtMost(limit: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(READ_BUFFER_BYTES)
        while (true) {
            val read = read(buffer)
            if (read < 0) return out.toByteArray()
            if (out.size() + read > limit) return null
            out.write(buffer, 0, read)
        }
    }

    private suspend fun <T> io(block: () -> T?): T? = withContext(Dispatchers.IO) {
        try {
            block()
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        }
    }
}
