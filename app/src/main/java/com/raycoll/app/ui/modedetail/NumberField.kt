package com.raycoll.app.ui.modedetail

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.raycoll.app.ui.components.AppTextField
import java.math.BigDecimal

/** Jak czytać i pokazywać liczbę danego typu w [NumberField]. */
internal class NumberFormat<T : Comparable<T>>(
    val keyboardType: KeyboardType,
    val parse: (String) -> T?,
    val format: (T) -> String
)

private val INT_TEXT = Regex("\\d+")
private val DECIMAL_TEXT = Regex("\\d*[.,]?\\d*")

internal val IntNumberFormat = NumberFormat(
    keyboardType = KeyboardType.Number,
    parse = { text -> text.trim().takeIf { INT_TEXT.matches(it) }?.toIntOrNull() },
    format = Int::toString
)

/** Przecinek i kropka są równoważne; „2.” i „,5” to poprawne stany przejściowe. */
internal val DecimalNumberFormat = NumberFormat(
    keyboardType = KeyboardType.Decimal,
    parse = { text ->
        text.trim()
            .takeIf { it.any(Char::isDigit) && DECIMAL_TEXT.matches(it) }
            ?.replace(',', '.')
            ?.toDoubleOrNull()
    },
    format = { BigDecimal(it.toString()).stripTrailingZeros().toPlainString() }
)

/**
 * Pole liczby z własnym tekstem. Podczas edycji model nie nadpisuje pola, więc kursor
 * zostaje na miejscu, a stany przejściowe („”, „2.”, „2,”) są dozwolone. Do modelu
 * trafia tylko poprawna liczba z [range]; wartość spoza zakresu jest oznaczona błędem.
 * Po wyjściu z pola (lub „Gotowe”) liczba spoza zakresu jest przycinana do zakresu,
 * a niepoprawny tekst wraca do wartości z modelu. [onValidityChange] pozwala ekranowi
 * zablokować zapis, dopóki pole pokazuje błąd. [hint] to podpowiedź pod poprawną liczbą
 * (np. nazwa oceny); przy błędzie pod polem jest zakres.
 */
@Composable
internal fun <T : Comparable<T>> NumberField(
    label: String,
    value: T,
    range: ClosedRange<T>,
    numberFormat: NumberFormat<T>,
    onChange: (T) -> Unit,
    modifier: Modifier = Modifier,
    onValidityChange: (Boolean) -> Unit = {},
    hint: (T) -> String? = { null }
) {
    val formatted = numberFormat.format(value)
    var text by rememberSaveable { mutableStateOf(formatted) }
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    // Zmiana z innego miejsca (reset trybu, przestawienie) odświeża tylko nieedytowane pole.
    LaunchedEffect(formatted, focused) {
        if (!focused) text = formatted
    }

    val parsed = numberFormat.parse(text)
    val valid = parsed != null && parsed in range
    val currentOnValidityChange by rememberUpdatedState(onValidityChange)
    LaunchedEffect(valid) { currentOnValidityChange(valid) }
    DisposableEffect(Unit) { onDispose { currentOnValidityChange(true) } }

    fun commit() {
        val number = numberFormat.parse(text)
        if (number == null) {
            text = formatted
            return
        }
        val coerced = number.coerceIn(range)
        if (coerced != value) onChange(coerced)
        text = numberFormat.format(coerced)
    }

    AppTextField(
        value = text,
        onValueChange = { input ->
            text = input
            numberFormat.parse(input)?.takeIf { it in range && it != value }?.let(onChange)
        },
        label = { Text(label) },
        isError = !valid,
        supportingText = if (valid) {
            hint(parsed)?.let { hintText -> { Text(hintText) } }
        } else {
            {
                Text("${numberFormat.format(range.start)}–${numberFormat.format(range.endInclusive)}")
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = numberFormat.keyboardType, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        singleLine = true,
        modifier = modifier.onFocusChanged { state ->
            if (focused && !state.isFocused) commit()
            focused = state.isFocused
        }
    )
}
