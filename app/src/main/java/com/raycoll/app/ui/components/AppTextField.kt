package com.raycoll.app.ui.components

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue

/** Domyślne opcje zwykłego tekstu oraz pól, które mają zachować dosłownie wpisaną treść. */
object AppTextFieldDefaults {
    val keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
    val verbatimKeyboardOptions = KeyboardOptions(
        capitalization = KeyboardCapitalization.None,
        autoCorrectEnabled = false
    )
    val searchKeyboardOptions = verbatimKeyboardOptions.copy(imeAction = ImeAction.Search)

    internal fun resolve(options: KeyboardOptions): KeyboardOptions {
        val defaults = when (options.keyboardType) {
            KeyboardType.Unspecified, KeyboardType.Text -> keyboardOptions
            else -> verbatimKeyboardOptions
        }
        return options.copy(
            capitalization = if (options.capitalization == KeyboardCapitalization.Unspecified) {
                defaults.capitalization
            } else {
                options.capitalization
            },
            autoCorrectEnabled = options.autoCorrectEnabled ?: defaults.autoCorrectEnabled
        )
    }
}

/** Wspólne pole tekstowe: domyślnie wielka litera na początku zdania; jawne opcje pola mają pierwszeństwo. */
@Composable
fun AppTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    isError: Boolean = false,
    label: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        isError = isError,
        label = label,
        supportingText = supportingText,
        keyboardOptions = AppTextFieldDefaults.resolve(keyboardOptions),
        keyboardActions = keyboardActions
    )
}

/** Wariant dla pól, których właściciel przechowuje sam tekst, bez osobnego stanu kursora. */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    isError: Boolean = false,
    label: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        isError = isError,
        label = label,
        supportingText = supportingText,
        keyboardOptions = AppTextFieldDefaults.resolve(keyboardOptions),
        keyboardActions = keyboardActions
    )
}
