package com.raycoll.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.InfiniteAnimationPolicy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.raycoll.app.R
import com.raycoll.app.ui.theme.Spacing

/** Treść ekranu, która jeszcze się wczytuje. */
@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ExpressiveLoadingIndicator()
    }
}

/**
 * Wskaźnik ładowania Material 3 Expressive (kształt, który płynnie się przekształca).
 * Gdy animacje nieskończone są wyłączone ([InfiniteAnimationPolicy], np. w testach), zwykłe kółko:
 * LoadingIndicator z Material 3 1.5.0-beta01 mimo tej polityki uruchamia pętlę animacji bez końca.
 */
@Composable
fun ExpressiveLoadingIndicator(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    if (scope.coroutineContext[InfiniteAnimationPolicy] == null) {
        LoadingIndicator(modifier)
    } else {
        CircularProgressIndicator(modifier)
    }
}

/** Pusta lista z opcjonalną akcją (np. "Importuj"). */
@Composable
fun EmptyState(
    message: String,
    modifier: Modifier = Modifier,
    icon: Painter = painterResource(R.drawable.ic_inbox),
    action: (@Composable () -> Unit)? = null
) {
    MessageState(message, icon, MaterialTheme.colorScheme.primary, modifier, action)
}

/** Treści nie da się pokazać (np. talię usunięto na innym urządzeniu). */
@Composable
fun ErrorState(message: String, modifier: Modifier = Modifier) {
    MessageState(
        message,
        painterResource(R.drawable.ic_error),
        MaterialTheme.colorScheme.error,
        modifier,
        action = null
    )
}

@Composable
private fun MessageState(
    message: String,
    icon: Painter,
    iconTint: Color,
    modifier: Modifier,
    action: (@Composable () -> Unit)?
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val scrolling = if (constraints.hasBoundedHeight) {
            Modifier.heightIn(min = maxHeight).verticalScroll(rememberScrollState())
        } else {
            Modifier
        }
        Column(
            Modifier.fillMaxWidth().then(scrolling).padding(Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(
                    modifier = Modifier.padding(horizontal = Spacing.xxl, vertical = Spacing.xl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.l)
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = iconTint.copy(alpha = 0.7f)
                    )
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    action?.invoke()
                }
            }
        }
    }
}
