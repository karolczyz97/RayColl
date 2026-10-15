package com.raycoll.app.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.raycoll.app.ui.theme.Spacing

@Composable
fun Pill(
    text: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.labelSmall
) {
    Surface(modifier = modifier, shape = MaterialTheme.shapes.extraSmall, color = container, contentColor = content) {
        Text(text, style = textStyle, modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xxs))
    }
}
