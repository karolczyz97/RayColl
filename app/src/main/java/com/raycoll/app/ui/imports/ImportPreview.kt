package com.raycoll.app.ui.imports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.raycoll.app.R
import com.raycoll.app.ui.components.SectionCard
import com.raycoll.app.ui.theme.Spacing

@Composable
internal fun ColumnHeaders(pageNames: List<String>, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        pageNames.forEachIndexed { index, name ->
            Text(
                name.ifBlank { stringResource(R.string.import_page_label, index + 1) },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = Spacing.l).semantics { heading() }
            )
        }
        Spacer(Modifier.width(48.dp))
    }
}

@Composable
internal fun PreviewRow(
    rowNumber: Int,
    cells: List<String>,
    pageNames: List<String>,
    enabled: Boolean,
    onCellChange: (Int, String) -> Unit,
    onDelete: () -> Unit
) {
    val vertical = cells.size > 2
    val field: @Composable (Int, Modifier) -> Unit = { column, modifier ->
        val name = pageNames.getOrNull(column).orEmpty().ifBlank {
            stringResource(R.string.import_page_label, column + 1)
        }
        OutlinedTextField(
            value = cells[column],
            onValueChange = { onCellChange(column, it) },
            label = { Text(stringResource(R.string.import_cell_label, rowNumber, name)) },
            enabled = enabled,
            singleLine = !vertical,
            maxLines = if (vertical) 3 else 1,
            modifier = modifier
        )
    }
    val delete: @Composable () -> Unit = {
        IconButton(shapes = IconButtonDefaults.shapes(), onClick = onDelete, enabled = enabled) {
            Icon(
                painterResource(R.drawable.ic_delete),
                contentDescription = stringResource(
                    R.string.a11y_item_action,
                    stringResource(R.string.btn_delete),
                    rowNumber
                ),
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
    if (vertical) {
        SectionCard(title = stringResource(R.string.import_row_title, rowNumber)) {
            cells.indices.forEach { field(it, Modifier.fillMaxWidth()) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { delete() }
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            cells.indices.forEach { field(it, Modifier.weight(1f)) }
            delete()
        }
    }
}
