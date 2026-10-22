package com.raycoll.app.ui.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
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
import com.raycoll.app.R
import com.raycoll.app.ui.components.AppTextField
import com.raycoll.app.ui.components.blurredDialogProperties
import com.raycoll.app.ui.components.pageDisplayName
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
    saving: Boolean = false
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
    // Zadanie zapisu należy do ViewModelu; po odtworzeniu procesu ta lokalna blokada nie wraca.
    var submitted by remember(draftKey, saveAttempt) { mutableStateOf(false) }
    val busy = submitted || saving
    val firstFieldFocus = remember { FocusRequester() }
    if (autoFocusFirst) {
        // requestFocus rzuca, gdy pole nie jest jeszcze podpięte — wtedy po prostu bez fokusu
        LaunchedEffect(Unit) { runCatching { firstFieldFocus.requestFocus() } }
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        // dotknięcie poza oknem nie wyrzuca wpisanego tekstu; wyjście: Anuluj lub Wstecz
        properties = blurredDialogProperties(dismissOnBackPress = !busy, dismissOnClickOutside = false),
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
                    AppTextField(
                        value = field.value,
                        onValueChange = { field.value = it },
                        enabled = !busy,
                        label = {
                            Text(pageDisplayName(pageNames.getOrNull(index), index))
                        },
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
                    if (!busy) {
                        submitted = true
                        onSave(pages)
                    }
                },
                enabled = !busy && pages.count { it.isNotBlank() } >= MIN_FILLED_PAGES
            ) { Text(stringResource(R.string.btn_save)) }
        },
        dismissButton = {
            TextButton(shapes = ButtonDefaults.shapes(), onClick = onDismiss, enabled = !busy) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}
