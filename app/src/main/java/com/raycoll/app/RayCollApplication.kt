package com.raycoll.app

import android.app.Application
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.raycoll.app.data.remote.FirebaseConfig
import com.raycoll.app.data.repository.ArchivePurger
import com.raycoll.app.data.sync.SyncLifecycle
import com.raycoll.app.domain.model.StudyConstants
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class RayCollApplication : Application() {

    @Inject lateinit var syncLifecycle: SyncLifecycle
    @Inject lateinit var archivePurger: ArchivePurger

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        // przed super.onCreate(), zeby Firebase byl gotowy zanim Hilt zbuduje graf zalezny od FirebaseAuth/Firestore
        FirebaseConfig.ensureInitialized(this)
        super.onCreate()
        syncLifecycle.start(appScope)
        // apka schodzi w tło → best-effort flush zaległych zmian do chmury (T10)
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) syncLifecycle.flushOnBackground()
            }
        )
        // purge archiwum: przy starcie apki i co godzinę
        appScope.launch {
            while (true) {
                runCatching { archivePurger.purge() }
                delay(StudyConstants.ARCHIVE_PURGE_INTERVAL_MS)
            }
        }
    }
}
