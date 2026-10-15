package com.raycoll.app.ui.components

import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import com.raycoll.app.R
import com.raycoll.app.data.firestore.DocumentStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun RejectedChangesMessage(store: DocumentStore, modifier: Modifier = Modifier) {
    val host = remember { SnackbarHostState() }
    val resources by rememberUpdatedState(LocalResources.current)
    LaunchedEffect(store) {
        var total = 0
        var window: Job? = null
        store.rejections.collect { count ->
            total += count
            if (window == null) {
                window = launch {
                    delay(2_000)
                    val rejected = total
                    total = 0
                    window = null
                    host.showSnackbar(resources.getQuantityString(R.plurals.sync_changes_rejected, rejected, rejected))
                }
            }
        }
    }
    SnackbarHost(host, modifier)
}
