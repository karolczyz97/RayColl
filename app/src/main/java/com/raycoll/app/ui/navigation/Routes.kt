package com.raycoll.app.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object DashboardRoute : NavKey

@Serializable data class StudyRoute(val deckId: String) : NavKey

@Serializable data class BrowseRoute(val deckId: String) : NavKey

@Serializable data class DeckSettingsRoute(val deckId: String) : NavKey

/** [modeId] == null: nowy tryb. */
@Serializable data class ModeDetailRoute(val modeId: String? = null) : NavKey

@Serializable data object ImportRoute : NavKey

@Serializable data object StatsRoute : NavKey

@Serializable data object ArchiveRoute : NavKey

@Serializable data object AppSettingsRoute : NavKey

@Serializable data object StudyModesRoute : NavKey
