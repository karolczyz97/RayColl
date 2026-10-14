package com.raycoll.app.domain.merge

import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.domain.model.SrsState
import com.raycoll.app.domain.model.StudyMode
import com.raycoll.app.domain.model.WaitStep
import com.raycoll.app.domain.seed.SEED_MODES
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Decyzja o migracji danych gościa i wykrywanie własnych trybów. */
class MigrationRulesTest {

    @Test
    fun `shouldTriggerMigration only when user cache fresh and guest has data`() {
        assertTrue(shouldTriggerMigration(hasUserLocalCache = false, guestHasData = true))
        assertFalse(shouldTriggerMigration(hasUserLocalCache = true, guestHasData = true))
        assertFalse(shouldTriggerMigration(hasUserLocalCache = false, guestHasData = false))
        assertFalse(shouldTriggerMigration(hasUserLocalCache = true, guestHasData = false))
    }

    @Test
    fun `getGuestHasData detects groups heatmap and user-authored modes`() {
        // pusto → brak danych
        assertFalse(getGuestHasData(emptyList(), emptyMap(), emptyList()))

        // żywa talia → dane
        val deck = FlashcardGroup(id = "g1", name = "Deck", cards = emptyList(), updatedAt = 1L)
        assertTrue(getGuestHasData(listOf(deck), emptyMap(), emptyList()))

        // usunięta talia → brak danych
        assertFalse(getGuestHasData(listOf(deck.copy(deletedAt = 1L)), emptyMap(), emptyList()))

        // heatmapa z aktywnością → dane
        assertTrue(getGuestHasData(emptyList(), mapOf("2026-05-27" to 1), emptyList()))
        assertFalse(getGuestHasData(emptyList(), mapOf("2026-05-27" to 0), emptyList()))

        // nietknięte tryby wbudowane (seed) → brak danych
        val seedModes = SEED_MODES
        assertFalse(getGuestHasData(emptyList(), emptyMap(), seedModes))

        // własny tryb → dane
        val customMode = StudyMode("custom-1", name = "My Mode", steps = emptyList(), isBuiltIn = false)
        assertTrue(getGuestHasData(emptyList(), emptyMap(), listOf(customMode)))
    }

    @Test
    fun `isUserAuthoredMode true for custom and for built-in without valid sourceId`() {
        val custom = StudyMode("c", name = "Custom", isBuiltIn = false)
        assertTrue(isUserAuthoredMode(custom))

        val builtInValid = SEED_MODES[0]
        assertFalse(isUserAuthoredMode(builtInValid))

        val builtInNoSource = builtInValid.copy(builtInSourceId = null)
        assertTrue(isUserAuthoredMode(builtInNoSource))

        val builtInBadSource = builtInValid.copy(builtInSourceId = "not-a-builtin")
        assertTrue(isUserAuthoredMode(builtInBadSource))

        // edytowany wbudowany (zmiana nazwy lub kroków) = dane użytkownika
        val editedSteps = builtInValid.copy(steps = listOf(WaitStep(ms = 100L)))
        assertTrue(isUserAuthoredMode(editedSteps))
        val editedName = builtInValid.copy(name = "Edited Classic")
        assertTrue(isUserAuthoredMode(editedName))
    }

    private fun seedShapedDeck() = FlashcardGroup(
        id = "seed",
        name = "English - basics",
        cards = listOf(
            Flashcard(id = "c1", pages = listOf("Yes", "Tak"), srsState = SrsState()),
            Flashcard(id = "c2", pages = listOf("Please", "Prosze"), srsState = SrsState())
        )
    )

    @Test
    fun `untouched seed decks do not count as guest data`() {
        assertFalse(getGuestHasData(listOf(seedShapedDeck()), emptyMap(), SEED_MODES))
    }

    @Test
    fun `edited or reviewed seed decks count as guest data`() {
        val seed = seedShapedDeck()
        assertTrue(getGuestHasData(listOf(seed.copy(updatedAt = 5L)), emptyMap(), emptyList()))

        val reviewed = seed.copy(cards = listOf(seed.cards[0].copy(srsUpdatedAt = 9L), seed.cards[1]))
        assertTrue(getGuestHasData(listOf(reviewed), emptyMap(), emptyList()))

        val edited = seed.copy(cards = listOf(seed.cards[0].copy(contentUpdatedAt = 9L), seed.cards[1]))
        assertTrue(getGuestHasData(listOf(edited), emptyMap(), emptyList()))
    }
}
