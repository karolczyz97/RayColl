package com.raycoll.app.ui.appsettings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raycoll.app.data.local.prefs.UserPreferences
import com.raycoll.app.data.local.prefs.UserPreferencesRepository
import com.raycoll.app.data.repository.BackupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream
import javax.inject.Inject

@HiltViewModel
class AppSettingsViewModel @Inject constructor(
    private val prefsRepository: UserPreferencesRepository,
    private val backupRepository: BackupRepository,
) : ViewModel() {
    val preferences: StateFlow<UserPreferences> = prefsRepository.preferences
        .stateIn(viewModelScope, SharingStarted.Lazily, UserPreferences())

    fun setTheme(value: String) { viewModelScope.launch { prefsRepository.setThemePref(value) } }
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
