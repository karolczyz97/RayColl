package com.raycoll.app.ui.components

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.raycoll.app.R
import com.raycoll.app.domain.model.StudyMode
import com.raycoll.app.domain.seed.BuiltInMode

/**
 * Nazwa trybu do pokazania. Wbudowany tryb z niezmienioną (kanoniczną) nazwą dostaje
 * tłumaczenie w języku aplikacji; nazwa nadana przez użytkownika zostaje bez zmian.
 */
@Composable
fun modeDisplayName(name: String, builtIn: BuiltInMode?): String =
    if (builtIn == null || name != builtIn.defaultName) name else builtInModeName(builtIn)

/** Nazwa do zapisu: wpisane tłumaczenie nazwy wbudowanej zapisuje się jako nazwa kanoniczna. */
fun canonicalModeName(input: String, builtIn: BuiltInMode?, translatedName: String): String =
    if (builtIn != null && input == translatedName) builtIn.defaultName else input

@Composable
fun StudyMode.displayName(): String = modeDisplayName(name, BuiltInMode.fromId(builtInSourceId)?.takeIf { isBuiltIn })

@Composable
private fun builtInModeName(builtIn: BuiltInMode): String = stringResource(builtIn.nameRes())

/** Jednozdaniowy opis działania wbudowanego trybu; tryby własne opisu nie mają. */
@Composable
fun StudyMode.builtInDescription(): String? {
    val builtIn = BuiltInMode.fromId(builtInSourceId)?.takeIf { isBuiltIn } ?: return null
    return stringResource(builtIn.descriptionRes())
}

@StringRes
fun BuiltInMode.nameRes(): Int = when (this) {
    BuiltInMode.CLASSIC -> R.string.mode_classic_name
    BuiltInMode.LISTEN_SPEAK -> R.string.mode_listen_speak_name
    BuiltInMode.LISTEN_ONLY -> R.string.mode_listen_only_name
    BuiltInMode.BROWSE -> R.string.mode_browse_name
    BuiltInMode.AUTO_REVEAL -> R.string.mode_auto_reveal_name
}

@StringRes
fun BuiltInMode.descriptionRes(): Int = when (this) {
    BuiltInMode.CLASSIC -> R.string.mode_classic_desc
    BuiltInMode.LISTEN_SPEAK -> R.string.mode_listen_speak_desc
    BuiltInMode.LISTEN_ONLY -> R.string.mode_listen_only_desc
    BuiltInMode.BROWSE -> R.string.mode_browse_desc
    BuiltInMode.AUTO_REVEAL -> R.string.mode_auto_reveal_desc
}
