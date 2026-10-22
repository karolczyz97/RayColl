package com.raycoll.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.raycoll.app.R
import com.raycoll.app.domain.model.StudyMode
import com.raycoll.app.ui.theme.Spacing

private const val ARROW_OPEN_DEGREES = 180f

/**
 * Przycisk dzielony: ▶ zaczyna naukę, ▾ rozwija wybór trybu nauki talii. Przy otwartym menu prawa
 * część zmienia się w kółko, a strzałka się obraca (Material 3 Expressive).
 */
@Composable
fun StudySplitButton(
    modes: List<StudyMode>,
    activeModeId: String,
    onStudy: () -> Unit,
    onSelectMode: (modeId: String) -> Unit,
    onManageModes: () -> Unit,
    hasDue: Boolean,
    deckName: String,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    // bez kart do powtórki przycisk nie wyróżnia się jako główne działanie (nadal działa — ekran nauki wyjaśni dlaczego pusto)
    val buttonColors = if (hasDue) {
        ButtonDefaults.buttonColors()
    } else {
        ButtonDefaults.buttonColors(
            containerColor = colors.secondaryContainer,
            contentColor = colors.onSecondaryContainer
        )
    }
    val studyLabel = actionLabel(stringResource(R.string.route_study), deckName)
    val modesLabel = actionLabel(stringResource(R.string.study_modes_title), deckName)
    val arrowRotation by animateFloatAsState(
        targetValue = if (menuExpanded) ARROW_OPEN_DEGREES else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "mode_menu_arrow"
    )
    SplitButtonLayout(
        modifier = modifier,
        leadingButton = {
            SplitButtonDefaults.LeadingButton(
                onClick = onStudy,
                colors = buttonColors,
                modifier = Modifier.semantics { contentDescription = studyLabel }
            ) {
                Icon(
                    painterResource(R.drawable.ic_play_arrow_filled),
                    contentDescription = null,
                    modifier = Modifier.size(SplitButtonDefaults.LeadingIconSize)
                )
            }
        },
        trailingButton = {
            SplitButtonDefaults.TrailingButton(
                checked = menuExpanded,
                onCheckedChange = { menuExpanded = it },
                enabled = modes.isNotEmpty(),
                colors = buttonColors,
                modifier = Modifier.semantics { contentDescription = modesLabel }
            ) {
                Icon(
                    painterResource(R.drawable.ic_keyboard_arrow_down),
                    contentDescription = null,
                    modifier = Modifier
                        .size(SplitButtonDefaults.TrailingIconSize)
                        .graphicsLayer { rotationZ = arrowRotation }
                )
                ModeMenu(
                    expanded = menuExpanded,
                    modes = modes,
                    activeModeId = activeModeId,
                    onDismiss = { menuExpanded = false },
                    onSelectMode = onSelectMode,
                    onManageModes = onManageModes
                )
            }
        }
    )
}

@Composable
private fun actionLabel(action: String, deckName: String): String =
    stringResource(R.string.a11y_item_action, action, deckName)

@Composable
private fun ModeMenu(
    expanded: Boolean,
    modes: List<StudyMode>,
    activeModeId: String,
    onDismiss: () -> Unit,
    onSelectMode: (String) -> Unit,
    onManageModes: () -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        modes.forEach { mode ->
            DropdownMenuItem(
                text = { Text(mode.displayName()) },
                leadingIcon = { RadioButton(selected = mode.id == activeModeId, onClick = null) },
                modifier = Modifier.semantics {
                    selected = mode.id == activeModeId
                    role = Role.RadioButton
                },
                onClick = {
                    onDismiss()
                    onSelectMode(mode.id)
                }
            )
        }
        HorizontalDivider(Modifier.padding(vertical = Spacing.xs))
        DropdownMenuItem(
            text = { Text(stringResource(R.string.study_modes_manage)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_settings), contentDescription = null) },
            onClick = {
                onDismiss()
                onManageModes()
            }
        )
    }
}
