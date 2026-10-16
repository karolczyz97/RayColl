package com.raycoll.app.ui.appsettings

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import com.raycoll.app.ui.main.AppActivity
import com.raycoll.app.ui.studymodes.StudyModesActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AppSettingsActivity : AppActivity() {
    @Composable
    override fun Screen() {
        AppSettingsScreen(
            onBack = ::finish,
            onStudyModes = { openScreen(StudyModesActivity.intent(this)) }
        )
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, AppSettingsActivity::class.java)
    }
}
