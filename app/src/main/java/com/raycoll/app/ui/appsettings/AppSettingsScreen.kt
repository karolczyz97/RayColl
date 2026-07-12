package com.raycoll.app.ui.appsettings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.raycoll.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsScreen(onStudyModes: () -> Unit, viewModel: AppSettingsViewModel = hiltViewModel()) {
    val themePref by viewModel.themePref.collectAsState()
    val lang by viewModel.lang.collectAsState()
    val bg by viewModel.backgroundPlayback.collectAsState()

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let { viewModel.exportToUri(it) } }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { viewModel.importFromUri(it) } }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.app_settings_title)) }, navigationIcon = { IconButton(onClick = { }) { Icon(Icons.Default.ArrowBack, "Back") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

            Text(stringResource(R.string.app_settings_section_appearance), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("system" to R.string.app_settings_theme_system, "light" to R.string.app_settings_theme_light, "dark" to R.string.app_settings_theme_dark).forEach { (k, l) -> FilterChip(themePref == k, { viewModel.setTheme(k) }, label = { Text(stringResource(l)) }) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("pl" to R.string.lang_pl_PL, "en" to R.string.lang_en_US, "de" to R.string.lang_de_DE, "es" to R.string.lang_es_ES, "it" to R.string.lang_it_IT).forEach { (k, l) -> FilterChip(lang == k, { viewModel.setLang(k) }, label = { Text(stringResource(l)) }) }
            }
            OutlinedButton(onClick = onStudyModes, Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Icon(Icons.Default.School, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.app_settings_study_modes)) }

            HorizontalDivider()
            Text(stringResource(R.string.app_settings_section_language_speech), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(stringResource(R.string.app_settings_background_playback)); Switch(bg, { viewModel.setBackground(it) }) }

            HorizontalDivider()
            Text(stringResource(R.string.app_settings_export_import), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
            OutlinedButton({ exportLauncher.launch("raycoll-backup-${java.time.LocalDate.now()}.json") }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Icon(Icons.Default.FileDownload, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.app_settings_export_btn)) }
            OutlinedButton({ importLauncher.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Icon(Icons.Default.FileUpload, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.app_settings_import_file_btn)) }
        }
    }
}
