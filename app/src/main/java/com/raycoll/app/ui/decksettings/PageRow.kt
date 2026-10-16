package com.raycoll.app.ui.decksettings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.raycoll.app.R
import com.raycoll.app.ui.components.LanguagePicker
import com.raycoll.app.ui.components.PersistedTextField
import com.raycoll.app.ui.theme.Spacing

/** Nazwa strony, jej język i przestawianie stron. */
@Composable
fun PageRow(
    pageIndex: Int,
    name: String,
    language: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onNameChange: (String) -> Unit,
    onLanguageChange: (String) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    resetKey: Int = 0
) {
    val focus = LocalFocusManager.current
    // nazwa strony w opisie przycisków, żeby czytnik ekranu odróżniał kolejne wiersze
    val pageName = name.ifBlank { stringResource(R.string.import_page_label, pageIndex + 1) }
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        PersistedTextField(
            storedValue = name,
            onValueChange = onNameChange,
            label = stringResource(R.string.import_page_label, pageIndex + 1),
            modifier = Modifier.weight(1f),
            resetKey = resetKey
        )
        LanguagePicker(language, onSelect = onLanguageChange)
        IconButton(shapes = IconButtonDefaults.shapes(), onClick = {
            focus.clearFocus()
            onMoveUp()
        }, enabled = canMoveUp) {
            Icon(
                painterResource(R.drawable.ic_keyboard_arrow_up),
                contentDescription = stringResource(
                    R.string.a11y_item_action,
                    stringResource(R.string.a11y_move_page_up),
                    pageName
                )
            )
        }
        IconButton(shapes = IconButtonDefaults.shapes(), onClick = {
            focus.clearFocus()
            onMoveDown()
        }, enabled = canMoveDown) {
            Icon(
                painterResource(R.drawable.ic_keyboard_arrow_down),
                contentDescription = stringResource(
                    R.string.a11y_item_action,
                    stringResource(R.string.a11y_move_page_down),
                    pageName
                )
            )
        }
    }
}
