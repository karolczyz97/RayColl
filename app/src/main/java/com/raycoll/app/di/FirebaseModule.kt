package com.raycoll.app.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// Firebase jest inicjalizowany w RayCollApplication przed zbudowaniem grafu Hilta.
@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {
    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance().apply {
        firestoreSettings = com.google.firebase.firestore.FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(
                com.google.firebase.firestore.PersistentCacheSettings.newBuilder()
                    .setSizeBytes(com.google.firebase.firestore.FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED).build()
            ).build()
    }

    @Provides
    @Singleton
    fun provideAuth(): FirebaseAuth = FirebaseAuth.getInstance()
}
