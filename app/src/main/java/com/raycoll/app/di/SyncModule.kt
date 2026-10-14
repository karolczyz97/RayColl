package com.raycoll.app.di

import com.raycoll.app.data.remote.auth.AuthRepository
import com.raycoll.app.data.remote.auth.GoogleSignIn
import com.raycoll.app.data.sync.CloudSync
import com.raycoll.app.data.sync.CurrentUser
import com.raycoll.app.data.sync.SyncEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Synchronizacja zależy od wąskich interfejsów chmury i konta — testy dwóch urządzeń podstawiają atrapy. */
@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {
    @Binds
    abstract fun bindCloudSync(engine: SyncEngine): CloudSync

    @Binds
    abstract fun bindCurrentUser(auth: AuthRepository): CurrentUser

    @Binds
    abstract fun bindGoogleSignIn(auth: AuthRepository): GoogleSignIn
}
