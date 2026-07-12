package com.raycoll.app.ui.appsettings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raycoll.app.data.local.prefs.UserPreferences
import com.raycoll.app.data.local.prefs.UserPreferencesRepository
import com.raycoll.app.data.remote.auth.AuthRepository
import com.raycoll.app.data.remote.auth.SignInResult
import com.raycoll.app.data.repository.BackupRepository
import com.raycoll.app.data.sync.MigrationController
import com.raycoll.app.data.sync.SyncLifecycle
import com.raycoll.app.data.sync.SyncStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class AccountUiState(
    val signedIn: Boolean = false,
    val email: String = "",
    val signInInProgress: Boolean = false,
)

@HiltViewModel
class AppSettingsViewModel @Inject constructor(
    private val prefsRepository: UserPreferencesRepository,
    private val backupRepository: BackupRepository,
    private val authRepository: AuthRepository,
    private val syncLifecycle: SyncLifecycle,
) : ViewModel() {
    val preferences: StateFlow<UserPreferences> = prefsRepository.preferences
        .stateIn(viewModelScope, SharingStarted.Lazily, UserPreferences())

    val accountState: StateFlow<AccountUiState> = authRepository.authState
        .map { uid ->
            AccountUiState(signedIn = uid.isNotEmpty(), email = authRepository.currentEmail())
        }
        .stateIn(
            viewModelScope, SharingStarted.Lazily,
            AccountUiState(signedIn = authRepository.isSignedIn(), email = authRepository.currentEmail()),
        )

    val migrationRequired: StateFlow<Boolean> = syncLifecycle.migrationRequired
    val syncStatus: StateFlow<SyncStatus> = syncLifecycle.syncStatus

    /** Wynik logowania: null = ok/anulowano, inaczej id stringa błędu. */
    fun signIn(activityContext: Context, onError: (Int) -> Unit) {
        viewModelScope.launch {
            when (val result = authRepository.signInWithGoogle(activityContext)) {
                is SignInResult.Success -> syncLifecycle.runBootstrap()
                is SignInResult.Cancelled -> Unit
                is SignInResult.Error -> onError(result.messageRes)
            }
        }
    }

    fun signOut(activityContext: Context) {
        viewModelScope.launch {
            authRepository.signOut(activityContext)
            syncLifecycle.onSignedOut()
            syncLifecycle.runBootstrap()
        }
    }

    fun resolveMigration(option: MigrationController.MigrationOption) {
        viewModelScope.launch { syncLifecycle.resolveMigration(option) }
    }

    fun syncNow() {
        viewModelScope.launch { syncLifecycle.pushNow() }
    }

    fun setTheme(value: String) { viewModelScope.launch { prefsRepository.setThemePref(value) } }
    fun setUiLanguage(value: String) { viewModelScope.launch { prefsRepository.setUiLanguage(value) } }
    fun setSystemColors(value: Boolean) { viewModelScope.launch { prefsRepository.setUseSystemColors(value) } }
    fun setTtsRate(value: Float) { viewModelScope.launch { prefsRepository.setTtsRate(value) } }
    fun setBackgroundPlayback(value: Boolean) { viewModelScope.launch { prefsRepository.setBackgroundPlayback(value) } }
    fun factoryReset() { viewModelScope.launch { backupRepository.factoryReset() } }

    fun exportBackup(context: Context, uri: Uri, onError: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val json = withContext(Dispatchers.IO) { backupRepository.exportJson() }
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                }
                onError(false)
            } catch (e: Exception) { onError(true) }
        }
    }

    fun importBackup(context: Context, uri: Uri, onSuccess: () -> Unit, onError: () -> Unit) {
        viewModelScope.launch {
            try {
                val json = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                } ?: throw IllegalStateException("Cannot read file")
                withContext(Dispatchers.IO) { backupRepository.importJson(json) }
                onSuccess()
            } catch (e: Exception) { onError() }
        }
    }
}
