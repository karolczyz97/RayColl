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
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsScreen(onBack: () -> Unit, viewModel: AppSettingsViewModel = hiltViewModel()) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle(UserPreferences())
    val context = LocalContext.current
    val snackbarHost = remember { SnackbarHostState() }
    var showResetDialog by remember { mutableStateOf(false) }
    var importError by remember { mutableStateOf(false) }
    var importSuccess by remember { mutableStateOf(false) }

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
            Text(stringResource(R.string.app_settings_section_appearance), style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.app_settings_dynamic_colors_title))
                Switch(checked = prefs.useSystemColors, onCheckedChange = { viewModel.setSystemColors(it) })
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
                Text(stringResource(R.string.btn_delete))
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.dialog_delete_permanent_title)) },
            text = { Text(stringResource(R.string.dialog_delete_desc)) },
            confirmButton = { TextButton(onClick = { showResetDialog = false; viewModel.factoryReset() }) { Text(stringResource(R.string.btn_delete)) } },
            dismissButton = { TextButton(onClick = { showResetDialog = false }) { Text(stringResource(R.string.btn_cancel)) } },
        )
    }
}
