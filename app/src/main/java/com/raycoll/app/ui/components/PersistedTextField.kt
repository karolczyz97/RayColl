package com.raycoll.app.ui.components

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
 * po potwierdzeniu zapisu i wyjściu z pola pokazuje stan z bazy. Nieprawidłowy tekst
 * wraca do zapisanej wartości po wyjściu. [errorText] pojawia się pod polem, gdy [isError].
 */
@Composable
fun PersistedTextField(
    storedValue: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isError: (String) -> Boolean = { false },
    errorText: String? = null
) {
    var text by rememberSaveable { mutableStateOf(storedValue) }
    var pendingValue by rememberSaveable { mutableStateOf<String?>(null) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(storedValue, focused, pendingValue) {
        if (storedValue == pendingValue) pendingValue = null
        if (!focused && (pendingValue == null || isError(text))) {
            text = storedValue
            pendingValue = null
        }
    }
    val error = isError(text)
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            pendingValue = it.trim().takeUnless(isError)
            onValueChange(it)
        },
        label = { Text(label) },
        isError = error,
        supportingText = if (error && errorText != null) {
            { Text(errorText) }
        } else {
            null
        },
        singleLine = true,
        shape = MaterialTheme.shapes.small,
        modifier = modifier.onFocusChanged { focused = it.isFocused }
    )
}
