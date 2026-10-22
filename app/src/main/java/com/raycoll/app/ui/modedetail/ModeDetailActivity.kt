package com.raycoll.app.ui.modedetail

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import com.raycoll.app.ui.main.AppActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ModeDetailActivity : AppActivity() {
    private val modeDetail: ModeDetailViewModel by assistedViewModel<ModeDetailViewModel, ModeDetailViewModel.Factory> {
        it.create(intent.getStringExtra(EXTRA_MODE_ID))
    }

    override fun isContentLoading() = modeDetail.uiState.value is ModeDetailUiState.Loading

    @Composable
    override fun Screen() {
        ModeDetailScreen(onBack = ::finish, viewModel = modeDetail)
    }

    companion object {
        private const val EXTRA_MODE_ID = "modeId"

        /** [modeId] == null: nowy tryb. */
        fun intent(context: Context, modeId: String?): Intent =
            Intent(context, ModeDetailActivity::class.java).putExtra(EXTRA_MODE_ID, modeId)
    }
}
