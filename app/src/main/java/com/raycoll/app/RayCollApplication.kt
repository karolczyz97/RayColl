package com.raycoll.app

import android.app.Application
import com.raycoll.app.data.remote.FirebaseConfig
import com.raycoll.app.data.sync.SyncLifecycle
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Inject

@HiltAndroidApp
class RayCollApplication : Application() {

    @Inject lateinit var syncLifecycle: SyncLifecycle

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        // przed super.onCreate(), zeby Firebase byl gotowy zanim Hilt zbuduje graf zalezny od FirebaseAuth/Firestore
        FirebaseConfig.ensureInitialized(this)
        super.onCreate()
        syncLifecycle.start(appScope)
    }
}
