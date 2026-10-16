package com.raycoll.app.ui.study

import android.content.Context
import android.content.Intent
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import com.raycoll.app.ui.main.AppActivity
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.withCreationCallback

@AndroidEntryPoint
class StudyActivity : AppActivity() {
    private val study: StudyViewModel by viewModels(
        extrasProducer = {
            defaultViewModelCreationExtras.withCreationCallback<StudyViewModel.Factory> {
                it.create(requireNotNull(intent.getStringExtra(EXTRA_DECK_ID)))
            }
        }
    )

    override fun isContentLoading() = study.uiState.value is StudyUiState.Loading

    @Composable
    override fun Screen() {
        StudyScreen(onExit = ::finish, viewModel = study)
    }

    companion object {
        private const val EXTRA_DECK_ID = "deckId"

        fun intent(context: Context, deckId: String): Intent =
            Intent(context, StudyActivity::class.java).putExtra(EXTRA_DECK_ID, deckId)
    }
}
