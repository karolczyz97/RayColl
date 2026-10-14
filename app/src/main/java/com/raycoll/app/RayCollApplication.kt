package com.raycoll.app

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.raycoll.app.data.remote.FirebaseConfig
import com.raycoll.app.data.sync.SyncLifecycle
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class RayCollApplication : Application() {

    @Inject lateinit var syncLifecycle: SyncLifecycle

    override fun onCreate() {
        // przed super.onCreate(): Hilt buduje tam graf, który potrzebuje gotowego Firebase
        FirebaseConfig.ensureInitialized(this)
        super.onCreate()
        syncLifecycle.start()
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) = syncLifecycle.onAppForeground()

                override fun onStop(owner: LifecycleOwner) = syncLifecycle.onAppBackground()
            }
        )
    }
}
