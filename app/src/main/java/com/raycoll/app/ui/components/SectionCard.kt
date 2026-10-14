package com.raycoll.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.raycoll.app.ui.theme.Spacing

/** Karta sekcji ekranu z opcjonalnym nagłówkiem (dla czytników ekranu to nagłówek sekcji). */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    spacing: Dp = Spacing.m,
    content: @Composable ColumnScope.() -> Unit
) {
    RayCollCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(spacing)) {
            if (title != null) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() }
                )
            }
            content()
        }
    }
}
