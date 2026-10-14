package com.raycoll.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.util.Locale

/** Języki stron talii (synteza i rozpoznawanie mowy). */
private val PAGE_LANGUAGES = listOf("en-US", "pl-PL", "de-DE", "es-ES", "it-IT", "fr-FR")

/** Język strony: skrót na przycisku, pełne nazwy na liście (wybrany oznaczony ptaszkiem). */
@Composable
fun LanguagePicker(language: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        AssistChip(
            onClick = { expanded = true },
            label = { Text(language.substringBefore('-').uppercase(Locale.ROOT)) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            modifier = Modifier.semantics { contentDescription = Locale.forLanguageTag(language).displayName }
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            PAGE_LANGUAGES.forEach { option ->
                val current = option == language
                DropdownMenuItem(
                    text = { Text(Locale.forLanguageTag(option).displayName) },
                    leadingIcon = {
                        if (current) {
                            Icon(
                                Icons.Filled.Check,
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
                    modifier = Modifier.semantics { selected = current }
                )
            }
        }
    }
}
