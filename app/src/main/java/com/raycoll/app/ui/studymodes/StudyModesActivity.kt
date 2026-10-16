package com.raycoll.app.ui.studymodes

import android.content.Context
import android.content.Intent
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import com.raycoll.app.ui.main.AppActivity
import com.raycoll.app.ui.modedetail.ModeDetailActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class StudyModesActivity : AppActivity() {
    private val studyModes: StudyModesViewModel by viewModels()

    override fun isContentLoading() = studyModes.uiState.value is StudyModesUiState.Loading

    @Composable
    override fun Screen() {
        StudyModesScreen(
            onBack = ::finish,
            onEditMode = { openScreen(ModeDetailActivity.intent(this, it)) },
            onNewMode = { openScreen(ModeDetailActivity.intent(this, null)) },
            viewModel = studyModes
        )
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, StudyModesActivity::class.java)
    }
}
