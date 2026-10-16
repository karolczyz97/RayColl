package com.raycoll.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
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
        BuiltInBadge()
        if (mode.isModifiedBuiltIn()) {
            Pill(
                stringResource(R.string.study_modes_modified),
                MaterialTheme.colorScheme.tertiaryContainer,
                MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

/** Plakietka „Tryb wbudowany” — ta sama na listach i w edytorze trybu. */
@Composable
fun BuiltInBadge() {
    Pill(
        stringResource(R.string.mode_editor_builtin_badge),
        MaterialTheme.colorScheme.secondaryContainer,
        MaterialTheme.colorScheme.onSecondaryContainer
    )
}

/** Przywróć domyślne (tryb wbudowany) albo usuń (tryb własny) + edytuj. */
@Composable
fun ModeActions(mode: StudyMode, onEdit: () -> Unit, onReset: () -> Unit, onDelete: () -> Unit) {
    // opis z nazwą trybu, żeby czytnik ekranu odróżniał przyciski w kolejnych wierszach
    val name = mode.displayName()
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (mode.isBuiltIn) {
            // niezmieniony wbudowany tryb nie ma czego przywracać
            IconButton(shapes = IconButtonDefaults.shapes(), onClick = onReset, enabled = mode.isModifiedBuiltIn()) {
                Icon(
                    painterResource(R.drawable.ic_restart_alt),
                    contentDescription = stringResource(
                        R.string.a11y_item_action,
                        stringResource(R.string.mode_list_reset),
                        name
                    )
                )
            }
        } else {
            IconButton(shapes = IconButtonDefaults.shapes(), onClick = onDelete) {
                Icon(
                    painterResource(R.drawable.ic_delete),
                    contentDescription = stringResource(
                        R.string.a11y_item_action,
                        stringResource(R.string.btn_delete),
                        name
                    ),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
        IconButton(shapes = IconButtonDefaults.shapes(), onClick = onEdit) {
            Icon(
                painterResource(R.drawable.ic_edit),
                contentDescription = stringResource(
                    R.string.a11y_item_action,
                    stringResource(R.string.mode_list_edit),
                    name
                ),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
