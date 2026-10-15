package com.raycoll.app.ui.components

import androidx.compose.material3.FilterChip
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics

/** Pojedynczy wybór w grupie, z tą samą rolą w filtrach i ustawieniach. */
@Composable
fun ChoiceChip(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit, modifier: Modifier = Modifier) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        modifier = modifier.semantics { role = Role.RadioButton }
    )
}
