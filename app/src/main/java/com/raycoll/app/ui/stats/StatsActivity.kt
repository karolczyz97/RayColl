package com.raycoll.app.ui.stats

import android.content.Context
import android.content.Intent
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import com.raycoll.app.ui.browse.BrowseActivity
import com.raycoll.app.ui.main.AppActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class StatsActivity : AppActivity() {
    private val stats: StatsViewModel by viewModels()

    override fun isContentLoading() = stats.uiState.value is StatsUiState.Loading

    @Composable
    override fun Screen() {
        StatsScreen(
            onBack = ::finish,
            onBrowse = { openScreen(BrowseActivity.intent(this, it)) },
            viewModel = stats
        )
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, StatsActivity::class.java)
    }
}
