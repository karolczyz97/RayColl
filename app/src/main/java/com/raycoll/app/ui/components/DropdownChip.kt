package com.raycoll.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.raycoll.app.R

/** Przycisk z bieżącym wyborem [label], który rozwija listę [options]. */
@Composable
fun <T> DropdownChip(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onSelect: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        AssistChip(
            enabled = enabled,
            onClick = { expanded = true },
            label = { Text(label) },
            trailingIcon = { Icon(painterResource(R.drawable.ic_arrow_drop_down), contentDescription = null) },
            modifier = modifier
        )
        DropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                val current = option == selected
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    leadingIcon = {
                        if (current) {
                            Icon(
                                painterResource(R.drawable.ic_check),
                                contentDescription = null
                            )
                        } else {
                            Spacer(Modifier.size(24.dp))
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                    modifier = Modifier.semantics { this.selected = current }
                )
            }
        }
    }
}
