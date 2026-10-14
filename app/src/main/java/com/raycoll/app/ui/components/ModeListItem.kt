package com.raycoll.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.raycoll.app.R
import com.raycoll.app.domain.model.StudyMode
import com.raycoll.app.domain.operations.isModifiedBuiltIn
import com.raycoll.app.ui.theme.Spacing

// Wspólne elementy listy trybów (ustawienia talii i ekran trybów): te same plakietki,
// ikony i opisy dla tych samych działań.

/** „Tryb wbudowany” i — gdy zmieniony względem wersji fabrycznej — „Zmodyfikowany”. */
@Composable
fun ModeBadges(mode: StudyMode, modifier: Modifier = Modifier) {
    if (!mode.isBuiltIn) return
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        ModeBadge(
            stringResource(R.string.mode_editor_builtin_badge),
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
        if (mode.isModifiedBuiltIn()) {
            ModeBadge(
                stringResource(R.string.study_modes_modified),
                MaterialTheme.colorScheme.tertiaryContainer,
                MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

/** Przywróć domyślne (tryb wbudowany) albo usuń (tryb własny) + edytuj. */
@Composable
fun ModeActions(mode: StudyMode, onEdit: () -> Unit, onReset: () -> Unit, onDelete: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (mode.isBuiltIn) {
            // niezmieniony wbudowany tryb nie ma czego przywracać
            IconButton(onClick = onReset, enabled = mode.isModifiedBuiltIn()) {
                Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.mode_list_reset))
            }
        } else {
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.DeleteOutline,
                    contentDescription = stringResource(R.string.btn_delete),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
        IconButton(onClick = onEdit) {
            Icon(
                Icons.Filled.Edit,
                contentDescription = stringResource(R.string.mode_list_edit),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun ModeBadge(text: String, container: Color, content: Color) {
    Surface(shape = MaterialTheme.shapes.extraSmall, color = container, contentColor = content) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xxs)
        )
    }
}
