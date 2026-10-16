package com.raycoll.app.ui.imports

import android.content.Intent
import androidx.compose.runtime.Composable
import com.raycoll.app.ui.main.AppActivity
import com.raycoll.app.ui.navigation.OpenForResult
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ImportActivity : AppActivity() {
    @Composable
    override fun Screen() {
        ImportScreen(onFinished = ::finish, onCompleted = ::finishWith)
    }

    /** Import talii; wynik: zaimportowano. */
    internal object Open : OpenForResult<Unit>({ context, _ -> Intent(context, ImportActivity::class.java) })
}
