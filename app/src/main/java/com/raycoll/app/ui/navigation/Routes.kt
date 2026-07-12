package com.raycoll.app.ui.navigation

import kotlinx.serialization.Serializable

@Serializable object DashboardRoute

@Serializable data class StudyRoute(val deckId: String)

@Serializable data class BrowseRoute(val deckId: String)

@Serializable data class DeckSettingsRoute(val deckId: String)

@Serializable data class ModeDetailRoute(val modeId: String? = null)

@Serializable object ImportRoute

@Serializable object StatsRoute

@Serializable object ArchiveRoute

@Serializable object AppSettingsRoute
