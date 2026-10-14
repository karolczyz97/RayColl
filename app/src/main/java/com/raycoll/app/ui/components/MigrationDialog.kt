package com.raycoll.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import com.raycoll.app.R
import com.raycoll.app.data.sync.MigrationOption

/**
 * Pierwsze logowanie, gdy gość ma dane: przenieść je czy zacząć od nowa.
 * Okna nie da się zamknąć przypadkiem — rezygnacja z logowania to osobny przycisk.
 */
@Composable
fun MigrationDialog(onChoose: (MigrationOption) -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text(stringResource(R.string.auth_migration_title)) },
        text = { Text(stringResource(R.string.auth_migration_desc)) },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = { onChoose(MigrationOption.MIGRATE) }) {
                    Text(stringResource(R.string.auth_migration_migrate_btn))
                }
                TextButton(onClick = { onChoose(MigrationOption.FRESH) }) {
                    Text(stringResource(R.string.auth_migration_fresh_btn))
                }
                TextButton(onClick = { onChoose(MigrationOption.CANCEL) }) {
                    Text(stringResource(R.string.auth_migration_cancel_btn))
                }
            }
        }
    )
}
