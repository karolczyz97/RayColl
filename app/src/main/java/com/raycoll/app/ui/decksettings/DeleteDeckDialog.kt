package com.raycoll.app.ui.decksettings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import com.raycoll.app.R
import com.raycoll.app.ui.components.blurredDialogProperties
import com.raycoll.app.ui.theme.Spacing
import org.apache.commons.lang3.StringUtils

/**
 * Trwałe usunięcie talii — wymaga wpisania całego słowa potwierdzenia w języku aplikacji;
 * wielkość liter i ogonki nie mają znaczenia („usun”, „Usuń”, „USUŃ”).
 */
@Composable
fun DeleteDeckDialog(deckName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val token = stringResource(R.string.delete_confirm_token)
    var typed by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    val confirmed = isDeleteConfirmed(typed.text, token)
    val confirmIfValid = { if (confirmed) onConfirm() }
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = blurredDialogProperties(),
        title = { Text(stringResource(R.string.dialog_delete_permanent_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text(stringResource(R.string.dialog_delete_permanent_desc, deckName))
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    label = { Text(token) },
                    supportingText = { Text(stringResource(R.string.delete_confirm_hint, token)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { confirmIfValid() }),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                shapes = ButtonDefaults.shapes(),
                onClick = confirmIfValid,
                enabled = confirmed,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text(stringResource(R.string.btn_delete)) }
        },
        dismissButton = {
            TextButton(shapes = ButtonDefaults.shapes(), onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}

/** Całe słowo potwierdzenia, bez względu na wielkość liter, ogonki i spacje na brzegach. */
internal fun isDeleteConfirmed(typed: String, token: String): Boolean =
    StringUtils.stripAccents(typed.trim()).equals(StringUtils.stripAccents(token), ignoreCase = true)
