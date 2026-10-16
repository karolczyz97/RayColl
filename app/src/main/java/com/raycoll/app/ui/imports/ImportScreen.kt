package com.raycoll.app.ui.imports

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raycoll.app.R
import com.raycoll.app.domain.imports.CsvSeparator
import com.raycoll.app.domain.imports.ImportProblem
import com.raycoll.app.domain.model.PageLimits
import com.raycoll.app.ui.components.CollectEvents
import com.raycoll.app.ui.components.ConfirmDialog
import com.raycoll.app.ui.components.DropdownChip
import com.raycoll.app.ui.components.LanguagePicker
import com.raycoll.app.ui.components.ScreenScaffold
import com.raycoll.app.ui.components.SectionCard
import com.raycoll.app.ui.components.SwitchRow
import com.raycoll.app.ui.components.expandIn
import com.raycoll.app.ui.components.rememberSnackbarMessages
import com.raycoll.app.ui.components.shrinkOut
import com.raycoll.app.ui.navigation.ScreenResult
import com.raycoll.app.ui.theme.Spacing

// text/* obejmuje csv, tsv, txt i md; reszta to typy, pod którymi niektóre aplikacje zgłaszają CSV
private val IMPORT_MIME_TYPES = arrayOf("text/*", "application/csv", "application/vnd.ms-excel")

@Composable
fun ImportScreen(
    onFinished: () -> Unit,
    viewModel: ImportViewModel = hiltViewModel(),
    onCompleted: (ScreenResult) -> Unit = { onFinished() }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    val snackbarHostState = remember { SnackbarHostState() }
    val messages = rememberSnackbarMessages(snackbarHostState)
    var showDiscard by rememberSaveable { mutableStateOf(false) }
    var pendingFile by rememberSaveable { mutableStateOf<String?>(null) }
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            if (state.sourceText.isNotBlank() || state.rows.isNotEmpty()) {
                pendingFile = uri.toString()
            } else {
                viewModel.loadFile(uri)
            }
        }
    }
    val requestBack = {
        if (!state.importing) {
            if (state.hasDraft) showDiscard = true else onFinished()
        }
    }
    BackHandler(onBack = requestBack)
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val windowHeight = LocalWindowInfo.current.containerSize.height
    val importInToolbar = with(LocalDensity.current) { windowHeight.toDp() } < 480.dp && keyboardVisible

    CollectEvents(viewModel.events) { event ->
        val message = when (event) {
            ImportEvent.Imported -> return@CollectEvents onCompleted(ScreenResult.Imported)
            is ImportEvent.Invalid -> resources.getString(problemMessage(event.problem), *problemArgs(event.problem))
            ImportEvent.FileUnreadable -> resources.getString(R.string.import_file_error)
            ImportEvent.SaveFailed -> resources.getString(R.string.errors_save_failed)
            ImportEvent.ColumnSettingsReset -> resources.getString(R.string.import_column_settings_reset)
        }
        messages.showLatest(message)
    }

    val defaultPageNames =
        List(PageLimits.MAX_STORED_PAGE_COUNT) {
            stringResource(
                R.string.import_page_label,
                it + 1
            )
        }

    ScreenScaffold(
        title = stringResource(R.string.import_title),
        onBack = requestBack,
        closeInsteadOfBack = true,
        actions = {
            if (importInToolbar) {
                IconButton(shapes = IconButtonDefaults.shapes(), onClick = {
                    viewModel.runImport(defaultPageNames)
                }, enabled = state.canImport) {
                    if (state.importing) {
                        CircularProgressIndicator(Modifier.size(ButtonDefaults.IconSize))
                    } else {
                        Icon(
                            painterResource(R.drawable.ic_check),
                            contentDescription = stringResource(R.string.import_title)
                        )
                    }
                }
            }
        },
        snackbarHostState = snackbarHostState
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            AnimatedVisibility(state.loadingFile, enter = expandIn(), exit = shrinkOut()) {
                LinearWavyProgressIndicator(Modifier.fillMaxWidth())
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = Spacing.screenContent(),
                verticalArrangement = Arrangement.spacedBy(Spacing.item)
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
                    if (state.validPageCount) {
                        item(key = "preview_header") {
                            Text(
                                stringResource(R.string.import_preview_count, state.rows.size),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.semantics { heading() }
                            )
                        }
                        if (state.pageCount <= 2) {
                            stickyHeader(key = "preview_columns") {
                                ColumnHeaders(
                                    state.pageNames,
                                    Modifier.background(MaterialTheme.colorScheme.background)
                                )
                            }
                        }
                        items(count = state.rows.size, key = { "row_$it" }) { rowIndex ->
                            PreviewRow(
                                rowNumber = rowIndex + 1,
                                cells = state.rows[rowIndex],
                                pageNames = state.pageNames,
                                enabled = !state.inputLocked,
                                onCellChange = { column, value -> viewModel.updateCell(rowIndex, column, value) },
                                onDelete = { viewModel.deleteRow(rowIndex) }
                            )
                        }
                    }
                }
            }
            if (!importInToolbar) SubmitBar(state = state, onImport = { viewModel.runImport(defaultPageNames) })
        }
    }
    if (showDiscard) {
        ConfirmDialog(
            title = stringResource(R.string.mode_editor_discard_title),
            text = stringResource(R.string.import_discard_desc),
            confirmLabel = stringResource(R.string.mode_editor_discard_confirm),
            destructive = true,
            onConfirm = {
                showDiscard = false
                onFinished()
            },
            onDismiss = { showDiscard = false }
        )
    }
    pendingFile?.let { uri ->
        ConfirmDialog(
            title = stringResource(R.string.import_replace_title),
            text = stringResource(R.string.import_replace_desc),
            confirmLabel = stringResource(R.string.import_replace_confirm),
            onConfirm = {
                pendingFile = null
                viewModel.loadFile(uri.toUri())
            },
            onDismiss = { pendingFile = null }
        )
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
                state.inputLocked || state.canImport || state.sourceText.isBlank() -> null
                !state.validPageCount -> R.string.import_error_page_count
                state.deckName.isBlank() -> R.string.import_error_name_required
                else -> R.string.import_error_no_cards
            }
            if (hint != null) {
                Text(
                    if (hint == R.string.import_error_page_count) {
                        stringResource(hint, PageLimits.MIN_PAGE_COUNT, PageLimits.MAX_STORED_PAGE_COUNT)
                    } else {
                        stringResource(hint)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = onImport,
                enabled = state.canImport,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
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
            enabled = !state.inputLocked,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            onValueChange = onDeckName,
            label = { Text(stringResource(R.string.dashboard_name_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = state.sourceText,
            onValueChange = onSourceText,
            label = { Text(stringResource(R.string.import_csv_label)) },
            supportingText = { Text(stringResource(R.string.import_hint_format)) },
            enabled = !state.inputLocked,
            minLines = 4,
            maxLines = 8,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedButton(
            onClick = onPickFile,
            enabled = !state.inputLocked,
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            shapes = ButtonDefaults.shapes()
        ) {
            Icon(
                painterResource(R.drawable.ic_file_open),
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize)
            )
            Text(stringResource(R.string.import_upload_file), Modifier.padding(start = ButtonDefaults.IconSpacing))
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
                selected = state.separator,
                enabled = !state.inputLocked,
                optionLabel = { separatorLabel(it) },
                onSelect = onSeparator
            )
        }
        SwitchRow(
            title = stringResource(R.string.import_first_row_header),
            checked = state.hasHeader,
            enabled = !state.inputLocked,
            onChange = onHasHeader
        )
        if (!state.validPageCount) {
            Text(
                stringResource(
                    R.string.import_error_page_count,
                    PageLimits.MIN_PAGE_COUNT,
                    PageLimits.MAX_STORED_PAGE_COUNT
                ),
                color = MaterialTheme.colorScheme.error
            )
        } else {
            Text(
                stringResource(R.string.settings_pages_config),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics {
                    heading()
                }
            )
            for (index in 0 until state.pageCount) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s)
                ) {
                    OutlinedTextField(
                        value = state.pageNames[index],
                        enabled = !state.inputLocked,
                        onValueChange = { onPageName(index, it) },
                        label = { Text(stringResource(R.string.import_page_label, index + 1)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    LanguagePicker(state.pageLanguages[index], enabled = !state.inputLocked) {
                        onPageLanguage(index, it)
                    }
                }
            }
            if (state.pageCount > PageLimits.MAX_VISIBLE_PAGE_COUNT) {
                Text(
                    stringResource(
                        R.string.import_stored_columns_note,
                        state.pageCount,
                        PageLimits.MAX_VISIBLE_PAGE_COUNT
                    )
                )
            }
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
