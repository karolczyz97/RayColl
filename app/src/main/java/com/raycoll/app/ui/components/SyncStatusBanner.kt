package com.raycoll.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.raycoll.app.R
import com.raycoll.app.data.sync.SyncStatus

@Composable
fun SyncStatusBanner(
    status: SyncStatus,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (status == SyncStatus.IDLE) return
    val message = when (status) {
        SyncStatus.SAVING -> stringResource(R.string.sync_status_saving)
        SyncStatus.SYNCING -> stringResource(R.string.sync_status_syncing)
        SyncStatus.ERROR -> stringResource(R.string.sync_status_offline)
        else -> return
    }
    val color = if (status == SyncStatus.ERROR) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
    Row(
        modifier = modifier.fillMaxWidth().background(color).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (status == SyncStatus.ERROR) Icons.Filled.CloudOff else Icons.Filled.Sync,
            contentDescription = null,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(message, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        if (status == SyncStatus.ERROR) {
            TextButton(onClick = onRetry) { Text(stringResource(R.string.errors_retry)) }
        }
    }
}
