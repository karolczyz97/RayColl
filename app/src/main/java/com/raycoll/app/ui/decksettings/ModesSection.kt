package com.raycoll.app.ui.decksettings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.raycoll.app.R
import com.raycoll.app.domain.model.StudyMode
import com.raycoll.app.ui.components.ModeActions
import com.raycoll.app.ui.components.ModeBadges
import com.raycoll.app.ui.components.SectionCard
import com.raycoll.app.ui.components.displayName
import com.raycoll.app.ui.theme.Spacing

@Composable
internal fun ModesSection(
    activeModeId: String,
    modes: List<StudyMode>,
    onSelect: (String) -> Unit,
    onEdit: (String?) -> Unit,
    onReset: (StudyMode) -> Unit,
    onDelete: (StudyMode) -> Unit
) {
    SectionCard(spacing = Spacing.s) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.study_modes_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() }
            )
            TextButton(shapes = ButtonDefaults.shapes(), onClick = {
                onEdit(null)
            }, contentPadding = ButtonDefaults.TextButtonWithIconContentPadding) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Text(
                    stringResource(R.string.mode_list_new),
                    modifier = Modifier.padding(start = ButtonDefaults.IconSpacing)
                )
            }
        }
        Text(
            stringResource(R.string.study_modes_shared_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        modes.forEach { mode ->
            val active = activeModeId == mode.id
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(
                        color = if (active) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        } else {
                            Color.Transparent
                        },
                        shape = MaterialTheme.shapes.small
                    )
                    .padding(horizontal = Spacing.s, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // wybór trybu obejmuje cały wiersz z nazwą; przyciski akcji zostają osobnymi elementami
                Row(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .selectable(selected = active, role = Role.RadioButton, onClick = { onSelect(mode.id) }),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s)
                ) {
                    RadioButton(selected = active, onClick = null)
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                        Text(mode.displayName(), style = MaterialTheme.typography.bodyLarge)
                        ModeBadges(mode)
                    }
                }
                ModeActions(
                    mode,
                    onEdit = { onEdit(mode.id) },
                    onReset = { onReset(mode) },
                    onDelete = { onDelete(mode) }
                )
            }
        }
    }
}
