package com.raycoll.app.ui.archive

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.raycoll.app.R
import com.raycoll.app.ui.components.DeckCard
import com.raycoll.app.ui.components.SwipeAction
import com.raycoll.app.ui.components.SwipeReveal
import com.raycoll.app.ui.components.rememberSwipeRevealState
import kotlinx.coroutines.launch

private const val URGENT_DAYS_LEFT = 3

/** Archiwum dostarcza termin usunięcia i własne akcje do wspólnych komponentów. */
@Composable
internal fun ArchivedDeckItem(
    deck: ArchivedDeck,
    onBrowse: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    revealed: Boolean,
    onRevealedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    failedWriteKey: Int = 0
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
                label = stringResource(R.string.btn_restore),
                icon = painterResource(R.drawable.ic_unarchive),
                containerColor = colors.secondaryContainer,
                contentColor = colors.onSecondaryContainer,
                removesItem = true,
                onClick = onRestore
            ),
            SwipeAction(
                label = stringResource(R.string.archive_delete_permanently),
                icon = painterResource(R.drawable.ic_delete),
                containerColor = colors.errorContainer,
                contentColor = colors.onErrorContainer,
                removesItem = true,
                onClick = onDelete
            )
        ),
        resetKey = failedWriteKey,
        state = reveal,
        onOpenChange = onRevealedChange,
        modifier = modifier
    ) {
        DeckCard(
            name = deck.name,
            stats = deck.stats,
            onClick = { if (reveal.isOpen) scope.launch { reveal.close() } else onBrowse() },
            supportingContent = { ArchiveCountdown(deck.daysLeft) }
        )
    }
}

@Composable
private fun ArchiveCountdown(daysLeft: Int) {
    Text(
        when (daysLeft) {
            0 -> stringResource(R.string.archive_auto_delete_today)
            1 -> stringResource(R.string.archive_auto_delete_tomorrow)
            else -> pluralStringResource(R.plurals.archive_auto_delete_in, daysLeft, daysLeft)
        },
        style = MaterialTheme.typography.bodySmall,
        color = if (daysLeft <= URGENT_DAYS_LEFT) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    )
}
