package com.raycoll.app.ui.imports

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
fun ImportScreen(viewModel: ImportViewModel = hiltViewModel()) {
    val s by viewModel.uiState.collectAsState()
    LaunchedEffect(s.done) { }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.import_title)) }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(s.rawText, { viewModel.onTextChanged(it) }, Modifier.fillMaxWidth().height(150.dp), label = { Text(stringResource(R.string.import_csv_label)) }, shape = RoundedCornerShape(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(s.skipFirstRow, { }, label = { Text(stringResource(R.string.import_first_row_header)) })
                AssistChip(onClick = { }, label = { Text("${stringResource(R.string.import_pages_count)} ${s.pageCount}") }, leadingIcon = { Icon(Icons.Default.Layers, null, Modifier.size(16.dp)) })
            }

            Text(stringResource(R.string.import_separator), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("tab" to R.string.import_sep_tab, "semicolon" to R.string.import_sep_semicolon, "comma" to R.string.import_sep_comma, "pipe" to R.string.import_sep_pipe).forEach { (k, l) -> FilterChip(s.separator == k, { viewModel.setSeparator(k) }, label = { Text(stringResource(l)) }) }
            }

            Text("${stringResource(R.string.import_preview)} (${s.preview.size})", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(8.dp)) { s.preview.take(5).forEach { Text(it.joinToString(" | "), style = MaterialTheme.typography.bodySmall) } }
            }

            OutlinedTextField(s.deckName, { viewModel.setDeckName(it) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.dashboard_name_label)) }, singleLine = true, shape = RoundedCornerShape(12.dp))
            Button({ viewModel.import(); }, Modifier.fillMaxWidth().height(48.dp), enabled = s.preview.isNotEmpty(), shape = RoundedCornerShape(12.dp)) { Icon(Icons.Default.FileUpload, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.import_btn, s.preview.size)) }
        }
    }
    s.error?.let { AlertDialog(onDismissRequest = {}, title = { Text(stringResource(R.string.errors_generic_title)) }, text = { Text(it) }, confirmButton = { TextButton({ viewModel.onTextChanged(s.rawText) }) { Text(stringResource(R.string.common_ok)) } }) }
}
