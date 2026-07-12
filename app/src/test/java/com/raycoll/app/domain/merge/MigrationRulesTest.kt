package com.raycoll.app.domain.merge

import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.domain.model.StudyMode
import com.raycoll.app.domain.model.WaitStep
import com.raycoll.app.domain.srs.createSeedModes
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Port testów MigrationRules z "RayColl - Kopie" (spec = useStoreBootstrap.ts). */
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
        val deck = FlashcardGroup(id = "g1", name = "Deck", cards = emptyList())
        assertTrue(getGuestHasData(listOf(deck), emptyMap(), emptyList()))

        // usunięta talia → brak danych
        assertFalse(getGuestHasData(listOf(deck.copy(deletedAt = 1L)), emptyMap(), emptyList()))

        // heatmapa z aktywnością → dane
        assertTrue(getGuestHasData(emptyList(), mapOf("2026-05-27" to 1), emptyList()))
        assertFalse(getGuestHasData(emptyList(), mapOf("2026-05-27" to 0), emptyList()))

        // nietknięte tryby wbudowane (seed) → brak danych
        val seedModes = createSeedModes()
        assertFalse(getGuestHasData(emptyList(), emptyMap(), seedModes))

        // własny tryb → dane
        val customMode = StudyMode("custom-1", name = "My Mode", steps = emptyList(), isBuiltIn = false)
        assertTrue(getGuestHasData(emptyList(), emptyMap(), listOf(customMode)))
    }

    @Test
    fun `isUserAuthoredMode true for custom and for built-in without valid sourceId`() {
        val custom = StudyMode("c", name = "Custom", isBuiltIn = false)
        assertTrue(isUserAuthoredMode(custom))

        val builtInValid = createSeedModes()[0]
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
}
