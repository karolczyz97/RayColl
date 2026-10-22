package com.raycoll.app.data.files

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.net.toFile
import com.raycoll.app.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Największy odczytywany plik tekstowy; największa realna kopia to kilka MB. */
internal const val MAX_TEXT_BYTES = 20 * 1024 * 1024

private const val READ_BUFFER_BYTES = 8 * 1024

/** Pliki wskazane przez użytkownika w systemowym oknie wyboru (tekst UTF-8). */
@Singleton
class DocumentFiles @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    suspend fun readPrivate(name: String): String? = io {
        val file = android.util.AtomicFile(java.io.File(context.filesDir, name))
        if (file.baseFile.exists() ||
            java.io.File(file.baseFile.path + ".bak").exists()
        ) {
            file.openRead().use { it.readAtMost(MAX_TEXT_BYTES)?.decodeToString() }
        } else {
            null
        }
    }
    suspend fun writePrivate(name: String, text: String) = withContext(ioDispatcher) {
        val file = android.util.AtomicFile(java.io.File(context.filesDir, name))
        val stream = file.startWrite()
        try {
            stream.write(text.toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
    }
    suspend fun deletePrivate(name: String) = withContext(ioDispatcher) {
        android.util.AtomicFile(java.io.File(context.filesDir, name)).delete()
    }

    /**
     * Treść pliku (bez BOM) albo null, gdy nie da się go otworzyć ani przeczytać
     * albo jest większy niż [MAX_TEXT_BYTES] — to nie jest kopia ani plik importu.
     */
    suspend fun readText(uri: Uri): String? = io {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.readAtMost(MAX_TEXT_BYTES)?.decodeToString()?.removePrefix("\uFEFF")
        }
    }

    /** Nazwa z selektora plików; brak metadanych nie blokuje odczytu treści. */
    suspend fun displayName(uri: Uri): String? = io {
        val name = if (uri.scheme == "file") {
            uri.toFile().name
        } else {
            try {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                    val column = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (column >= 0 && it.moveToFirst()) it.getString(column) else null
                }
            } catch (error: RuntimeException) {
                Log.w("DocumentFiles", "File name unavailable", error)
                null
            }
        }
        name?.trim()?.takeIf { it.isNotBlank() }
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

    private suspend fun <T> io(block: () -> T?): T? = withContext(ioDispatcher) {
        try {
            block()
        } catch (e: IOException) {
            Log.w("DocumentFiles", "File access failed", e)
            null
        } catch (e: SecurityException) {
            Log.w("DocumentFiles", "File access denied", e)
            null
        }
    }
}
