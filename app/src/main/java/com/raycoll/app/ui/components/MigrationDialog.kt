package com.raycoll.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import com.raycoll.app.R
import com.raycoll.app.data.sync.MigrationOption
import com.raycoll.app.ui.theme.Spacing

/**
 * Pierwsze logowanie, gdy gość ma dane: przenieść je czy zacząć od nowa.
 * Okna nie da się zamknąć przypadkiem — rezygnacja z logowania to osobny przycisk.
 */
@Composable
fun MigrationDialog(busy: Boolean = false, failed: Boolean = false, onChoose: (MigrationOption) -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text(stringResource(R.string.auth_migration_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text(stringResource(R.string.auth_migration_desc))
                if (failed) {
                    Text(
                        stringResource(R.string.auth_migration_failed),
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (busy) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                Button(enabled = !busy, onClick = { onChoose(MigrationOption.MIGRATE) }) {
                    Text(stringResource(R.string.auth_migration_migrate_btn))
                }
                TextButton(enabled = !busy, onClick = { onChoose(MigrationOption.FRESH) }) {
                    Text(stringResource(R.string.auth_migration_fresh_btn))
                }
                Spacer(Modifier.height(Spacing.s))
                TextButton(enabled = !busy, onClick = { onChoose(MigrationOption.CANCEL) }) {
                    Text(stringResource(R.string.auth_migration_cancel_btn))
                }
            }
        }
    )
}
