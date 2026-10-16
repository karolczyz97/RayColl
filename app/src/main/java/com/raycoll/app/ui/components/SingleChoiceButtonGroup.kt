package com.raycoll.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.raycoll.app.ui.theme.Spacing

private val CompactPadding = PaddingValues(horizontal = Spacing.xs, vertical = Spacing.s)

/**
 * Wybór jednej z kilku krótkich opcji: połączona grupa przycisków Material 3 Expressive
 * (jak w ustawieniach Androida). Dla długich nazw opcji lepsza jest lista z [SettingsRadio].
 */
@Composable
fun <T> SingleChoiceButtonGroup(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** Wąskie wnętrze przycisku — gdy opcji jest dużo (np. pięć filtrów na szerokość telefonu). */
    compact: Boolean = false,
    /** Względna szerokość przycisku [index] (domyślnie wszystkie równe). */
    weight: (index: Int) -> Float = { 1f },
    label: @Composable (value: T, text: String) -> Unit = { _, text -> Text(text, textAlign = TextAlign.Center) }
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
    ) {
        options.forEachIndexed { index, (value, text) ->
            ToggleButton(
                checked = value == selected,
                onCheckedChange = { onSelect(value) },
                enabled = enabled,
                contentPadding = if (compact) {
                    CompactPadding
                } else {
                    ToggleButtonDefaults.contentPaddingFor(
                        ButtonDefaults.MinHeight
                    )
                },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                modifier = Modifier
                    .weight(weight(index))
                    // jak przycisk radiowy: czytnik ekranu ogłasza „wybrane”, nie „włączone”
                    .semantics {
                        role = Role.RadioButton
                        this.selected = value == selected
                    }
            ) { label(value, text) }
        }
    }
}
