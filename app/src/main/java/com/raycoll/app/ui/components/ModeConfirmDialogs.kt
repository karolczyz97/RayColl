package com.raycoll.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.raycoll.app.R

/** Przywrócenie wbudowanego trybu do wersji fabrycznej. */
@Composable
fun ModeResetDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ConfirmDialog(
        title = stringResource(R.string.mode_list_reset_title),
        text = stringResource(R.string.mode_list_reset_desc),
        confirmLabel = stringResource(R.string.mode_list_reset),
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}

/** Usunięcie własnego trybu. */
@Composable
fun ModeDeleteDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ConfirmDialog(
        title = stringResource(R.string.mode_list_delete_title),
        text = stringResource(R.string.mode_list_delete_desc),
        confirmLabel = stringResource(R.string.btn_delete),
        destructive = true,
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}
