package com.raycoll.app.di

import com.raycoll.app.data.account.AccountManager
import com.raycoll.app.data.account.CurrentAccount
import com.raycoll.app.data.firestore.DocumentStore
import com.raycoll.app.data.firestore.FirestoreDocumentStore
import com.raycoll.app.data.remote.auth.AuthRepository
import com.raycoll.app.data.remote.auth.GoogleSignIn
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {
    @Binds abstract fun bindStore(store: FirestoreDocumentStore): DocumentStore

    @Binds abstract fun bindAccount(manager: AccountManager): CurrentAccount

    @Binds abstract fun bindGoogleSignIn(auth: AuthRepository): GoogleSignIn
}
