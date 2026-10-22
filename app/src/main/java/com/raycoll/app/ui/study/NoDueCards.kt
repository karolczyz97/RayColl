package com.raycoll.app.ui.study

import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.raycoll.app.R
import com.raycoll.app.ui.components.EmptyState

@Composable
fun NoDueCards(onExit: () -> Unit, modifier: Modifier = Modifier) {
    EmptyState(
        message = stringResource(R.string.study_no_due),
        modifier = modifier,
        action = {
            Button(onClick = onExit, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.study_back_to_panel))
            }
        }
    )
}
