package com.raycoll.app.ui.component

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.raycoll.app.R

@Composable
fun SyncStatusBanner(syncStatus: String, lastError: String? = null, onRetry: () -> Unit = {}) {
    when (syncStatus) {
        "saving" -> Text(stringResource(R.string.sync_status_saving), style = MaterialTheme.typography.bodySmall)
        "syncing" -> Text(stringResource(R.string.sync_status_syncing), style = MaterialTheme.typography.bodySmall)
        "error" -> {
            Text(lastError ?: stringResource(R.string.sync_status_offline), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}
