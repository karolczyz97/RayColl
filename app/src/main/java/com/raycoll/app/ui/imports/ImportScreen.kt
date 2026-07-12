package com.raycoll.app.ui.imports

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.raycoll.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(onFinished: () -> Unit, viewModel: ImportViewModel = hiltViewModel()) {
    var text by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.import_title)) }, navigationIcon = { IconButton(onClick = onFinished) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) } }) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.dashboard_name_label)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text(stringResource(R.string.import_csv_label)) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), minLines = 5)
            Button(onClick = { viewModel.import(name, text); onFinished() }, enabled = name.isNotBlank() && text.isNotBlank()) { Text(stringResource(R.string.btn_add)) }
        }
    }
}
