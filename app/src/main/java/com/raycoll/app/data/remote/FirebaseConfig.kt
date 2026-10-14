package com.raycoll.app.data.remote

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.raycoll.app.BuildConfig

/**
 * Konfiguracja aplikacji typu „web” w projekcie Firebase `raycoll-io`.
 * Klucz API spoza repozytorium (local.properties → BuildConfig).
 * Inicjalizacja ręczna zamiast google-services.json — plik
 * google-services.json (gdy user doda apkę Android w konsoli) ma pierwszeństwo:
 * wtedy FirebaseApp.getApps() nie będzie puste i ten init się nie wykona.
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
