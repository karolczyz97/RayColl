package com.raycoll.app.domain.seed

import com.raycoll.app.core.Ids
import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.domain.model.StudyMode
import com.raycoll.app.domain.model.createNewSrsState
import com.raycoll.app.domain.normalize.DEFAULT_CARD_ORDER
import com.raycoll.app.domain.normalize.DEFAULT_STUDY_FILTER
import com.raycoll.app.domain.srs.createSeedModes

object SeedData {
    fun createSeedGroups(): List<FlashcardGroup> {
        fun mk(pages: List<String>): Flashcard = Flashcard(
            id = Ids.newId(),
            pages = pages,
            srsState = createNewSrsState(),
            contentUpdatedAt = 0,
            srsUpdatedAt = 0,
        )
        return listOf(
            FlashcardGroup(
                id = Ids.newId(),
                name = "Angielski - Podstawy",
                activeModeId = "classic",
                studyFilter = DEFAULT_STUDY_FILTER,
                cardOrder = DEFAULT_CARD_ORDER,
                pageLanguages = listOf("en-US", "pl-PL"),
                pageNames = listOf("Phrase", "Tłumaczenie"),
                activePageCount = 2,
                updatedAt = 0,
                cards = listOf(
                    mk(listOf("Good morning", "Dzień dobry")),
                    mk(listOf("Thank you", "Dziękuję")),
                    mk(listOf("Please", "Proszę")),
                    mk(listOf("Goodbye", "Do widzenia")),
                    mk(listOf("Yes", "Tak")),
                ),
            ),
            FlashcardGroup(
                id = Ids.newId(),
                name = "Hiszpański - Podstawy",
                activeModeId = "classic",
                studyFilter = DEFAULT_STUDY_FILTER,
                cardOrder = DEFAULT_CARD_ORDER,
                pageLanguages = listOf("es-ES", "pl-PL"),
                pageNames = listOf("Palabra", "Tłumaczenie"),
                activePageCount = 2,
                updatedAt = 0,
                cards = listOf(
                    mk(listOf("Hola", "Cześć")),
                    mk(listOf("Gracias", "Dziękuję")),
                    mk(listOf("Por favor", "Proszę")),
                    mk(listOf("Adiós", "Do widzenia")),
                ),
            ),
        )
    }

    fun createSeedModes(): List<StudyMode> = com.raycoll.app.domain.srs.createSeedModes()

    fun createSeedData() = com.raycoll.app.domain.model.StoreData(
        groups = createSeedGroups(),
        studyModes = createSeedModes(),
        activityHeatmap = emptyMap(),
    )
}
