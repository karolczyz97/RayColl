package com.raycoll.app

import android.app.Application
import android.app.UiModeManager
import com.raycoll.app.data.account.AccountManager
import com.raycoll.app.data.local.prefs.ThemePref
import com.raycoll.app.data.local.prefs.UserPreferencesRepository
import com.raycoll.app.data.remote.FirebaseConfig
import com.raycoll.app.di.ApplicationScope
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@HiltAndroidApp
class RayCollApplication : Application() {
    @Inject lateinit var accountManager: AccountManager

    @Inject lateinit var preferences: UserPreferencesRepository

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onCreate() {
        FirebaseConfig.ensureInitialized(this)
        super.onCreate()
        accountManager.start()
        // Android zapisuje tryb aplikacji i odtwarza okna przy zmianie uiMode; Compose czyta tę samą konfigurację.
        applicationScope.launch(Dispatchers.Main.immediate) {
            preferences.preferences.map { it.theme }.distinctUntilChanged().collect { theme ->
                val mode = when (theme) {
                    ThemePref.LIGHT -> UiModeManager.MODE_NIGHT_NO
                    ThemePref.DARK -> UiModeManager.MODE_NIGHT_YES
                    ThemePref.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
                }
                getSystemService(UiModeManager::class.java).setApplicationNightMode(mode)
            }
        }
    }
}
