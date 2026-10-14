package com.raycoll.app.ui.imports

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raycoll.app.R
import com.raycoll.app.domain.imports.CsvSeparator
import com.raycoll.app.domain.imports.ImportProblem
import com.raycoll.app.ui.components.CollectEvents
import com.raycoll.app.ui.components.LanguagePicker
import com.raycoll.app.ui.components.RayCollCard
import com.raycoll.app.ui.components.ScreenScaffold
import com.raycoll.app.ui.theme.Spacing

private val IMPORT_MIME_TYPES = arrayOf("text/csv", "text/plain", "text/markdown", "text/comma-separated-values")

@Composable
fun ImportScreen(onFinished: () -> Unit, viewModel: ImportViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    val snackbarHostState = remember { SnackbarHostState() }
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::loadFile)
    }

    CollectEvents(viewModel.events) { event ->
        val message = when (event) {
            ImportEvent.Imported -> return@CollectEvents onFinished()
            is ImportEvent.Invalid -> resources.getString(problemMessage(event.problem), *problemArgs(event.problem))
            ImportEvent.FileUnreadable -> resources.getString(R.string.import_file_error)
            ImportEvent.SaveFailed -> resources.getString(R.string.errors_save_failed)
            ImportEvent.ColumnSettingsReset -> resources.getString(R.string.import_column_settings_reset)
        }
        snackbarHostState.showSnackbar(message)
    }

    ScreenScaffold(
        title = stringResource(R.string.import_title),
        onBack = onFinished,
        snackbarHostState = snackbarHostState
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
            contentPadding = PaddingValues(Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.m)
        ) {
            item(key = "source") {
                SourceCard(
                    state = state,
                    onDeckName = viewModel::setDeckName,
                    onSourceText = viewModel::onSourceTextChanged,
                    onPickFile = { fileLauncher.launch(IMPORT_MIME_TYPES) }
                )
            }
            if (state.sourceText.isNotBlank()) {
                item(key = "config") {
                    ConfigCard(
                        state = state,
                        onSeparator = viewModel::setSeparator,
                        onHasHeader = viewModel::setHasHeader,
                        onPageName = viewModel::setPageName,
                        onPageLanguage = viewModel::setPageLanguage
                    )
                }
                item(key = "preview_header") {
                    Text(
                        stringResource(R.string.import_preview_count, state.rows.size),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                items(count = state.rows.size, key = { "row_$it" }) { rowIndex ->
                    PreviewRow(
                        cells = state.rows[rowIndex],
                        onCellChange = { column, value -> viewModel.updateCell(rowIndex, column, value) },
                        onDelete = { viewModel.deleteRow(rowIndex) }
                    )
                }
            }
            item(key = "submit") {
                val defaultPageNames = List(state.pageCount) { stringResource(R.string.import_page_label, it + 1) }
                Button(
                    onClick = { viewModel.runImport(defaultPageNames) },
                    enabled = state.canImport,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) { Text(stringResource(R.string.import_title)) }
            }
        }
    }
}

@Composable
private fun SourceCard(
    state: ImportUiState,
    onDeckName: (String) -> Unit,
    onSourceText: (String) -> Unit,
    onPickFile: () -> Unit
) {
    RayCollCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            OutlinedTextField(
                value = state.deckName,
                onValueChange = onDeckName,
                label = { Text(stringResource(R.string.dashboard_name_label)) },
                shape = MaterialTheme.shapes.small,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.sourceText,
                onValueChange = onSourceText,
                label = { Text(stringResource(R.string.import_csv_label)) },
                enabled = !state.importing,
                shape = MaterialTheme.shapes.small,
                minLines = 4,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedButton(onClick = onPickFile, shape = MaterialTheme.shapes.small) {
                Icon(
                    Icons.Filled.FileOpen,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
                Text(stringResource(R.string.import_upload_file), Modifier.padding(start = Spacing.s))
            }
        }
    }
}

@Composable
private fun ConfigCard(
    state: ImportUiState,
    onSeparator: (CsvSeparator) -> Unit,
    onHasHeader: (Boolean) -> Unit,
    onPageName: (Int, String) -> Unit,
    onPageLanguage: (Int, String) -> Unit
) {
    RayCollCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.import_separator),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                SeparatorPicker(state.separator, onSeparator)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = state.hasHeader, onCheckedChange = onHasHeader)
                Text(stringResource(R.string.import_first_row_header), style = MaterialTheme.typography.bodyMedium)
            }
            Text(stringResource(R.string.settings_pages_config), style = MaterialTheme.typography.titleMedium)
            for (index in 0 until state.pageCount) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s)
                ) {
                    OutlinedTextField(
                        value = state.pageNames[index],
                        onValueChange = { onPageName(index, it) },
                        label = { Text(stringResource(R.string.import_page_label, index + 1)) },
                        shape = MaterialTheme.shapes.small,
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    LanguagePicker(state.pageLanguages[index]) { onPageLanguage(index, it) }
                }
            }
        }
    }
}

@Composable
private fun SeparatorPicker(selected: CsvSeparator, onSelect: (CsvSeparator) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        AssistChip(
            onClick = { expanded = true },
            label = { Text(separatorLabel(selected)) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) }
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            CsvSeparator.entries.forEach { separator ->
                DropdownMenuItem(
                    text = { Text(separatorLabel(separator)) },
                    onClick = {
                        expanded = false
                        onSelect(separator)
                    }
                )
            }
        }
    }
}

@Composable
private fun PreviewRow(cells: List<String>, onCellChange: (Int, String) -> Unit, onDelete: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        cells.forEachIndexed { column, value ->
            OutlinedTextField(
                value = value,
                onValueChange = { onCellChange(column, it) },
                shape = MaterialTheme.shapes.small,
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = stringResource(R.string.btn_delete),
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun separatorLabel(separator: CsvSeparator): String = stringResource(
    when (separator) {
        CsvSeparator.TAB -> R.string.import_sep_tab
        CsvSeparator.SEMICOLON -> R.string.import_sep_semicolon
        CsvSeparator.COMMA -> R.string.import_sep_comma
        CsvSeparator.PIPE -> R.string.import_sep_pipe
    }
)

private fun problemMessage(problem: ImportProblem): Int = when (problem) {
    ImportProblem.DeckNameRequired -> R.string.import_error_name_required
    ImportProblem.InvalidPageCount -> R.string.import_error_page_count
    is ImportProblem.InvalidLanguage -> R.string.import_error_language
    ImportProblem.NoCards -> R.string.import_error_no_cards
    is ImportProblem.EmptyRow -> R.string.import_error_empty_row
}

private fun problemArgs(problem: ImportProblem): Array<Any> = when (problem) {
    is ImportProblem.InvalidLanguage -> arrayOf(problem.code)
    is ImportProblem.EmptyRow -> arrayOf(problem.rowNumber)
    else -> emptyArray()
}
