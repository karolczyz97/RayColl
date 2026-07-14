package com.raycoll.app.ui.appsettings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raycoll.app.R
import com.raycoll.app.data.local.prefs.UserPreferences
import com.raycoll.app.data.sync.MigrationController
import com.raycoll.app.ui.components.MigrationDialog
import com.raycoll.app.ui.components.SyncStatusBanner
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsScreen(onBack: () -> Unit, viewModel: AppSettingsViewModel = hiltViewModel()) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle(UserPreferences())
    val accountState by viewModel.accountState.collectAsStateWithLifecycle()
    val migrationRequired by viewModel.migrationRequired.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHost = remember { SnackbarHostState() }
    var showResetDialog by remember { mutableStateOf(false) }
    var importError by remember { mutableStateOf(false) }
    var importSuccess by remember { mutableStateOf(false) }
    var authErrorRes by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(authErrorRes) {
        authErrorRes?.let { snackbarHost.showSnackbar(context.getString(it)); authErrorRes = null }
    }

    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm"))
    val exportFilename = "raycoll-backup-$timestamp.json"

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let {
            viewModel.exportBackup(context, it) { error -> if (error) importError = true }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.importBackup(context, it,
                onSuccess = { importSuccess = true },
                onError = { importError = true }
            )
        }
    }

    LaunchedEffect(importError) { if (importError) { snackbarHost.showSnackbar(context.getString(R.string.app_settings_import_error)); importError = false } }
    LaunchedEffect(importSuccess) { if (importSuccess) { snackbarHost.showSnackbar(context.getString(R.string.app_settings_import_success)); importSuccess = false } }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_settings_title)) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) } }) },
        snackbarHost = { SnackbarHost(snackbarHost) }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.app_settings_section_account), style = MaterialTheme.typography.titleMedium)
            if (accountState.signedIn) {
                Text(stringResource(R.string.auth_signed_in_as, accountState.email))
                // banner statusu z przyciskiem Ponów przy błędzie (spec T10)
                SyncStatusBanner(status = syncStatus, onRetry = { viewModel.syncNow() })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewModel.syncNow() }) { Text(stringResource(R.string.auth_sync_now)) }
                    TextButton(onClick = { viewModel.signOut(context) }) { Text(stringResource(R.string.auth_sign_out)) }
                }
            } else {
                Text(stringResource(R.string.auth_local), style = MaterialTheme.typography.bodySmall)
                Button(onClick = {
                    viewModel.signIn(context) { messageRes -> authErrorRes = messageRes }
                }) { Text(stringResource(R.string.auth_sign_in_google)) }
            }

            Text(stringResource(R.string.app_settings_section_appearance), style = MaterialTheme.typography.titleMedium)
            // Material You dostępne od Androida 12
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.app_settings_dynamic_colors_title))
                    Switch(checked = prefs.useSystemColors, onCheckedChange = { viewModel.setSystemColors(it) })
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.app_settings_theme))
                Row {
                    listOf(R.string.app_settings_theme_system to "system", R.string.app_settings_theme_light to "light", R.string.app_settings_theme_dark to "dark").forEach { (label, value) ->
                        TextButton(onClick = { viewModel.setTheme(value) }) {
                            Text(stringResource(label), color = if (prefs.themePref == value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }

            Text(stringResource(R.string.app_settings_section_language_speech), style = MaterialTheme.typography.titleMedium)
            // Język UI: 5 opcji + systemowy; zmiana natychmiastowa (per-app locale), persystowana przez appcompat
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(stringResource(R.string.app_settings_lang))
                var langMenu by remember { mutableStateOf(false) }
                val currentTag = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales().toLanguageTags()
                TextButton(onClick = { langMenu = true }) {
                    Text(languageLabel(currentTag))
                }
                DropdownMenu(expanded = langMenu, onDismissRequest = { langMenu = false }) {
                    listOf("", "pl", "en", "de", "es", "it").forEach { tag ->
                        DropdownMenuItem(
                            text = { Text(languageLabel(tag)) },
                            onClick = {
                                langMenu = false
                                viewModel.setUiLanguage(tag)
                                androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
                                    if (tag.isEmpty()) androidx.core.os.LocaleListCompat.getEmptyLocaleList()
                                    else androidx.core.os.LocaleListCompat.forLanguageTags(tag)
                                )
                            },
                        )
                    }
                }
            }
            Text(stringResource(R.string.app_settings_tts_rate))
            Slider(value = prefs.ttsRate, onValueChange = { viewModel.setTtsRate(it) }, valueRange = 0.5f..2.0f, steps = 14)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.app_settings_background_playback))
                Switch(checked = prefs.backgroundPlayback, onCheckedChange = { viewModel.setBackgroundPlayback(it) })
            }

            Text(stringResource(R.string.app_settings_export_import), style = MaterialTheme.typography.titleMedium)
            Button(onClick = { exportLauncher.launch(exportFilename) }) { Text(stringResource(R.string.app_settings_export_btn)) }
            Button(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "text/csv")) }) { Text(stringResource(R.string.app_settings_import_file_btn)) }

            Button(onClick = { showResetDialog = true }, colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                Text(stringResource(R.string.app_settings_factory_reset))
            }

            // Wersja aplikacji
            val versionName = remember {
                runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
            }
            Text(
                stringResource(R.string.app_settings_version, versionName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }

    if (migrationRequired) {
        MigrationDialog(
            onMigrate = { viewModel.resolveMigration(MigrationController.MigrationOption.MIGRATE) },
            onFresh = { viewModel.resolveMigration(MigrationController.MigrationOption.FRESH) },
            onCancel = { viewModel.resolveMigration(MigrationController.MigrationOption.CANCEL) },
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.app_settings_factory_reset)) },
            text = { Text(stringResource(R.string.app_settings_factory_reset_desc)) },
            confirmButton = { TextButton(onClick = { showResetDialog = false; viewModel.factoryReset() }) { Text(stringResource(R.string.btn_delete)) } },
            dismissButton = { TextButton(onClick = { showResetDialog = false }) { Text(stringResource(R.string.btn_cancel)) } },
        )
    }
}

@Composable
private fun languageLabel(tag: String): String = when (tag.substringBefore('-').lowercase()) {
    "pl" -> stringResource(R.string.lang_pl_PL)
    "en" -> stringResource(R.string.lang_en_US)
    "de" -> stringResource(R.string.lang_de_DE)
    "es" -> stringResource(R.string.lang_es_ES)
    "it" -> stringResource(R.string.lang_it_IT)
    else -> stringResource(R.string.lang_system)
}
