package com.raycoll.app.domain.seed

import com.raycoll.app.domain.model.*
import com.raycoll.app.domain.srs.createNewSrsState
import java.util.UUID

fun createSeedModes(): List<StudyMode> = listOf(
    StudyMode(
        id = "classic",
        name = "Klasyczny",
        isBuiltIn = true,
        builtInSourceId = "classic",
        updatedAt = 0L,
        steps = listOf(
            ShowPage(pageIndex = 0),
            SpeakPage(pageIndex = 0),
            WaitForTapToReveal(),
            ShowAllPages(),
            ShowRatings(),
        ),
    ),
    StudyMode(
        id = "listen-speak",
        name = "Audio",
        isBuiltIn = true,
        builtInSourceId = "listen-speak",
        updatedAt = 0L,
        steps = listOf(
            ShowPage(pageIndex = 0),
            SpeakPage(pageIndex = 0),
            ListenAndCheck(pageIndex = 1, successThreshold = 70),
            FeedbackSuccess(condition = StepCondition.correct),
            ShowAllPages(condition = StepCondition.correct),
            AutoRateFromAnswer(condition = StepCondition.correct),
            NextCard(condition = StepCondition.correct),
            FeedbackError(condition = StepCondition.wrong),
            ShowAllPages(condition = StepCondition.wrong),
            MarkFailed(condition = StepCondition.wrong),
            AutoRateFixed(rating = 1, condition = StepCondition.wrong),
            NextCard(condition = StepCondition.wrong),
            ShowAllPages(),
            ShowRatings(),
        ),
    ),
)

fun createSeedGroups(): List<FlashcardGroup> {
    fun mk(pages: List<String>): Flashcard = Flashcard(
        id = UUID.randomUUID().toString(),
        pages = pages,
        srsState = createNewSrsState(),
    )

    return listOf(
        FlashcardGroup(
            id = UUID.randomUUID().toString(),
            name = "Angielski - Podstawy",
            activeModeId = "classic",
            pageLanguages = listOf("en-US", "pl-PL"),
            pageNames = listOf("Phrase", "Tłumaczenie"),
            activePageCount = 2,
            cards = listOf(
                mk(listOf("Good morning", "Dzień dobry")),
                mk(listOf("Thank you", "Dziękuję")),
                mk(listOf("Please", "Proszę")),
                mk(listOf("Goodbye", "Do widzenia")),
                mk(listOf("Yes", "Tak")),
            ),
        ),
        FlashcardGroup(
            id = UUID.randomUUID().toString(),
            name = "Hiszpański - Podstawy",
            activeModeId = "classic",
            pageLanguages = listOf("es-ES", "pl-PL"),
            pageNames = listOf("Palabra", "Tłumaczenie"),
            activePageCount = 2,
            cards = listOf(
                mk(listOf("Hola", "Cześć")),
                mk(listOf("Gracias", "Dziękuję")),
                mk(listOf("Por favor", "Proszę")),
                mk(listOf("Adiós", "Do widzenia")),
            ),
        ),
    )
}
