package com.raycoll.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import com.raycoll.app.R

/** Szkic nazwy przeżywa odtworzenie okna; zapis wyłącznie po zatwierdzeniu, błąd zostawia szkic do ponowienia. */
@Composable
fun NameEditorDialog(
    title: String,
    draftKey: String,
    initialValue: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
    saveAttempt: Int = 0,
    allowBlank: Boolean = false,
    saving: Boolean = false
) {
    var field by rememberSaveable(draftKey, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initialValue, TextRange(0, initialValue.length)))
    }
    // Trwające zadanie należy do ViewModelu; po śmierci procesu nie odtwarzamy fikcyjnego zapisu.
    var submitted by remember(draftKey, saveAttempt) { mutableStateOf(false) }
    val initialAttempt = rememberSaveable(draftKey) { saveAttempt }
    val focus = remember { FocusRequester() }
    val invalid = !allowBlank && field.text.isBlank()
    val failed = saveAttempt != initialAttempt
    val busy = submitted || saving
    val canSave = !busy && !invalid && field.text.trim() != initialValue.trim()
    fun save() {
        if (!canSave || submitted) return
        submitted = true
        onSave(field.text.trim())
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = blurredDialogProperties(dismissOnBackPress = !busy, dismissOnClickOutside = false),
        title = { Text(title) },
        text = {
            AppTextField(
                value = field,
                onValueChange = { field = it },
                label = { Text(title) },
                enabled = !busy,
                singleLine = true,
                isError = invalid || failed,
                supportingText = when {
                    invalid -> {
                        { Text(stringResource(R.string.import_error_name_required)) }
                    }

                    failed -> {
                        { Text(stringResource(R.string.errors_save_failed)) }
                    }

                    else -> null
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus)
            )
        },
        confirmButton = {
            TextButton(shapes = ButtonDefaults.shapes(), enabled = canSave, onClick = ::save) {
                Text(stringResource(R.string.btn_save))
            }
        },
        dismissButton = {
            TextButton(shapes = ButtonDefaults.shapes(), enabled = !busy, onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}
