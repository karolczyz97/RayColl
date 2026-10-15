package com.raycoll.app.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raycoll.app.data.account.AccountManager
import com.raycoll.app.data.account.GuestDataChoice
import com.raycoll.app.data.local.prefs.UserPreferences
import com.raycoll.app.data.local.prefs.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Stan całej aplikacji: wygląd z ustawień i decyzja o danych gościa po zalogowaniu. */
@HiltViewModel
class MainViewModel @Inject constructor(
    prefsRepository: UserPreferencesRepository,
    private val accountManager: AccountManager
) : ViewModel() {
    /** null do pierwszego odczytu — ekran powitalny czeka, żeby nie mignął zły motyw. */
    val preferences: StateFlow<UserPreferences?> = prefsRepository.preferences
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val accountState = accountManager.state
    fun retryStart() {
        viewModelScope.launch { accountManager.retryStart() }
    }

    val migrationRequired: StateFlow<Boolean> = accountManager.choiceRequired
    private val _migrating = MutableStateFlow(false)
    val migrating = _migrating.asStateFlow()
    private val _migrationFailed = MutableStateFlow(false)
    val migrationFailed = _migrationFailed.asStateFlow()

    fun resolveMigration(option: GuestDataChoice) {
        if (_migrating.value) return
        _migrating.value = true
        _migrationFailed.value = false
        viewModelScope.launch {
            try {
                _migrationFailed.value = !accountManager.resolveChoice(option)
            } finally {
                _migrating.value = false
            }
        }
    }
}
