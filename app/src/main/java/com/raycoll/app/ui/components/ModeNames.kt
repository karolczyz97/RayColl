package com.raycoll.app.ui.components

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

@Composable
fun StudyMode.displayName(): String = modeDisplayName(name, BuiltInMode.fromId(builtInSourceId)?.takeIf { isBuiltIn })

@Composable
private fun builtInModeName(builtIn: BuiltInMode): String = stringResource(
    when (builtIn) {
        BuiltInMode.CLASSIC -> R.string.mode_classic_name
        BuiltInMode.LISTEN_SPEAK -> R.string.mode_listen_speak_name
    }
)
