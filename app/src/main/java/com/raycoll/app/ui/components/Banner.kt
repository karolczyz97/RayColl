package com.raycoll.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import com.raycoll.app.ui.theme.Spacing

/** Ramka z komunikatem (informacja, wskazówka, błąd) — ten sam kształt i marginesy na każdym ekranie. */
@Composable
fun Banner(
    icon: Painter,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
        contentColor = contentColor,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.m)
        ) {
            Icon(icon, contentDescription = null)
            ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs), content = content)
            }
            action?.invoke()
        }
    }
}
