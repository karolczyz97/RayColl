package com.raycoll.app.ui.study

import android.Manifest
import android.app.Application
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import com.raycoll.app.domain.session.SessionState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SessionPermissionsTest {
    @get:Rule val compose = createComposeRule()
    private val requested = mutableListOf<String>()
    private val registry = object : ActivityResultRegistry() {
        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?
        ) {
            requested += input as String
            dispatchResult(requestCode, false)
        }
    }
    private val owner = object : ActivityResultRegistryOwner {
        override val activityResultRegistry = registry
    }

    private fun active(microphone: Boolean, background: Boolean) = StudyUiState.Active(
        deckName = "Deck",
        session = SessionState(),
        activePageCount = 2,
        runsInBackground = background,
        needsMicrophone = microphone,
        endedEarly = false
    )

    @Test
    fun `denied permissions are not requested again after restoration`() {
        shadowOf(ApplicationProvider.getApplicationContext<Application>()).denyPermissions(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS
        )
        val state = mutableStateOf(active(microphone = true, background = false))
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                SessionPermissions(state.value) {}
            }
        }
        compose.waitForIdle()
        assertEquals(listOf(Manifest.permission.RECORD_AUDIO), requested)
        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        assertEquals(1, requested.size)
        state.value = active(microphone = false, background = true)
        compose.waitForIdle()
        assertEquals(listOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS), requested)
        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        assertEquals(2, requested.size)
    }

    @Test
    fun `opening a new session allows a new permission attempt`() {
        shadowOf(
            ApplicationProvider.getApplicationContext<Application>()
        ).denyPermissions(Manifest.permission.RECORD_AUDIO)
        val entry = mutableStateOf(0)
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                key(entry.value) { SessionPermissions(active(microphone = true, background = false)) {} }
            }
        }
        compose.waitForIdle()
        assertEquals(1, requested.size)
        entry.value++
        compose.waitForIdle()
        assertEquals(2, requested.size)
    }
}
