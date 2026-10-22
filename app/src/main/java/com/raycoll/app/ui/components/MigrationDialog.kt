package com.raycoll.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.raycoll.app.R
import com.raycoll.app.data.account.GuestDataChoice
import com.raycoll.app.ui.theme.Spacing

/**
 * Pierwsze logowanie, gdy gość ma dane: przenieść je czy zacząć od nowa.
 * Okna nie da się zamknąć przypadkiem — rezygnacja z logowania to osobny przycisk.
 */
@Composable
fun MigrationDialog(busy: Boolean = false, failed: Boolean = false, onChoose: (GuestDataChoice) -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        properties = blurredDialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text(stringResource(R.string.auth_migration_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text(stringResource(R.string.auth_choice_desc))
                if (failed) {
                    Text(
                        stringResource(R.string.auth_migration_failed),
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (busy) ExpressiveLoadingIndicator(Modifier.align(Alignment.CenterHorizontally))
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                Button(shapes = ButtonDefaults.shapes(), enabled = !busy, onClick = {
                    onChoose(GuestDataChoice.MOVE_TO_ACCOUNT)
                }) {
                    Text(stringResource(R.string.auth_choice_move))
                }
                TextButton(shapes = ButtonDefaults.shapes(), enabled = !busy, onClick = {
                    onChoose(GuestDataChoice.KEEP_ON_DEVICE)
                }) {
                    Text(stringResource(R.string.auth_choice_keep))
                }
                TextButton(shapes = ButtonDefaults.shapes(), enabled = !busy, onClick = {
                    onChoose(GuestDataChoice.DELETE)
                }) {
                    Text(stringResource(R.string.auth_choice_delete))
                }
                Spacer(Modifier.height(Spacing.s))
                TextButton(shapes = ButtonDefaults.shapes(), enabled = !busy, onClick = {
                    onChoose(GuestDataChoice.CANCEL)
                }) {
                    Text(stringResource(R.string.auth_migration_cancel_btn))
                }
            }
        }
    )
}
