package com.raycoll.app.data.local.mappers

import com.raycoll.app.data.local.db.CardEntity
import com.raycoll.app.data.local.db.StudyModeEntity
import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.model.ShowPageStep
import com.raycoll.app.domain.model.SrsState
import com.raycoll.app.domain.model.StudyMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Kolumny JSON w Room: poprawny zapis wraca bez zmian, uszkodzony daje pustą listę zamiast wyjątku. */
@RunWith(RobolectricTestRunner::class)
class EntityMappersTest {

    private fun cardEntity(pagesJson: String) = CardEntity(
        id = "c1",
        namespace = "",
        deckId = "d1",
        pagesJson = pagesJson,
        difficulty = 5.0,
        stability = 1.0,
        repetitions = 1,
        state = 2,
        lastReviewTimestamp = 10,
        nextReviewTimestamp = 20,
        contentUpdatedAt = 30,
        srsUpdatedAt = 40,
        deletedAt = null
    )

    @Test
    fun `card round-trips through the entity`() {
        val card = Flashcard("c1", listOf("front", "back \"quoted\""), SrsState(repetitions = 3), contentUpdatedAt = 7)
        assertEquals(card, card.toEntity(namespace = "", deckId = "d1").toDomain())
    }

    @Test
    fun `corrupt card pages give an empty card instead of a crash`() {
        val card = cardEntity(pagesJson = "[\"front\", ").toDomain()
        assertTrue(card.pages.isEmpty())
        assertEquals("c1", card.id)
        assertEquals(1, card.srsState.repetitions)
    }

    @Test
    fun `corrupt mode steps give an empty step list`() {
        val entity = StudyModeEntity("m1", "", "Mode", "{not json", false, null, 1, null)
        assertTrue(entity.toDomain().steps.isEmpty())
    }

    @Test
    fun `mode steps round-trip through the entity`() {
        val mode = StudyMode(id = "m1", name = "Mode", steps = listOf(ShowPageStep(pageIndex = 1)))
        assertEquals(mode, mode.toEntity(namespace = "").toDomain())
    }
}
