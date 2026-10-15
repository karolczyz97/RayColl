package com.raycoll.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.raycoll.app.R
import com.raycoll.app.data.firestore.SyncStatus
import com.raycoll.app.ui.theme.Spacing

/** Stan synchronizacji z chmurą; nic nie pokazuje, gdy wszystko jest wysłane. */
@Composable
fun SyncStatusBanner(status: SyncStatus, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val isError = status == SyncStatus.ERROR
    val message = when (status) {
        SyncStatus.IDLE -> return
        SyncStatus.SYNCING -> stringResource(R.string.sync_status_syncing)
        SyncStatus.ERROR -> stringResource(R.string.sync_status_offline)
    }
    val colors = MaterialTheme.colorScheme
    val contentColor = if (isError) colors.onErrorContainer else colors.onPrimaryContainer
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (isError) colors.errorContainer else colors.primaryContainer,
        contentColor = contentColor,
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (isError) Icons.Filled.CloudOff else Icons.Filled.Sync,
                contentDescription = null,
                modifier = Modifier.padding(end = Spacing.m)
            )
            Text(message, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            if (isError) {
                TextButton(onClick = onRetry) { Text(stringResource(R.string.errors_retry), color = contentColor) }
            }
        }
    }
}
