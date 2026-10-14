package com.raycoll.app.ui.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged

/**
 * Pole zapisywane na bieżąco ([onValueChange] przy każdej zmianie). Dopóki użytkownik
 * pisze, zmiany z bazy (np. spóźnione echo zapisu, synchronizacja) nie nadpisują tekstu;
 * po wyjściu z pola pokazuje to, co jest w bazie.
 */
@Composable
fun PersistedTextField(
    storedValue: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isError: (String) -> Boolean = { false }
) {
    var text by rememberSaveable { mutableStateOf(storedValue) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(storedValue, focused) {
        if (!focused) text = storedValue
    }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onValueChange(it)
        },
        label = { Text(label) },
        isError = isError(text),
        singleLine = true,
        keyboardOptions = KeyboardOptions.Default,
        shape = MaterialTheme.shapes.small,
        modifier = modifier.onFocusChanged { focused = it.isFocused }
    )
}
