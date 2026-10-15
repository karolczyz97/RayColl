package com.raycoll.app.ui.appsettings

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.core.os.LocaleListCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raycoll.app.R
import com.raycoll.app.data.remote.auth.SignInError
import com.raycoll.app.data.repository.BackupResult
import com.raycoll.app.ui.components.CollectEvents
import com.raycoll.app.ui.components.ConfirmDialog
import com.raycoll.app.ui.components.ScreenScaffold
import com.raycoll.app.ui.components.rememberSnackbarMessages
import com.raycoll.app.ui.theme.Spacing
import kotlinx.coroutines.launch

private const val BACKUP_MIME_TYPE = "application/json"

@Composable
fun AppSettingsScreen(onBack: () -> Unit, onStudyModes: () -> Unit, viewModel: AppSettingsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    val snackbarHostState = remember { SnackbarHostState() }
    val messages = rememberSnackbarMessages(snackbarHostState)
    var showResetDialog by rememberSaveable { mutableStateOf(false) }
    var showImportDialog by rememberSaveable { mutableStateOf(false) }

    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BACKUP_MIME_TYPE)) { uri ->
            uri?.let(viewModel::exportBackup)
        }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importBackup)
    }
    val notificationsOff = stringResource(R.string.app_settings_notifications_off)
    val notificationsOpen = stringResource(R.string.app_settings_notifications_open)
    val notificationPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            // nauka w tle działa dalej, ale kontrolki w powiadomieniu są niewidoczne — warto o tym powiedzieć
            if (!granted) {
                scope.launch {
                    val result = messages.showAction(notificationsOff, actionLabel = notificationsOpen)
                    if (result == SnackbarResult.ActionPerformed) openNotificationSettings(context)
                }
            }
        }

    CollectEvents(viewModel.events) { event ->
        val message = when (event) {
            is AppSettingsEvent.SignInFailed -> signInErrorMessage(event.error)

            is AppSettingsEvent.BackupExported -> when (event.result) {
                BackupResult.SUCCESS -> R.string.app_settings_export_success
                BackupResult.FILE_ERROR, BackupResult.INVALID_BACKUP -> R.string.app_settings_export_error
            }

            is AppSettingsEvent.BackupImported -> when (event.result) {
                BackupResult.SUCCESS -> R.string.app_settings_import_success
                BackupResult.FILE_ERROR -> R.string.app_settings_import_error
                BackupResult.INVALID_BACKUP -> R.string.app_settings_import_invalid
            }

            AppSettingsEvent.SignOutFailed -> R.string.auth_sign_out_failed

            AppSettingsEvent.SaveFailed -> R.string.errors_save_failed

            is AppSettingsEvent.SyncFinished ->
                if (event.success) R.string.sync_now_success else R.string.sync_now_failed

            AppSettingsEvent.FactoryResetDone -> R.string.app_settings_factory_reset_done
        }
        messages.showLatest(resources.getString(message))
    }

    ScreenScaffold(
        title = stringResource(R.string.app_settings_title),
        onBack = onBack,
        snackbarHostState = snackbarHostState
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.l)
        ) {
            AccountSection(
                state = uiState,
                // wybór konta w zakresie ekranu — obrót go przerywa i nie zostawia Activity w ViewModelu
                onSignIn = { scope.launch { viewModel.chooseGoogleAccount(context) } },
                onSignOut = viewModel::signOut,
                onSyncNow = viewModel::syncNow
            )
            val preferences = uiState.preferences
            if (preferences == null) {
                CircularProgressIndicator()
            } else {
                AppearanceSection(
                    preferences = preferences,
                    onTheme = viewModel::setTheme,
                    onSystemColors = viewModel::setSystemColors
                )
                LanguageSpeechSection(
                    preferences = preferences,
                    currentLanguage = AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore('-'),
                    onLanguage = { tag ->
                        AppCompatDelegate.setApplicationLocales(
                            if (tag.isEmpty()) {
                                LocaleListCompat.getEmptyLocaleList()
                            } else {
                                LocaleListCompat.forLanguageTags(
                                    tag
                                )
                            }
                        )
                    },
                    onTtsRate = viewModel::setTtsRate,
                    onBackgroundPlayback = { enabled ->
                        viewModel.setBackgroundPlayback(enabled)
                        // kontrolki nauki w tle są w powiadomieniu
                        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                )
            }
            StudyModesLink(onStudyModes)
            val backupsEnabled = uiState.backupOperation == null && !uiState.signingIn && !uiState.signingOut
            if (uiState.backupOperation != null) LinearProgressIndicator()
            BackupSection(
                enabled = backupsEnabled,
                onExport = { exportLauncher.launch(viewModel.backupFileName()) },
                onImport = { showImportDialog = true }
            )
            FactoryResetSection(onReset = { showResetDialog = true }, enabled = backupsEnabled)
            Text(
                stringResource(R.string.app_settings_version, uiState.appVersion),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Spacing.s)
            )
        }
    }

    if (showImportDialog) {
        ConfirmDialog(
            title = stringResource(R.string.app_settings_import_confirm_title),
            text = stringResource(R.string.app_settings_import_confirm_desc),
            confirmLabel = stringResource(R.string.app_settings_import_file_btn),
            destructive = true,
            onConfirm = {
                showImportDialog = false
                importLauncher.launch(arrayOf(BACKUP_MIME_TYPE, "text/plain"))
            },
            onDismiss = { showImportDialog = false }
        )
    }
    if (showResetDialog) {
        ConfirmDialog(
            title = stringResource(R.string.app_settings_factory_reset),
            text = stringResource(R.string.app_settings_factory_reset_desc),
            confirmLabel = stringResource(R.string.app_settings_factory_reset),
            destructive = true,
            onConfirm = {
                showResetDialog = false
                viewModel.factoryReset()
            },
            onDismiss = { showResetDialog = false }
        )
    }
}

private fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // brak ekranu ustawień powiadomień na tym urządzeniu — nic więcej nie da się zrobić
    }
}

private fun signInErrorMessage(error: SignInError): Int = when (error) {
    SignInError.PLAY_SERVICES -> R.string.auth_error_play_services
    SignInError.NO_TOKEN -> R.string.auth_error_no_token
    SignInError.INVALID_TOKEN -> R.string.auth_error_invalid_token
    SignInError.LOGIN_FAILED -> R.string.auth_error_login_failed
    SignInError.LOCAL_DATA -> R.string.auth_error_local_data
}
