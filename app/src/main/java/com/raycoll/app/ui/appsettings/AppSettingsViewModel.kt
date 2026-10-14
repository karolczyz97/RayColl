package com.raycoll.app.ui.appsettings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raycoll.app.BuildConfig
import com.raycoll.app.core.Clock
import com.raycoll.app.data.local.prefs.ThemePref
import com.raycoll.app.data.local.prefs.UserPreferences
import com.raycoll.app.data.local.prefs.UserPreferencesRepository
import com.raycoll.app.data.remote.auth.Account
import com.raycoll.app.data.remote.auth.GoogleSignIn
import com.raycoll.app.data.remote.auth.IdTokenResult
import com.raycoll.app.data.remote.auth.SignInError
import com.raycoll.app.data.remote.auth.SignInResult
import com.raycoll.app.data.repository.BackupRepository
import com.raycoll.app.data.repository.BackupResult
import com.raycoll.app.data.sync.SyncLifecycle
import com.raycoll.app.data.sync.SyncStatus
import com.raycoll.app.ui.components.UiEvents
import com.raycoll.app.ui.components.launchSaving
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AppSettingsUiState(
    val preferences: UserPreferences = UserPreferences(),
    val account: Account? = null,
    val signingIn: Boolean = false,
    val syncStatus: SyncStatus = SyncStatus.IDLE,
    val appVersion: String = BuildConfig.VERSION_NAME
)

sealed interface AppSettingsEvent {
    data class SignInFailed(val error: SignInError) : AppSettingsEvent

    data class BackupExported(val result: BackupResult) : AppSettingsEvent

    data class BackupImported(val result: BackupResult) : AppSettingsEvent

    /** Ustawienie albo reset danych się nie zapisały. */
    data object SaveFailed : AppSettingsEvent

    /** Wynik ręcznej synchronizacji. */
    data class SyncFinished(val success: Boolean) : AppSettingsEvent

    /** Dane fabryczne zostały przywrócone. */
    data object FactoryResetDone : AppSettingsEvent
}

@HiltViewModel
class AppSettingsViewModel @Inject constructor(
    private val prefsRepository: UserPreferencesRepository,
    private val backupRepository: BackupRepository,
    private val authRepository: GoogleSignIn,
    private val syncLifecycle: SyncLifecycle,
    private val clock: Clock
) : ViewModel() {
    private val signingIn = MutableStateFlow(false)

    val uiState: StateFlow<AppSettingsUiState> = combine(
        prefsRepository.preferences,
        authRepository.account,
        signingIn,
        syncLifecycle.syncStatus
    ) { preferences, account, signingIn, syncStatus ->
        AppSettingsUiState(preferences, account, signingIn, syncStatus)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AppSettingsUiState(account = authRepository.account.value)
    )

    private val eventSink = UiEvents<AppSettingsEvent>()
    val events: Flow<AppSettingsEvent> = eventSink.flow

    /**
     * Wybór konta Google. Woła go ekran w SWOIM zakresie korutyn: arkusz Credential Managera
     * potrzebuje Activity, więc ta część żyje tyle co ekran (obrót ją przerywa, a ViewModel nie
     * trzyma starej Activity). Logowanie tokenem i synchronizacja idą w zakresie aplikacji.
     */
    suspend fun chooseGoogleAccount(activityContext: Context) {
        if (signingIn.value) return
        signingIn.value = true
        val token = try {
            authRepository.requestGoogleIdToken(activityContext)
        } catch (e: CancellationException) {
            signingIn.value = false
            throw e
        }
        when (token) {
            is IdTokenResult.Token -> {
                val signIn = syncLifecycle.signIn(token.idToken)
                viewModelScope.launch {
                    try {
                        when (val result = signIn.await()) {
                            is SignInResult.Success -> Unit
                            is SignInResult.Failure -> eventSink.send(AppSettingsEvent.SignInFailed(result.error))
                        }
                    } finally {
                        signingIn.value = false
                    }
                }
            }

            IdTokenResult.Cancelled -> signingIn.value = false

            is IdTokenResult.Failure -> {
                signingIn.value = false
                eventSink.send(AppSettingsEvent.SignInFailed(token.error))
            }
        }
    }

    /** Najpierw wysyła zaległe zmiany (błąd chmury nie blokuje wylogowania), potem przechodzi na dane gościa. */
    fun signOut() {
        syncLifecycle.signOut()
    }

    /** Ręczna synchronizacja kończy się komunikatem — inaczej nie widać, czy coś się stało. */
    fun syncNow() {
        viewModelScope.launch { eventSink.send(AppSettingsEvent.SyncFinished(syncLifecycle.pushNow())) }
    }

    fun setTheme(value: ThemePref) = launch { prefsRepository.setTheme(value) }

    fun setSystemColors(value: Boolean) = launch { prefsRepository.setUseSystemColors(value) }

    fun setTtsRate(value: Float) = launch { prefsRepository.setTtsRate(value) }

    fun setBackgroundPlayback(value: Boolean) = launch { prefsRepository.setBackgroundPlayback(value) }

    fun factoryReset() = launch {
        backupRepository.factoryReset()
        eventSink.send(AppSettingsEvent.FactoryResetDone)
    }

    fun backupFileName(): String =
        "raycoll-backup-" + BACKUP_TIMESTAMP.format(Instant.ofEpochMilli(clock.now())) + ".json"

    fun exportBackup(uri: Uri) = launch {
        eventSink.send(AppSettingsEvent.BackupExported(backupRepository.exportTo(uri)))
    }

    fun importBackup(uri: Uri) = launch {
        eventSink.send(AppSettingsEvent.BackupImported(backupRepository.importFrom(uri)))
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launchSaving(onFailure = { eventSink.send(AppSettingsEvent.SaveFailed) }, block = block)
    }

    private companion object {
        val BACKUP_TIMESTAMP: DateTimeFormatter = DateTimeFormatter.ofPattern(
            "yyyy-MM-dd-HH-mm"
        ).withZone(ZoneId.systemDefault())
    }
}
