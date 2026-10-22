package com.raycoll.app.playback

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Zewnętrzny sterujący (zegarek, Asystent, Android Auto) łączy się z sesją przez serwis, więc serwis musi być
 * dostępny dla innych aplikacji i znaleziony po akcji Media3. Bez tego przyciski zegarka są nieaktywne.
 */
@RunWith(RobolectricTestRunner::class)
class StudySessionServiceManifestTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `the session service is exported for outside controllers`() {
        val info = context.packageManager.getServiceInfo(ComponentName(context, StudySessionService::class.java), 0)
        assertTrue(info.exported)
    }

    @Test
    fun `outside controllers find the service by the Media3 action`() {
        val found = context.packageManager.queryIntentServices(
            Intent("androidx.media3.session.MediaSessionService").setPackage(context.packageName),
            PackageManager.MATCH_ALL
        )
        assertEquals(listOf(StudySessionService::class.java.name), found.map { it.serviceInfo.name })
    }
}
