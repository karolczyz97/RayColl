package com.raycoll.app.ui.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.window.DialogProperties
import com.raycoll.app.R
import com.raycoll.app.ui.theme.Spacing

/** Minimalna liczba niepustych stron, żeby karta miała sens (pytanie + odpowiedź). */
private const val MIN_FILLED_PAGES = 2

/**
 * Edycja stron karty; zapis dopiero przyciskiem — anulowanie niczego nie zostawia.
 * Szkic przeżywa obrót ekranu i jest przypisany do [draftKey] (id karty), więc tekst
 * jednej karty nie przechodzi do następnej.
 */
@Composable
fun CardEditorDialog(
    title: String,
    draftKey: String,
    initialPages: List<String>,
    pageNames: List<String>,
    onDismiss: () -> Unit,
    onSave: (pages: List<String>) -> Unit,
    saveAttempt: Int = 0,
    autoFocusFirst: Boolean = false,
    /** Usunięcie edytowanej karty (druga droga obok przesunięcia na liście); null — bez przycisku. */
    onDelete: (() -> Unit)? = null
) {
    // tekst i kursor każdej strony przeżywają obrót; nowy szkic zaczyna z kursorem na końcu tekstu
    // key(): każde pole ma własny klucz zapisu stanu (w pętli bez niego pola dzieliłyby jeden)
    val fields = initialPages.mapIndexed { index, page ->
        key(index) {
            rememberSaveable(draftKey, stateSaver = TextFieldValue.Saver) {
                mutableStateOf(TextFieldValue(page, TextRange(page.length)))
            }
        }
    }
    val pages = fields.map { it.value.text }
    var saved by rememberSaveable(draftKey, saveAttempt) { mutableStateOf(false) }
    val firstFieldFocus = remember { FocusRequester() }
    if (autoFocusFirst) {
        LaunchedEffect(Unit) { runCatching { firstFieldFocus.requestFocus() } }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        // dotknięcie poza oknem nie wyrzuca wpisanego tekstu; wyjście: Anuluj lub Wstecz
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.s)
            ) {
                fields.forEachIndexed { index, field ->
                    OutlinedTextField(
                        value = field.value,
                        onValueChange = { field.value = it },
                        label = {
                            Text(pageNames.getOrNull(index) ?: stringResource(R.string.import_page_label, index + 1))
                        },
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (index == 0) Modifier.focusRequester(firstFieldFocus) else Modifier)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                shapes = ButtonDefaults.shapes(),
                onClick = {
                    // drugie dotknięcie, zanim okno zniknie, nie zapisuje drugi raz
                    if (!saved) {
                        saved = true
                        onSave(pages)
                    }
                },
                enabled = !saved && pages.count { it.isNotBlank() } >= MIN_FILLED_PAGES
            ) { Text(stringResource(R.string.btn_save)) }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(
                        shapes = ButtonDefaults.shapes(),
                        onClick = onDelete,
                        enabled = !saved,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text(stringResource(R.string.browse_delete_card)) }
                }
                TextButton(shapes = ButtonDefaults.shapes(), onClick = onDismiss) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        }
    )
}
