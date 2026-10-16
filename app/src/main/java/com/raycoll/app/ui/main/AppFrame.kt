package com.raycoll.app.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raycoll.app.R
import com.raycoll.app.ui.components.ExpressiveLoadingIndicator
import com.raycoll.app.ui.components.MigrationDialog
import com.raycoll.app.ui.components.RejectedChangesMessage

/**
 * Oprawa każdego okna aplikacji: motyw, ekran startu konta zamiast [content], komunikat o odrzuconych
 * zmianach i pytanie o dane gościa po zalogowaniu (może przyjść na dowolnym ekranie).
 * [onFramed] — oprawa ułożona z motywem z ustawień.
 */
@Composable
internal fun ComponentActivity.AppFrame(app: MainViewModel, onFramed: () -> Unit, content: @Composable () -> Unit) {
    val preferences by app.preferences.collectAsStateWithLifecycle()
    val migrationRequired by app.migrationRequired.collectAsStateWithLifecycle()
    val migrating by app.migrating.collectAsStateWithLifecycle()
    val migrationFailed by app.migrationFailed.collectAsStateWithLifecycle()
    val prefs = preferences ?: return
    SideEffect(onFramed)
    AppWindow(prefs) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize()) {
                AccountGateContent(app, content)
                // poza Scaffoldem: wcięcie od paska nawigacji systemu ręcznie
                RejectedChangesMessage(
                    app.store,
                    Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                )
            }
            if (migrationRequired) {
                MigrationDialog(
                    busy = migrating,
                    failed = migrationFailed,
                    onChoose = app::resolveMigration
                )
            }
        }
    }
}

/** [content] dopiero z gotowym kontem i (dla konta Google) pierwszymi danymi z serwera. */
@Composable
private fun AccountGateContent(app: MainViewModel, content: @Composable () -> Unit) {
    val gate by app.accountGate.collectAsStateWithLifecycle()
    when (val state = gate) {
        AccountGate.Starting -> CenteredColumn { ExpressiveLoadingIndicator() }

        AccountGate.NeedsInternet -> CenteredColumn {
            Text(stringResource(R.string.auth_start_internet))
            Button(shapes = ButtonDefaults.shapes(), onClick = app::retryStart) {
                Text(stringResource(R.string.auth_retry_start))
            }
        }

        is AccountGate.WaitingForData -> CenteredColumn {
            Text(stringResource(if (state.offline) R.string.auth_data_when_online else R.string.auth_loading_data))
            if (state.showsLoading) ExpressiveLoadingIndicator()
        }

        AccountGate.Open -> content()
    }
}

@Composable
private fun CenteredColumn(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        content()
    }
}
