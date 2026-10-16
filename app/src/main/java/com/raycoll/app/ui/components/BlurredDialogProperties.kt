package com.raycoll.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.isCrossWindowBlurEnabled
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

private val BlurBehindRadius = 12.dp

// przy rozmyciu wystarczy lżejsze przyciemnienie; bez niego (oszczędzanie baterii, słabszy GPU)
// zostaje zwykłe przyciemnienie z motywu
private const val BLURRED_SCRIM_ALPHA = 0.3f

/** Okno dialogowe z rozmytym ekranem pod spodem — ten sam efekt w każdym oknie aplikacji. */
@Composable
fun blurredDialogProperties(
    dismissOnBackPress: Boolean = true,
    dismissOnClickOutside: Boolean = true
): DialogProperties {
    val blurEnabled = LocalWindowInfo.current.isCrossWindowBlurEnabled
    return DialogProperties(
        dismissOnBackPress = dismissOnBackPress,
        dismissOnClickOutside = dismissOnClickOutside,
        blurBehindRadius = BlurBehindRadius,
        scrimAlpha = if (blurEnabled) BLURRED_SCRIM_ALPHA else Float.NaN
    )
}
