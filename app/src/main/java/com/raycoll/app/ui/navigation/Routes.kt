package com.raycoll.app.ui.navigation

import kotlinx.serialization.Serializable

@Serializable data object DashboardRoute

@Serializable data class StudyRoute(val deckId: String)

@Serializable data class BrowseRoute(val deckId: String)

@Serializable data class DeckSettingsRoute(val deckId: String)

/** [modeId] == null: nowy tryb. */
@Serializable data class ModeDetailRoute(val modeId: String? = null)

@Serializable data object ImportRoute

@Serializable data object StatsRoute

@Serializable data object ArchiveRoute

@Serializable data object AppSettingsRoute

@Serializable data object StudyModesRoute
