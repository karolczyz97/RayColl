package com.raycoll.app.ui.browse

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import com.raycoll.app.ui.main.AppActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class BrowseActivity : AppActivity() {
    private val browse: BrowseViewModel by assistedViewModel<BrowseViewModel, BrowseViewModel.Factory> {
        it.create(requireNotNull(intent.getStringExtra(EXTRA_DECK_ID)))
    }

    override fun isContentLoading() = browse.uiState.value is BrowseUiState.Loading

    @Composable
    override fun Screen() {
        BrowseScreen(onBack = ::finish, viewModel = browse)
    }

    companion object {
        private const val EXTRA_DECK_ID = "deckId"

        fun intent(context: Context, deckId: String): Intent =
            Intent(context, BrowseActivity::class.java).putExtra(EXTRA_DECK_ID, deckId)
    }
}
