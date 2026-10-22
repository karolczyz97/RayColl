package com.raycoll.app.ui.components

import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import com.raycoll.app.R
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

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

/**
 * Usunięcie własnego trybu bez pytania „Na pewno?” — jak fiszki i talie: od razu znika,
 * a komunikat pozwala cofnąć.
 */
@Composable
fun rememberModeDelete(
    messages: SnackbarMessages,
    onDelete: (modeId: String) -> Unit,
    onRestore: (modeId: String) -> Unit
): (modeId: String) -> Unit {
    val scope = rememberCoroutineScope()
    val deleted = stringResource(R.string.mode_list_deleted)
    val undo = stringResource(R.string.btn_undo)
    return { modeId ->
        // Zarejestruj „Cofnij” przed zapisem, żeby szybki błąd mógł od razu zastąpić ten komunikat.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            val result = messages.showAction(deleted, actionLabel = undo, key = "modeUndo")
            if (result == SnackbarResult.ActionPerformed) onRestore(modeId)
        }
        onDelete(modeId)
    }
}
