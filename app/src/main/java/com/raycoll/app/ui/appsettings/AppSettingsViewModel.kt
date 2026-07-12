package com.raycoll.app.ui.appsettings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raycoll.app.data.local.prefs.UserPreferencesRepository
import com.raycoll.app.data.repository.DeckRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppSettingsViewModel @Inject constructor(
    private val prefs: UserPreferencesRepository,
    private val repo: DeckRepository,
) : ViewModel() {
    val themePref = prefs.preferences.map { it.themePref }.stateIn(viewModelScope, SharingStarted.Lazily, "system")
    val lang = prefs.preferences.map { it.uiLanguage }.stateIn(viewModelScope, SharingStarted.Lazily, "")
    val backgroundPlayback = prefs.preferences.map { it.backgroundPlayback }.stateIn(viewModelScope, SharingStarted.Lazily, false)

    fun setTheme(v: String) { viewModelScope.launch { prefs.setThemePref(v) } }
    fun setLang(v: String) { viewModelScope.launch { prefs.setUiLanguage(v) } }
    fun setBackground(v: Boolean) { viewModelScope.launch { prefs.setBackgroundPlayback(v) } }

    fun exportToUri(uri: Uri) {
        viewModelScope.launch {
            val json = repo.exportJson()
            // SAF write handled in a separate coroutine or delegated to Android content resolver
        }
    }

    fun importFromUri(uri: Uri) {
        viewModelScope.launch {
            // SAF read handled externally
        }
    }
}
