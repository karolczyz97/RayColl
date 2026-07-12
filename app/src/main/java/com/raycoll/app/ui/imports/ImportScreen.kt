package com.raycoll.app.ui.imports

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raycoll.app.R
import com.raycoll.app.ui.decksettings.PAGE_LANGUAGE_OPTIONS

private val SEPARATOR_OPTIONS = listOf("tab", "semicolon", "comma", "pipe")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(onFinished: () -> Unit, viewModel: ImportViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHost = remember { SnackbarHostState() }

    // Plik przez SAF: text/csv, text/plain, text/markdown — czytany jako UTF-8
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            runCatching {
                context.contentResolver.openInputStream(it)?.use { stream ->
                    viewModel.setSourceText(stream.readBytes().toString(Charsets.UTF_8))
                }
            }
        }
    }

    LaunchedEffect(state.errorMsg) {
        state.errorMsg?.let {
            snackbarHost.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.import_title)) },
                navigationIcon = { IconButton(onClick = onFinished) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) } },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = state.deckName,
                onValueChange = { viewModel.setDeckName(it) },
                label = { Text(stringResource(R.string.dashboard_name_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            OutlinedTextField(
                value = state.sourceText,
                onValueChange = { viewModel.setSourceText(it) },
                label = { Text(stringResource(R.string.import_csv_label)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 5,
                maxLines = 10,
            )

            OutlinedButton(onClick = {
                fileLauncher.launch(arrayOf("text/csv", "text/plain", "text/markdown", "text/comma-separated-values"))
            }) {
                Icon(Icons.Filled.FileOpen, contentDescription = null)
                Text(stringResource(R.string.import_upload_file), Modifier.padding(start = 8.dp))
            }

            if (state.sourceText.isNotBlank()) {
                // Separator: wykryty automatycznie, przełączalny ręcznie
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.import_separator), Modifier.weight(1f))
                    var sepMenu by remember { mutableStateOf(false) }
                    TextButton(onClick = { sepMenu = true }) {
                        Text(separatorLabel(state.separatorKey))
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(expanded = sepMenu, onDismissRequest = { sepMenu = false }) {
                        SEPARATOR_OPTIONS.forEach { key ->
                            DropdownMenuItem(
                                text = { Text(separatorLabel(key)) },
                                onClick = { sepMenu = false; viewModel.setSeparator(key) },
                            )
                        }
                    }
                }

                // Nagłówek: checkbox z auto-sugestią
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = state.hasHeader, onCheckedChange = { viewModel.setHasHeader(it) })
                    Text(stringResource(R.string.import_first_row_header))
                }

                // Strony: nazwa + język (z nagłówka, edytowalne)
                Text(stringResource(R.string.settings_pages_config), style = MaterialTheme.typography.titleMedium)
                for (i in 0 until state.pageCount) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = state.pageNames.getOrElse(i) { "" },
                            onValueChange = { viewModel.setPageName(i, it) },
                            label = { Text(stringResource(R.string.import_page_label, i + 1)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                        var langMenu by remember { mutableStateOf(false) }
                        TextButton(onClick = { langMenu = true }) {
                            Text(state.pageLanguages.getOrElse(i) { "en-US" }.substringBefore('-').uppercase())
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(expanded = langMenu, onDismissRequest = { langMenu = false }) {
                            PAGE_LANGUAGE_OPTIONS.forEach { lang ->
                                DropdownMenuItem(text = { Text(lang) }, onClick = { langMenu = false; viewModel.setPageLanguage(i, lang) })
                            }
                        }
                    }
                }

                // Podgląd wierszy: edycja komórek i usuwanie wierszy (sync z tekstem źródłowym)
                Text(stringResource(R.string.import_preview) + " (${state.dataRows.size})", style = MaterialTheme.typography.titleMedium)
                state.dataRows.forEachIndexed { rowIdx, row ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (colIdx in 0 until state.pageCount) {
                            OutlinedTextField(
                                value = row.getOrElse(colIdx) { "" },
                                onValueChange = { viewModel.updateCell(rowIdx, colIdx, it) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                            )
                        }
                        IconButton(onClick = { viewModel.deleteRow(rowIdx) }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.btn_delete), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            Button(
                onClick = { viewModel.runImport(onSuccess = onFinished) },
                enabled = state.canImport,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.import_title)) }
        }
    }
}

@Composable
private fun separatorLabel(key: String): String = when (key) {
    "tab" -> stringResource(R.string.import_sep_tab)
    "comma" -> stringResource(R.string.import_sep_comma)
    "pipe" -> stringResource(R.string.import_sep_pipe)
    else -> stringResource(R.string.import_sep_semicolon)
}
