package com.raycoll.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import java.util.Locale

/** Języki stron talii (synteza i rozpoznawanie mowy). */
private val PAGE_LANGUAGES = listOf("en-US", "pl-PL", "de-DE", "es-ES", "it-IT", "fr-FR")

/** Język strony: skrót na przycisku, pełne nazwy na liście (wybrany oznaczony ptaszkiem). */
@Composable
fun LanguagePicker(language: String, enabled: Boolean = true, onSelect: (String) -> Unit) {
    DropdownChip(
        label = language.substringBefore('-').uppercase(Locale.ROOT),
        options = PAGE_LANGUAGES,
        selected = language,
        enabled = enabled,
        optionLabel = { Locale.forLanguageTag(it).displayName },
        modifier = Modifier.semantics { contentDescription = Locale.forLanguageTag(language).displayName },
        onSelect = onSelect
    )
}
