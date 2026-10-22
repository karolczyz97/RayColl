package com.raycoll.app.ui.components

import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.raycoll.app.R
import com.raycoll.app.ui.components.SyncStatus

/** Stan synchronizacji z chmurą; nic nie pokazuje, gdy wszystko jest wysłane. */
@Composable
fun SyncStatusBanner(status: SyncStatus, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val isOffline = status == SyncStatus.OFFLINE
    val message = when (status) {
        SyncStatus.IDLE -> return
        SyncStatus.SYNCING -> stringResource(R.string.sync_status_syncing)
        SyncStatus.OFFLINE -> stringResource(R.string.sync_status_offline)
    }
    val colors = MaterialTheme.colorScheme
    val contentColor = if (isOffline) colors.onErrorContainer else colors.onPrimaryContainer
    Banner(
        icon = painterResource(if (isOffline) R.drawable.ic_cloud_off else R.drawable.ic_sync),
        containerColor = if (isOffline) colors.errorContainer else colors.primaryContainer,
        contentColor = contentColor,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
        action = if (isOffline) {
            {
                TextButton(shapes = ButtonDefaults.shapes(), onClick = onRetry) {
                    Text(stringResource(R.string.errors_retry), color = contentColor)
                }
            }
        } else {
            null
        }
    ) {
        Text(message)
    }
}
