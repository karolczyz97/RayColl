package com.raycoll.app

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.raycoll.app.data.remote.FirebaseConfig
import com.raycoll.app.data.sync.SyncLifecycle
import com.raycoll.app.playback.SessionEndSync
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class RayCollApplication : Application() {

    @Inject lateinit var syncLifecycle: SyncLifecycle

    @Inject lateinit var sessionEndSync: SessionEndSync

    override fun onCreate() {
        // przed super.onCreate(): Hilt buduje tam graf, który potrzebuje gotowego Firebase
        FirebaseConfig.ensureInitialized(this)
        super.onCreate()
        syncLifecycle.start()
        sessionEndSync.start()
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) = syncLifecycle.onAppForeground()

                override fun onStop(owner: LifecycleOwner) = syncLifecycle.onAppBackground()
            }
        )
    }
}
