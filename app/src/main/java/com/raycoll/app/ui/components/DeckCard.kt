package com.raycoll.app.ui.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.raycoll.app.R
import com.raycoll.app.domain.selectors.CardStats
import com.raycoll.app.ui.theme.Spacing

/** Wspólny wygląd talii; ekran dostarcza podtytuł, element po prawej i obsługę kliknięcia. */
@Composable
fun DeckCard(
    name: String,
    stats: CardStats,
    onClick: () -> Unit,
    supportingContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    trailingContent: (@Composable () -> Unit)? = null
) {
    val browseLabel = stringResource(R.string.a11y_item_action, stringResource(R.string.dashboard_browse), name)
    RayCollCard(modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClickLabel = browseLabel,
                    onLongClickLabel = onLongClickLabel,
                    hapticFeedbackEnabled = false,
                    onLongClick = onLongClick,
                    onClick = onClick
                )
                .padding(Spacing.l)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    Modifier.weight(1f).padding(end = if (trailingContent != null) Spacing.s else 0.dp)
                ) {
                    Text(name, style = MaterialTheme.typography.titleLarge)
                    Column(Modifier.padding(top = Spacing.xs)) { supportingContent() }
                }
                trailingContent?.invoke()
            }
            CardStatsBar(stats, Modifier.padding(top = Spacing.m))
        }
    }
}
