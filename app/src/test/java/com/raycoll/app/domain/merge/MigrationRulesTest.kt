package com.raycoll.app.domain.merge

import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.domain.model.SrsState
import com.raycoll.app.domain.model.StoreData
import com.raycoll.app.domain.model.StudyMode
import com.raycoll.app.domain.model.WaitStep
import com.raycoll.app.domain.seed.SEED_MODES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Decyzja o migracji danych gościa i wykrywanie własnych trybów. */
class MigrationRulesTest {

    @Test
    fun `joining guest copies modified built-in modes, drops untouched ones and repoints decks`() {
        val untouched = SEED_MODES[1]
        val edited = SEED_MODES[0].copy(name = "Edited", updatedAt = 50)
        val custom = StudyMode("custom", "Custom")
        val deck = FlashcardGroup(id = "d", name = "Deck", cards = emptyList(), activeModeId = edited.id)
        val other = deck.copy(id = "d2", activeModeId = untouched.id)

        val joined = StoreData(
            listOf(deck, other),
            listOf(untouched, edited, custom)
        ).withBuiltInModesCopied(" (phone)")

        val copy = joined.studyModes.single { it.name == "Edited (phone)" }
        assertFalse(copy.isBuiltIn)
        assertEquals(null, copy.builtInSourceId)
        assertNotEquals(edited.id, copy.id)
        assertEquals(listOf(copy, custom), joined.studyModes)
        assertEquals(copy.id, joined.groups.single { it.id == "d" }.activeModeId)
        // konto ma własny niezmieniony tryb wbudowany o tym samym id
        assertEquals(untouched.id, joined.groups.single { it.id == "d2" }.activeModeId)
        // ponowienie daje tę samą kopię, inna chwila zmiany — inną
        assertEquals(copy.id, StoreData(listOf(deck), listOf(edited)).withBuiltInModesCopied("").studyModes.single().id)
        assertNotEquals(
            copy.id,
            StoreData(
                listOf(deck),
                listOf(edited.copy(updatedAt = 51))
            ).withBuiltInModesCopied("").studyModes.single().id
        )
    }

    @Test
    fun `deleted custom and modified built in modes do not trigger guest migration`() {
        val custom = StudyMode("custom", "Custom", deletedAt = 100)
        val edited = SEED_MODES.first().copy(name = "Edited", updatedAt = 50, deletedAt = 100)
        assertFalse(guestHasData(emptyList(), emptyMap(), listOf(custom, edited)))
    }

    @Test
    fun `guestHasData detects groups heatmap and user-authored modes`() {
        // pusto → brak danych
        assertFalse(guestHasData(emptyList(), emptyMap(), emptyList()))

        // żywa talia → dane
        val deck = FlashcardGroup(id = "g1", name = "Deck", cards = emptyList(), updatedAt = 1L)
        assertTrue(guestHasData(listOf(deck), emptyMap(), emptyList()))

        // usunięta talia → brak danych
        assertFalse(guestHasData(listOf(deck.copy(deletedAt = 1L)), emptyMap(), emptyList()))

        // heatmapa z aktywnością → dane
        assertTrue(guestHasData(emptyList(), mapOf("2026-05-27" to 1), emptyList()))
        assertFalse(guestHasData(emptyList(), mapOf("2026-05-27" to 0), emptyList()))

        // nietknięte tryby wbudowane (seed) → brak danych
        val seedModes = SEED_MODES
        assertFalse(guestHasData(emptyList(), emptyMap(), seedModes))

        // własny tryb → dane
        val customMode = StudyMode("custom-1", name = "My Mode", steps = emptyList(), isBuiltIn = false)
        assertTrue(guestHasData(emptyList(), emptyMap(), listOf(customMode)))
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
        assertFalse(guestHasData(listOf(seedShapedDeck()), emptyMap(), SEED_MODES))
    }

    @Test
    fun `edited or reviewed seed decks count as guest data`() {
        val seed = seedShapedDeck()
        assertTrue(guestHasData(listOf(seed.copy(updatedAt = 5L)), emptyMap(), emptyList()))

        val reviewed = seed.copy(cards = listOf(seed.cards[0].copy(srsUpdatedAt = 9L), seed.cards[1]))
        assertTrue(guestHasData(listOf(reviewed), emptyMap(), emptyList()))

        val edited = seed.copy(cards = listOf(seed.cards[0].copy(contentUpdatedAt = 9L), seed.cards[1]))
        assertTrue(guestHasData(listOf(edited), emptyMap(), emptyList()))
    }

    @Test
    fun `seed deck with a deleted card counts as guest data`() {
        val seed = seedShapedDeck()
        val changed = seed.copy(cards = listOf(seed.cards[0].copy(deletedAt = 10L), seed.cards[1]))
        assertTrue(guestHasData(listOf(changed), emptyMap(), SEED_MODES))
    }
}
