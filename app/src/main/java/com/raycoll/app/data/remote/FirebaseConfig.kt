package com.raycoll.app.data.remote

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.raycoll.app.BuildConfig

/**
 * Ręczna inicjalizacja projektu Firebase `raycoll-io` jako aplikacja Android danego wariantu
 * (identyfikator z BuildConfig, osobny dla debug i release).
 * Klucz API („Android key”) spoza repozytorium (local.properties → BuildConfig).
 * Jeżeli Firebase zostało już zainicjalizowane (np. przez wtyczkę google-services), używamy tego stanu.
 */
object FirebaseConfig {
    /** Klient OAuth typu „web”, którego Google Sign-In wymaga do wydania tokenu dla Firebase Auth. */
    const val WEB_CLIENT_ID = "328078920834-t68panjarm0k3rva0e4r86jms78ren42.apps.googleusercontent.com"

    private const val PROJECT_ID = "raycoll-io"
    private const val SENDER_ID = "328078920834"

    fun ensureInitialized(context: Context) {
        if (FirebaseApp.getApps(context).isNotEmpty()) return
        val options = FirebaseOptions.Builder()
            .setApiKey(BuildConfig.FIREBASE_API_KEY)
            .setProjectId(PROJECT_ID)
            .setApplicationId(BuildConfig.FIREBASE_APP_ID)
            .setGcmSenderId(SENDER_ID)
            .build()
        FirebaseApp.initializeApp(context, options)
    }
}
