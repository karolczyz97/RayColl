package com.raycoll.app.ui.main

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raycoll.app.data.account.AccountManager
import com.raycoll.app.data.account.AccountState
import com.raycoll.app.data.account.GuestDataChoice
import com.raycoll.app.data.firestore.DocumentStore
import com.raycoll.app.data.firestore.decksArrived
import com.raycoll.app.data.local.prefs.UserPreferences
import com.raycoll.app.data.local.prefs.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Co okno pokazuje zamiast ekranu, dopóki konto (a dla konta Google także pierwsze dane z serwera) nie jest gotowe. */
sealed interface AccountGate {
    /** Tylko wskaźnik ładowania — okno nie zaczyna od niego pierwszej klatki (zob. [AppActivity]). */
    val showsLoading: Boolean get() = false

    data object Starting : AccountGate {
        override val showsLoading = true
    }

    data object NeedsInternet : AccountGate

    data class WaitingForData(val offline: Boolean) : AccountGate {
        override val showsLoading get() = !offline
    }

    data object Open : AccountGate
}

/**
 * Stan aplikacji widziany przez okno (Activity; każde ma własną instancję): wygląd z ustawień,
 * konto i decyzja o danych gościa po zalogowaniu.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MainViewModel @Inject constructor(
    prefsRepository: UserPreferencesRepository,
    private val accountManager: AccountManager,
    val store: DocumentStore,
    private val savedState: SavedStateHandle
) : ViewModel() {
    /** null do pierwszego odczytu — ekran powitalny czeka, żeby nie mignął zły motyw. */
    val preferences: StateFlow<UserPreferences?> = prefsRepository.preferences
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val accountState = accountManager.state

    /**
     * true, gdy zalogowano na inne konto niż to, z którym otwarto okno (np. wylogowanie w ustawieniach);
     * konto okna przeżywa śmierć procesu, więc odtworzone okno nie uzna startu konta za zmianę.
     */
    val accountChanged: StateFlow<Boolean> = accountState.filterIsInstance<AccountState.Ready>()
        .map { ready ->
            val openedFor = savedState.get<String>(OPENED_FOR_UID) ?: ready.uid.also { savedState[OPENED_FOR_UID] = it }
            ready.uid != openedFor
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val accountGate: StateFlow<AccountGate> = accountState.flatMapLatest { state ->
        if (state is AccountState.Ready && !state.isGuest) {
            combine(store.decksArrived(state.uid), store.offline) { arrived, offline ->
                if (arrived) AccountGate.Open else AccountGate.WaitingForData(offline)
            }
        } else {
            flowOf(accountGateBeforeData(state))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), accountGateBeforeData(accountState.value))

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

    private companion object {
        const val OPENED_FOR_UID = "openedForUid"
    }
}

/** Bramka bez wiedzy o danych z serwera: konto Google czeka na nie, dopóki ich nie sprawdzi nasłuch. */
private fun accountGateBeforeData(state: AccountState): AccountGate = when (state) {
    AccountState.Starting -> AccountGate.Starting
    AccountState.NeedsInternet -> AccountGate.NeedsInternet
    is AccountState.Ready -> if (state.isGuest) AccountGate.Open else AccountGate.WaitingForData(offline = false)
}
