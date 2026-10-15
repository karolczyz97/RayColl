package com.raycoll.app

import android.app.Application
import com.raycoll.app.data.account.AccountManager
import com.raycoll.app.data.remote.FirebaseConfig
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class RayCollApplication : Application() {
    @Inject lateinit var accountManager: AccountManager
    override fun onCreate() {
        FirebaseConfig.ensureInitialized(this)
        super.onCreate()
        accountManager.start()
    }
}
