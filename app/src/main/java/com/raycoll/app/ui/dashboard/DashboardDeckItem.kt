package com.raycoll.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.raycoll.app.R
import com.raycoll.app.domain.model.StudyMode
import com.raycoll.app.ui.components.DeckCard
import com.raycoll.app.ui.components.Pill
import com.raycoll.app.ui.components.StudySplitButton
import com.raycoll.app.ui.components.SwipeAction
import com.raycoll.app.ui.components.SwipeReveal
import com.raycoll.app.ui.components.displayName
import com.raycoll.app.ui.components.rememberSwipeRevealState
import com.raycoll.app.ui.components.reorderActions
import com.raycoll.app.ui.theme.Spacing
import kotlinx.coroutines.launch

/** Pulpit dobiera akcje i elementy nauki; wygląd talii i mechanika gestu są współdzielone. */
@Composable
internal fun DashboardDeckItem(
    deck: DeckSummary,
    modes: List<StudyMode>,
    onStudy: () -> Unit,
    onBrowse: () -> Unit,
    onSettings: () -> Unit,
    onArchive: () -> Unit,
    onSelectMode: (String) -> Unit,
    onManageModes: () -> Unit,
    onStartDrag: () -> Unit,
    reorderActions: List<CustomAccessibilityAction>,
    revealed: Boolean,
    onRevealedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    failedWriteKey: Int = 0,
    showHint: Boolean = false,
    onHintShown: () -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme
    val reveal = rememberSwipeRevealState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(revealed) {
        if (!revealed && reveal.isOpen) reveal.close()
    }
    SwipeReveal(
        actions = listOf(
            SwipeAction(
                label = stringResource(R.string.dashboard_deck_options),
                icon = painterResource(R.drawable.ic_settings),
                containerColor = colors.secondaryContainer,
                contentColor = colors.onSecondaryContainer,
                onClick = onSettings
            ),
            SwipeAction(
                label = stringResource(R.string.btn_archive),
                icon = painterResource(R.drawable.ic_archive),
                containerColor = colors.errorContainer,
                contentColor = colors.onErrorContainer,
                removesItem = true,
                onClick = onArchive
            )
        ),
        resetKey = failedWriteKey,
        state = reveal,
        onOpenChange = onRevealedChange,
        showHint = showHint,
        onHintShown = onHintShown,
        modifier = modifier
    ) {
        DeckCard(
            modifier = Modifier.reorderActions(reorderActions),
            name = deck.name,
            stats = deck.stats,
            onClick = { if (reveal.isOpen) scope.launch { reveal.close() } else onBrowse() },
            onLongClick = onStartDrag,
            onLongClickLabel = stringResource(R.string.dashboard_reorder_decks),
            supportingContent = { DashboardDeckSubtitle(deck) },
            trailingContent = {
                StudySplitButton(
                    modes = modes,
                    deckName = deck.name,
                    activeModeId = deck.activeMode.id,
                    hasDue = deck.dueCount > 0,
                    onStudy = onStudy,
                    onSelectMode = onSelectMode,
                    onManageModes = onManageModes
                )
            }
        )
    }
}

@Composable
private fun DashboardDeckSubtitle(deck: DeckSummary) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s)
    ) {
        val hasDue = deck.dueCount > 0
        Pill(
            text = stringResource(R.string.dashboard_due_count, deck.dueCount),
            container = if (hasDue) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
            },
            content = if (hasDue) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            textStyle = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
        )
        Icon(
            painterResource(R.drawable.ic_tune),
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            deck.activeMode.displayName(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}
