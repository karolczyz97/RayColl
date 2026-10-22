package com.raycoll.app.ui.decksettings

import android.content.Intent
import androidx.compose.runtime.Composable
import com.raycoll.app.ui.main.AppActivity
import com.raycoll.app.ui.modedetail.ModeDetailActivity
import com.raycoll.app.ui.navigation.OpenForResult
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class DeckSettingsActivity : AppActivity() {
    private val deckSettings: DeckSettingsViewModel by
        assistedViewModel<DeckSettingsViewModel, DeckSettingsViewModel.Factory> {
            it.create(requireNotNull(intent.getStringExtra(EXTRA_DECK_ID)))
        }

    override fun isContentLoading() = deckSettings.uiState.value is DeckSettingsUiState.Loading

    @Composable
    override fun Screen() {
        DeckSettingsScreen(
            onBack = ::finish,
            onClosed = ::finishWith,
            onEditMode = { openScreen(ModeDetailActivity.intent(this, it)) },
            viewModel = deckSettings
        )
    }

    /** Ustawienia talii o podanym id; wynik: archiwizacja talii. */
    internal object Open : OpenForResult<String>({ context, deckId ->
        Intent(context, DeckSettingsActivity::class.java).putExtra(EXTRA_DECK_ID, deckId)
    })

    private companion object {
        const val EXTRA_DECK_ID = "deckId"
    }
}
