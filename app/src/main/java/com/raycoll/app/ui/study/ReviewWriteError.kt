package com.raycoll.app.ui.study

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.raycoll.app.R
import com.raycoll.app.ui.theme.Spacing

/** Błąd dysku pozostaje widoczny i możliwy do ponowienia, także po powrocie z tła. */
@Composable
internal fun ReviewWriteError(onRetry: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.s)
                .semantics { liveRegion = LiveRegionMode.Polite }
        ) {
            Text(stringResource(R.string.study_review_save_failed), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onRetry) { Text(stringResource(R.string.errors_retry)) }
        }
    }
}
