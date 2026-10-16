package com.raycoll.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.raycoll.app.R

/**
 * Wspólny układ ekranu: pasek tytułu (z powrotem, gdy [onBack] != null), FAB i komunikaty.
 * [collapsingTitle] — duży tytuł, który przy przewijaniu treści zwija się do zwykłego paska;
 * false dla ekranów, które potrzebują całej wysokości (nauka). [closeInsteadOfBack] — X zamiast strzałki
 * dla edytorów na cały ekran (jak w aplikacjach Google: X porzuca, „Zapisz” po prawej zatwierdza).
 * [containerColor] — tło ekranu i paska; ekrany ustawień używają [settingsBackground].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    collapsingTitle: Boolean = true,
    closeInsteadOfBack: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable (PaddingValues) -> Unit
) {
    val scrollBehavior = if (collapsingTitle) TopAppBarDefaults.exitUntilCollapsedScrollBehavior() else null
    val barColors = TopAppBarDefaults.topAppBarColors(containerColor = containerColor)
    Box(modifier.fillMaxSize().background(containerColor), contentAlignment = Alignment.TopCenter) {
        Scaffold(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize()
                .then(
                    if (scrollBehavior !=
                        null
                    ) {
                        Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
                    } else {
                        Modifier
                    }
                ),
            topBar = {
                val titleContent = @Composable {
                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val navigationIcon = @Composable {
                    if (onBack != null) {
                        IconButton(shapes = IconButtonDefaults.shapes(), onClick = onBack) {
                            Icon(
                                painterResource(
                                    if (closeInsteadOfBack) R.drawable.ic_close else R.drawable.ic_arrow_back
                                ),
                                contentDescription = stringResource(
                                    if (closeInsteadOfBack) R.string.btn_close else R.string.btn_back
                                )
                            )
                        }
                    }
                }
                if (scrollBehavior != null) {
                    MediumFlexibleTopAppBar(
                        title = titleContent,
                        navigationIcon = navigationIcon,
                        actions = actions,
                        colors = barColors,
                        scrollBehavior = scrollBehavior
                    )
                } else {
                    TopAppBar(
                        title = titleContent,
                        navigationIcon = navigationIcon,
                        actions = actions,
                        colors = barColors
                    )
                }
            },
            containerColor = containerColor,
            floatingActionButton = floatingActionButton,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            content = content
        )
    }
}
