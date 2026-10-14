package com.raycoll.app.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raycoll.app.data.local.prefs.UserPreferences
import com.raycoll.app.data.local.prefs.UserPreferencesRepository
import com.raycoll.app.data.sync.MigrationOption
import com.raycoll.app.data.sync.SyncLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Stan całej aplikacji: wygląd z ustawień i decyzja o danych gościa po zalogowaniu. */
@HiltViewModel
class MainViewModel @Inject constructor(
    prefsRepository: UserPreferencesRepository,
    private val syncLifecycle: SyncLifecycle
) : ViewModel() {
    /** null do pierwszego odczytu — ekran powitalny czeka, żeby nie mignął zły motyw. */
    val preferences: StateFlow<UserPreferences?> = prefsRepository.preferences
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val migrationRequired: StateFlow<Boolean> = syncLifecycle.migrationRequired

    fun resolveMigration(option: MigrationOption) {
        viewModelScope.launch { syncLifecycle.resolveMigration(option) }
    }
}
