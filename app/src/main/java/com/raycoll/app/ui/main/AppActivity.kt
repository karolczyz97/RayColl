package com.raycoll.app.ui.main

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.raycoll.app.ui.navigation.ScreenResult
import com.raycoll.app.ui.navigation.screenResultIntent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Najdłuższe czekanie na treść ekranu przed pierwszą klatką: system zaczyna animację otwarcia
 * od pierwszej klatki, więc dłuższe czekanie byłoby odczuwalnym opóźnieniem po dotknięciu.
 */
private const val MAX_CONTENT_WAIT_MS = 400L

/**
 * Każdy ekran to osobne okno (Activity), żeby wejście i cofanie (także gestem) animował system,
 * tak jak w aplikacjach Androida. Wspólna oprawa okna: [AppFrame].
 */
abstract class AppActivity : ComponentActivity() {
    protected val app: MainViewModel by viewModels()

    /** Ekran pokazuje dane konta, z którym go otwarto — po zalogowaniu na inne konto zamyka się. */
    protected open val closesOnAccountChange: Boolean = true

    private var opening = false

    /** Oprawa okna jest już ułożona (motyw z ustawień wczytany). */
    private var framed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val created = SystemClock.uptimeMillis()
        // pierwsza klatka z motywem z ustawień i, jeśli zdąży, z treścią zamiast samego wskaźnika ładowania
        holdFirstFrameUntil {
            framed && (!showsOnlyLoading() || SystemClock.uptimeMillis() - created >= MAX_CONTENT_WAIT_MS)
        }
        if (closesOnAccountChange) {
            lifecycleScope.launch {
                app.accountChanged.first { it }
                finish()
            }
        }
        setContent { AppFrame(app, onFramed = { framed = true }) { Screen() } }
    }

    @Composable
    protected abstract fun Screen()

    /**
     * Czy ekran ma jeszcze tylko wskaźnik ładowania zamiast treści — według stanu ViewModelu,
     * bo treść w Scaffoldzie układa się dopiero przy pomiarze, już po decyzji o klatce.
     */
    protected open fun isContentLoading(): Boolean = false

    override fun onResume() {
        super.onResume()
        opening = false
    }

    /** Otwiera ekran; kolejne dotknięcia, zanim się otworzy, nic nie robią. */
    protected fun openScreen(intent: Intent) {
        if (claimOpening()) startActivity(intent)
    }

    /** Otwiera ekran zwracający wynik; kolejne dotknięcia, zanim się otworzy, nic nie robią. */
    protected fun <I> openScreen(launcher: ActivityResultLauncher<I>, input: I) {
        if (claimOpening()) launcher.launch(input)
    }

    /** Zamyka ekran z wynikiem dla ekranu, który go otworzył. */
    protected fun finishWith(result: ScreenResult?) {
        setResult(RESULT_OK, screenResultIntent(result))
        finish()
    }

    private fun showsOnlyLoading(): Boolean = when (val gate = app.accountGate.value) {
        AccountGate.Open -> isContentLoading()
        else -> gate.showsLoading
    }

    private fun claimOpening(): Boolean {
        if (opening || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return false
        opening = true
        return true
    }

    /** [ready] musi się utrzymać przez dwie klatki: Compose pokazuje nowy stan ViewModelu klatkę później. */
    private fun holdFirstFrameUntil(ready: () -> Boolean) {
        val content = window.decorView
        content.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            private var readyFrames = 0

            override fun onPreDraw(): Boolean {
                readyFrames = if (ready()) readyFrames + 1 else 0
                if (readyFrames < 2) return false
                content.viewTreeObserver.removeOnPreDrawListener(this)
                return true
            }
        })
    }
}
