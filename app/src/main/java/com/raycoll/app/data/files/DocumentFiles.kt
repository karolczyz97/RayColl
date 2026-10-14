package com.raycoll.app.data.files

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Pliki wskazane przez użytkownika w systemowym oknie wyboru (tekst UTF-8). */
@Singleton
class DocumentFiles @Inject constructor(@ApplicationContext private val context: Context) {

    /** Treść pliku albo null, gdy nie da się go otworzyć ani przeczytać. */
    suspend fun readText(uri: Uri): String? = io {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
    }

    /** Zapisuje [text]; false, gdy pliku nie da się otworzyć ani zapisać. */
    suspend fun writeText(uri: Uri, text: String): Boolean = io {
        context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
    } != null

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
