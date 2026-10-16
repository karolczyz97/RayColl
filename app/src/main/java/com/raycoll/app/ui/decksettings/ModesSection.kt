package com.raycoll.app.ui.decksettings

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.raycoll.app.R
import com.raycoll.app.domain.model.StudyMode
import com.raycoll.app.ui.components.ModeActions
import com.raycoll.app.ui.components.ModeBadges
import com.raycoll.app.ui.components.SettingsAction
import com.raycoll.app.ui.components.SettingsBlock
import com.raycoll.app.ui.components.SettingsGroup
import com.raycoll.app.ui.components.SettingsRadio
import com.raycoll.app.ui.components.displayName

@Composable
internal fun ModesSection(
    activeModeId: String,
    modes: List<StudyMode>,
    onSelect: (String) -> Unit,
    onEdit: (String?) -> Unit,
    onReset: (StudyMode) -> Unit,
    onDelete: (StudyMode) -> Unit
) {
    SettingsGroup(stringResource(R.string.study_modes_title)) {
        item { shapes ->
            SettingsBlock(shapes) {
                Text(
                    stringResource(R.string.study_modes_shared_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        modes.forEach { mode ->
            item { shapes ->
                SettingsRadio(
                    shapes = shapes,
                    selected = activeModeId == mode.id,
                    onClick = { onSelect(mode.id) },
                    supportingContent = { ModeBadges(mode) },
                    trailingContent = {
                        ModeActions(
                            mode,
                            onEdit = { onEdit(mode.id) },
                            onReset = { onReset(mode) },
                            onDelete = { onDelete(mode) }
                        )
                    }
                ) { Text(mode.displayName()) }
            }
        }
        item { shapes ->
            SettingsAction(
                shapes = shapes,
                title = stringResource(R.string.mode_list_new),
                icon = painterResource(R.drawable.ic_add),
                onClick = { onEdit(null) }
            )
        }
    }
}
