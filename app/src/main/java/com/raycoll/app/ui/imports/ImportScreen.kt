package com.raycoll.app.ui.imports

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raycoll.app.R
import com.raycoll.app.domain.imports.CsvSeparator
import com.raycoll.app.domain.imports.ImportProblem
import com.raycoll.app.domain.model.PageLimits
import com.raycoll.app.ui.components.CollectEvents
import com.raycoll.app.ui.components.DropdownChip
import com.raycoll.app.ui.components.LanguagePicker
import com.raycoll.app.ui.components.ScreenScaffold
import com.raycoll.app.ui.components.SectionCard
import com.raycoll.app.ui.theme.Spacing

// text/* obejmuje csv, tsv, txt i md; reszta to typy, pod którymi niektóre aplikacje zgłaszają CSV
private val IMPORT_MIME_TYPES = arrayOf("text/*", "application/csv", "application/vnd.ms-excel")

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

    val defaultPageNames = List(state.pageCount) { stringResource(R.string.import_page_label, it + 1) }

    ScreenScaffold(
        title = stringResource(R.string.import_title),
        onBack = onFinished,
        snackbarHostState = snackbarHostState
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f),
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
                    stickyHeader(key = "preview_columns") {
                        ColumnHeaders(state.pageNames, Modifier.background(MaterialTheme.colorScheme.background))
                    }
                    items(count = state.rows.size, key = { "row_$it" }) { rowIndex ->
                        PreviewRow(
                            rowNumber = rowIndex + 1,
                            cells = state.rows[rowIndex],
                            onCellChange = { column, value -> viewModel.updateCell(rowIndex, column, value) },
                            onDelete = { viewModel.deleteRow(rowIndex) }
                        )
                    }
                }
            }
            SubmitBar(state = state, onImport = { viewModel.runImport(defaultPageNames) })
        }
    }
}

/** Przycisk importu przypięty do dołu ekranu (przy setkach wierszy podglądu nie trzeba na niego scrollować). */
@Composable
private fun SubmitBar(state: ImportUiState, onImport: () -> Unit) {
    Surface(tonalElevation = 3.dp) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screen, vertical = Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.s)
        ) {
            val hint = when {
                state.importing || state.canImport || state.sourceText.isBlank() -> null
                state.deckName.isBlank() -> R.string.import_error_name_required
                else -> R.string.import_error_no_cards
            }
            if (hint != null) {
                Text(
                    stringResource(hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = onImport,
                enabled = state.canImport,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                if (state.importing) {
                    CircularProgressIndicator(Modifier.size(ButtonDefaults.IconSize), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.import_title))
                }
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
    SectionCard {
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
            supportingText = { Text(stringResource(R.string.import_hint_format)) },
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

@Composable
private fun ConfigCard(
    state: ImportUiState,
    onSeparator: (CsvSeparator) -> Unit,
    onHasHeader: (Boolean) -> Unit,
    onPageName: (Int, String) -> Unit,
    onPageLanguage: (Int, String) -> Unit
) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.import_separator),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            DropdownChip(
                label = separatorLabel(state.separator),
                options = CsvSeparator.entries,
                optionLabel = { separatorLabel(it) },
                onSelect = onSeparator
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .toggleable(value = state.hasHeader, role = Role.Checkbox, onValueChange = onHasHeader),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s)
        ) {
            Checkbox(checked = state.hasHeader, onCheckedChange = null)
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

/** Nazwy kolumn nad wierszami podglądu — te same wagi i odstępy co w [PreviewRow]. */
@Composable
private fun ColumnHeaders(pageNames: List<String>, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        pageNames.forEachIndexed { index, name ->
            Text(
                name.ifBlank { stringResource(R.string.import_page_label, index + 1) },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = Spacing.l)
            )
        }
        Spacer(Modifier.width(48.dp))
    }
}

@Composable
private fun PreviewRow(rowNumber: Int, cells: List<String>, onCellChange: (Int, String) -> Unit, onDelete: () -> Unit) {
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
                contentDescription = stringResource(
                    R.string.a11y_item_action,
                    stringResource(R.string.btn_delete),
                    rowNumber
                ),
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
    ImportProblem.InvalidPageCount -> arrayOf(PageLimits.MIN_PAGE_COUNT, PageLimits.MAX_STORED_PAGE_COUNT)
    is ImportProblem.InvalidLanguage -> arrayOf(problem.code)
    is ImportProblem.EmptyRow -> arrayOf(problem.rowNumber)
    else -> emptyArray()
}
