package com.raycoll.app.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.raycoll.app.R

@Composable
fun MigrationDialog(
    onMigrate: () -> Unit,
    onFresh: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.auth_migration_title)) },
        text = { Text(stringResource(R.string.auth_migration_desc)) },
        confirmButton = {
            TextButton(onClick = onMigrate) { Text(stringResource(R.string.auth_migration_migrate_btn)) }
        },
        dismissButton = {
            TextButton(onClick = onFresh) { Text(stringResource(R.string.auth_migration_fresh_btn)) }
        },
    )
}
