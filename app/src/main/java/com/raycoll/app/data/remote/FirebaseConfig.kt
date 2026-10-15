package com.raycoll.app.data.remote

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.raycoll.app.BuildConfig

/**
 * Ręczna inicjalizacja projektu Firebase `raycoll-io` z istniejącym identyfikatorem web.
 * Klucz API spoza repozytorium (local.properties → BuildConfig).
 * Sam plik google-services.json nie inicjalizuje Firebase: rejestracja Android wymaga
 * również wtyczki google-services, zgodnego applicationId i odcisków podpisów w konsoli.
 * Jeżeli Firebase zostało już zainicjalizowane (np. przez tę wtyczkę), używamy tego stanu.
 */
object FirebaseConfig {
    const val WEB_CLIENT_ID = "328078920834-t68panjarm0k3rva0e4r86jms78ren42.apps.googleusercontent.com"

    private const val PROJECT_ID = "raycoll-io"
    private const val APP_ID = "1:328078920834:web:2fef9d1cba75603542789b"
    private const val SENDER_ID = "328078920834"
    private const val STORAGE_BUCKET = "raycoll-io.firebasestorage.app"

    fun ensureInitialized(context: Context) {
        if (FirebaseApp.getApps(context).isNotEmpty()) return
        val options = FirebaseOptions.Builder()
            .setApiKey(BuildConfig.FIREBASE_API_KEY)
            .setProjectId(PROJECT_ID)
            .setApplicationId(APP_ID)
            .setGcmSenderId(SENDER_ID)
            .setStorageBucket(STORAGE_BUCKET)
            .build()
        FirebaseApp.initializeApp(context, options)
    }
}
