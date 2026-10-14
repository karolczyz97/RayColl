package com.raycoll.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
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
import java.util.Locale

/** Języki stron talii (synteza i rozpoznawanie mowy). */
private val PAGE_LANGUAGES = listOf("en-US", "pl-PL", "de-DE", "es-ES", "it-IT", "fr-FR")

/** Język strony: skrót na przycisku, pełne nazwy na liście. */
@Composable
fun LanguagePicker(language: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        AssistChip(
            onClick = { expanded = true },
            label = { Text(language.substringBefore('-').uppercase(Locale.ROOT)) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) }
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            PAGE_LANGUAGES.forEach { option ->
                DropdownMenuItem(
                    text = { Text(Locale.forLanguageTag(option).displayName) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    }
                )
            }
        }
    }
}
