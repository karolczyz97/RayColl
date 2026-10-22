package com.raycoll.app.ui.archive

import android.content.Context
import android.content.Intent
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import com.raycoll.app.ui.browse.BrowseActivity
import com.raycoll.app.ui.main.AppActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ArchiveActivity : AppActivity() {
    private val archive: ArchiveViewModel by viewModels()

    override fun isContentLoading() = archive.uiState.value is ArchiveUiState.Loading

    @Composable
    override fun Screen() {
        ArchiveScreen(
            onBack = ::finish,
            onBrowse = { openScreen(BrowseActivity.intent(this, it)) },
            viewModel = archive
        )
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, ArchiveActivity::class.java)
    }
}
