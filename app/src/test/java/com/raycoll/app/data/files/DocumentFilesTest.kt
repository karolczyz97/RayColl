package com.raycoll.app.data.files

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Pliki wskazane przez użytkownika: pełne nadpisanie przy zapisie, BOM i limit rozmiaru przy odczycie. */
@RunWith(RobolectricTestRunner::class)
class DocumentFilesTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val files = DocumentFiles(ApplicationProvider.getApplicationContext<Context>())

    private fun file(name: String, bytes: ByteArray = ByteArray(0)) =
        File(folder.root, name).apply { writeBytes(bytes) }

    @Test
    fun `writeText replaces a longer previous content`() = runBlocking {
        val target = file("backup.json", "x".repeat(1_000).toByteArray())
        assertTrue(files.writeText(Uri.fromFile(target), "short"))
        assertEquals("short", target.readText())
    }

    @Test
    fun `readText strips the BOM`() = runBlocking {
        val source = file("backup.json", byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "{}".toByteArray())
        assertEquals("{}", files.readText(Uri.fromFile(source)))
    }

    @Test
    fun `readText refuses a file over the size limit`() = runBlocking {
        val source = file("huge.csv", ByteArray(MAX_TEXT_BYTES + 1) { 'a'.code.toByte() })
        assertNull(files.readText(Uri.fromFile(source)))
        val atLimit = file("limit.csv", ByteArray(MAX_TEXT_BYTES) { 'a'.code.toByte() })
        assertEquals(MAX_TEXT_BYTES, files.readText(Uri.fromFile(atLimit))?.length)
    }

    @Test
    fun `readText of a missing file is null`() = runBlocking {
        assertNull(files.readText(Uri.fromFile(File(folder.root, "missing.json"))))
    }
}
