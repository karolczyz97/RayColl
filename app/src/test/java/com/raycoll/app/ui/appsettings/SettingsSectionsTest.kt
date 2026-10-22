package com.raycoll.app.ui.appsettings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsToggleable
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.raycoll.app.data.local.prefs.UserPreferences
import com.raycoll.app.data.remote.auth.Account
import com.raycoll.app.ui.components.SyncStatus
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class SettingsSectionsTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `backup and reset actions are disabled during an operation`() {
        compose.setContent { BackupSection(onExport = {}, onImport = {}, onReset = {}, enabled = false) }
        compose.onNodeWithText("Save backup to file").assertIsNotEnabled()
        compose.onNodeWithText("Restore from backup").assertIsNotEnabled()
        compose.onNodeWithText("Restore factory data").assertIsNotEnabled()
    }

    @Test
    @Config(qualifiers = "de-w360dp-h800dp")
    fun `backup actions fit a narrow screen in german`() {
        compose.setContent { Box(Modifier.width(360.dp)) { BackupSection(onExport = {}, onImport = {}, onReset = {}) } }

        listOf("Backup in Datei speichern", "Aus Backup wiederherstellen").forEach { label ->
            val bounds = compose.onNodeWithText(label).getUnclippedBoundsInRoot()
            assertTrue("$label sticks out: $bounds", bounds.right <= 360.dp)
        }
    }

    @Test
    fun `tapping the label of a setting toggles its switch`() {
        var enabled by mutableStateOf(false)
        compose.setContent {
            LanguageSpeechSection(
                preferences = UserPreferences(backgroundPlayback = enabled),
                currentLanguage = "",
                onLanguage = {},
                onTtsRate = {},
                onBackgroundPlayback = { enabled = it }
            )
        }

        compose.onNodeWithText("Background audio").assertIsToggleable().performClick()
        assertTrue(enabled)
    }

    @Test
    fun `sync now waits while a sync is running`() {
        var syncing by mutableStateOf(true)
        compose.setContent {
            AccountSection(
                state = AppSettingsUiState(
                    account = Account("uid", "me@example.com"),
                    syncStatus = if (syncing) SyncStatus.SYNCING else SyncStatus.IDLE
                ),
                onSignIn = {},
                onSignOut = {},
                onSyncNow = {}
            )
        }

        compose.onNodeWithText("Sync now").assertIsNotEnabled()
        syncing = false
        compose.onNodeWithText("Sync now").assertIsEnabled()
    }

    @Test
    fun `app languages match the translations shipped in res`() {
        val resDir = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }
        val shipped = resDir.listFiles().orEmpty()
            .filter { it.isDirectory && it.name.startsWith("values-") && File(it, "strings.xml").exists() }
            .map { it.name.removePrefix("values-") }
            .toSet()
        // pusty tag = język systemu, angielski to wartości domyślne
        assertEquals(shipped + "en", APP_LANGUAGES.filter { it.isNotEmpty() }.toSet())
    }
}
