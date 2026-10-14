package com.raycoll.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.raycoll.app.R
import com.raycoll.app.domain.model.StudyMode
import com.raycoll.app.ui.components.displayName
import com.raycoll.app.ui.theme.Spacing

/** "Ucz się" + rozwijany wybór trybu nauki talii. */
@Composable
fun StudySplitButton(
    modes: List<StudyMode>,
    activeModeId: String,
    onStudy: () -> Unit,
    onSelectMode: (modeId: String) -> Unit,
    onManageModes: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = modifier
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(role = Role.Button, onClick = onStudy)
                    .padding(horizontal = Spacing.s),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(
                    stringResource(R.string.route_study),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = Spacing.xs)
                )
            }
            Box(
                Modifier
                    .height(20.dp)
                    .width(1.dp)
                    .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.28f))
            )
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(min = 40.dp)
                    .clickable(
                        enabled = modes.isNotEmpty(),
                        role = Role.Button,
                        onClickLabel = stringResource(R.string.study_modes_title),
                        onClick = { menuExpanded = true }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.ArrowDropDown, contentDescription = stringResource(R.string.study_modes_title))
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
    }
}

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
                onClick = {
                    onDismiss()
                    onSelectMode(mode.id)
                }
            )
        }
        HorizontalDivider(Modifier.padding(vertical = Spacing.xs))
        DropdownMenuItem(
            text = { Text(stringResource(R.string.study_modes_manage)) },
            leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
            onClick = {
                onDismiss()
                onManageModes()
            }
        )
    }
}
