package com.raycoll.app.ui.navigation

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Wynik ekranu dla pulpitu (import, archiwizacja talii). */
@Serializable
sealed interface ScreenResult {
    @Serializable data object Imported : ScreenResult

    @Serializable data class Archived(val deckId: String) : ScreenResult
}

internal fun encodeScreenResult(value: ScreenResult?): String? =
    value?.let { Json.encodeToString(ScreenResult.serializer(), it) }

internal fun decodeScreenResult(value: String?): ScreenResult? = value?.let {
    runCatching { Json.decodeFromString(ScreenResult.serializer(), it) }.getOrNull()
}

private const val EXTRA_SCREEN_RESULT = "screenResult"

/** Dane zamykanego ekranu dla [Activity.setResult]. */
internal fun screenResultIntent(result: ScreenResult?): Intent =
    Intent().putExtra(EXTRA_SCREEN_RESULT, encodeScreenResult(result))

/** Otwiera ekran (osobną Activity) zwracający [ScreenResult]; [intentFor] buduje Intent z argumentu ekranu. */
internal open class OpenForResult<I>(private val intentFor: (Context, I) -> Intent) :
    ActivityResultContract<I, ScreenResult?>() {
    override fun createIntent(context: Context, input: I): Intent = intentFor(context, input)

    override fun parseResult(resultCode: Int, intent: Intent?): ScreenResult? =
        if (resultCode == Activity.RESULT_OK) decodeScreenResult(intent?.getStringExtra(EXTRA_SCREEN_RESULT)) else null
}
