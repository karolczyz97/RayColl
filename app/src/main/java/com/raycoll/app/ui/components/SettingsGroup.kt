package com.raycoll.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.raycoll.app.ui.theme.Spacing

/** Tło ekranu ustawień: pozycje w kolorze `surface` odcinają się od niego jak w ustawieniach Androida 16. */
val settingsBackground: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceContainer

/** Pozycje jednej grupy ustawień; kształt każdej zależy od jej miejsca w grupie (pierwsza, środkowa, ostatnia). */
class SettingsGroupScope internal constructor() {
    internal class Entry(val key: Any?, val content: @Composable (ListItemShapes) -> Unit)

    internal val items = mutableListOf<Entry>()

    /** [key] zachowuje stan pozycji, gdy zmienia się jej miejsce w grupie (np. przestawiane strony); bez niego liczy się miejsce. */
    fun item(key: Any? = null, content: @Composable (shapes: ListItemShapes) -> Unit) {
        items += Entry(key, content)
    }
}

/**
 * Grupa ustawień jak w Androidzie 16: nagłówek nad blokiem, a pozycje to zaokrąglone segmenty
 * z wąską przerwą (skrajne mają duże zaokrąglenie na zewnątrz grupy).
 */
@Composable
fun SettingsGroup(title: String?, modifier: Modifier = Modifier, content: SettingsGroupScope.() -> Unit) {
    val items = SettingsGroupScope().apply(content).items
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        if (title != null) SettingsGroupTitle(title)
        items.forEachIndexed { index, item ->
            key(item.key ?: index) { item.content(ListItemDefaults.segmentedShapes(index, items.size)) }
        }
    }
}

/** Nagłówek grupy pozycji; grupy ułożone w jednej leniwej liście używają go bez [SettingsGroup]. */
@Composable
fun SettingsGroupTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .padding(start = Spacing.l, top = Spacing.s, bottom = Spacing.s)
            .semantics { heading() }
    )
}

/** Pozycja z przełącznikiem — klikalna w całości, jak w ustawieniach systemu. */
@Composable
fun SettingsSwitch(
    shapes: ListItemShapes,
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    description: String? = null,
    enabled: Boolean = true
) {
    SegmentedListItem(
        checked = checked,
        onCheckedChange = onChange,
        shapes = shapes,
        enabled = enabled,
        // włączony przełącznik wystarcza; system nie podświetla całego wiersza
        colors = ListItemDefaults.segmentedColors(selectedContainerColor = MaterialTheme.colorScheme.surface),
        supportingContent = description?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) }
    ) { Text(title) }
}

private val BusyIndicatorSize = 24.dp
private val BusyStrokeWidth = 2.dp

/** Pozycja otwierająca inny ekran albo wykonująca akcję; [destructive] — czerwona, [busy] — trwa (kółko zamiast ikony). */
@Composable
fun SettingsAction(
    shapes: ListItemShapes,
    title: String,
    onClick: () -> Unit,
    icon: Painter? = null,
    description: String? = null,
    enabled: Boolean = true,
    destructive: Boolean = false,
    busy: Boolean = false,
    trailingContent: @Composable (() -> Unit)? = null
) {
    val colors = if (destructive) {
        ListItemDefaults.segmentedColors(
            contentColor = MaterialTheme.colorScheme.error,
            leadingContentColor = MaterialTheme.colorScheme.error
        )
    } else {
        ListItemDefaults.segmentedColors()
    }
    SegmentedListItem(
        onClick = onClick,
        shapes = shapes,
        enabled = enabled,
        colors = colors,
        trailingContent = trailingContent,
        leadingContent = when {
            busy -> {
                { CircularProgressIndicator(Modifier.size(BusyIndicatorSize), strokeWidth = BusyStrokeWidth) }
            }

            icon != null -> {
                { Icon(icon, contentDescription = null) }
            }

            else -> null
        },
        supportingContent = description?.let { { Text(it) } }
    ) { Text(title) }
}

/** Pozycja wyboru jednej z kilku opcji (przycisk radiowy po lewej). */
@Composable
fun SettingsRadio(
    shapes: ListItemShapes,
    selected: Boolean,
    onClick: () -> Unit,
    trailingContent: @Composable (() -> Unit)? = null,
    supportingContent: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    SegmentedListItem(
        selected = selected,
        onClick = onClick,
        shapes = shapes,
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        trailingContent = trailingContent,
        supportingContent = supportingContent,
        content = content
    )
}

/** Pozycja z własną treścią (pole tekstowe, suwak, chipy), bez własnej akcji kliknięcia. */
@Composable
fun SettingsBlock(
    shapes: ListItemShapes,
    title: String? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    SegmentedListItem(shapes = shapes, trailingContent = trailingContent) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            if (title != null) Text(title)
            content()
        }
    }
}
