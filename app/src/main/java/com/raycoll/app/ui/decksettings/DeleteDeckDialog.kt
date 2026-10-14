package com.raycoll.app.ui.decksettings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.input.TextFieldValue
import com.raycoll.app.R
import com.raycoll.app.ui.theme.Spacing
import java.text.Normalizer

/**
 * Trwałe usunięcie talii — wymaga wpisania całego słowa potwierdzenia w języku aplikacji;
 * wielkość liter i ogonki nie mają znaczenia („usun”, „Usuń”, „USUŃ”).
 */
@Composable
fun DeleteDeckDialog(deckName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val token = stringResource(R.string.delete_confirm_token)
    var typed by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_delete_permanent_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text(stringResource(R.string.dialog_delete_permanent_desc, deckName))
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    label = { Text(token) },
                    supportingText = { Text(stringResource(R.string.delete_confirm_hint, token)) },
                    shape = MaterialTheme.shapes.small,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = isDeleteConfirmed(typed.text, token),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text(stringResource(R.string.btn_delete)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) } }
    )
}

private val DIACRITICS = Regex("\\p{M}+")

private fun withoutDiacritics(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD).replace(DIACRITICS, "")

/** Całe słowo potwierdzenia, bez względu na wielkość liter, ogonki i spacje na brzegach. */
internal fun isDeleteConfirmed(typed: String, token: String): Boolean =
    withoutDiacritics(typed.trim()).equals(withoutDiacritics(token), ignoreCase = true)
