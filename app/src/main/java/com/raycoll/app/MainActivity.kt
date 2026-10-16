package com.raycoll.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.core.graphics.drawable.toDrawable
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raycoll.app.data.account.AccountState
import com.raycoll.app.ui.components.ExpressiveLoadingIndicator
import com.raycoll.app.ui.components.MigrationDialog
import com.raycoll.app.ui.components.RejectedChangesMessage
import com.raycoll.app.ui.main.MainViewModel
import com.raycoll.app.ui.navigation.RayCollNavHost
import com.raycoll.app.ui.theme.RayCollTheme
import com.raycoll.app.ui.theme.resolveDarkMode
import dagger.hilt.android.AndroidEntryPoint

// takie same półprzezroczyste tło paska nawigacji jak w domyślnym enableEdgeToEdge()
private val LightNavigationScrim = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val DarkNavigationScrim = Color.argb(0x80, 0x1b, 0x1b, 0x1b)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition { viewModel.preferences.value == null }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val preferences by viewModel.preferences.collectAsStateWithLifecycle()
            val waitingForData by viewModel.waitingForData.collectAsStateWithLifecycle()
            val offline by viewModel.offline.collectAsStateWithLifecycle()
            val accountState by viewModel.accountState.collectAsStateWithLifecycle()
            val migrationRequired by viewModel.migrationRequired.collectAsStateWithLifecycle()
            val migrating by viewModel.migrating.collectAsStateWithLifecycle()
            val migrationFailed by viewModel.migrationFailed.collectAsStateWithLifecycle()
            val prefs = preferences ?: return@setContent
            // motyw wybrany w aplikacji może różnić się od systemowego — ikony pasków muszą iść za aplikacją
            val isDark = prefs.theme.resolveDarkMode(isSystemInDarkTheme())
            DisposableEffect(isDark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { isDark },
                    navigationBarStyle = SystemBarStyle.auto(LightNavigationScrim, DarkNavigationScrim) { isDark }
                )
                onDispose {}
            }
            RayCollTheme(theme = prefs.theme, useSystemColors = prefs.useSystemColors) {
                // tło okna za Compose (obrót, klawiatura, przewijanie ponad krawędź) w kolorze motywu aplikacji
                val windowBackground = MaterialTheme.colorScheme.background.toArgb()
                DisposableEffect(windowBackground) {
                    window.setBackgroundDrawable(windowBackground.toDrawable())
                    onDispose {}
                }
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.fillMaxSize()) {
                        when (val account = accountState) {
                            AccountState.Starting -> Column(
                                Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                ExpressiveLoadingIndicator()
                            }

                            AccountState.NeedsInternet -> Column(
                                Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(stringResource(R.string.auth_start_internet))
                                Button(shapes = ButtonDefaults.shapes(), onClick = viewModel::retryStart) {
                                    Text(stringResource(R.string.auth_retry_start))
                                }
                            }

                            is AccountState.Ready -> if (!account.isGuest && waitingForData) {
                                Column(
                                    Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        stringResource(
                                            if (offline) {
                                                R.string.auth_data_when_online
                                            } else {
                                                R.string.auth_loading_data
                                            }
                                        )
                                    )
                                    if (!offline) ExpressiveLoadingIndicator()
                                }
                            } else {
                                RayCollNavHost()
                            }
                        }
                        // poza Scaffoldem: wcięcie od paska nawigacji systemu ręcznie
                        RejectedChangesMessage(
                            viewModel.store,
                            Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                        )
                    }
                    if (migrationRequired) {
                        MigrationDialog(
                            busy = migrating,
                            failed = migrationFailed,
                            onChoose = viewModel::resolveMigration
                        )
                    }
                }
            }
        }
    }
}
