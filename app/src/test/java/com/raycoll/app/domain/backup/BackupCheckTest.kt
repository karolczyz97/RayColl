package com.raycoll.app.domain.backup

import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.domain.model.PageLimits
import com.raycoll.app.domain.model.ShowPageStep
import com.raycoll.app.domain.model.SrsState
import com.raycoll.app.domain.model.StoreData
import com.raycoll.app.domain.model.StudyMode
import com.raycoll.app.domain.model.WaitStep
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reguły kopii, których nie pokrywa BackupDocumentStoreTest: SRS, znaczniki czasu i tryby. */
class BackupCheckTest {
    private val card = Flashcard("c1", listOf("q", "a"), SrsState())
    private val deck = FlashcardGroup(
        id = "d1",
        name = "Deck",
        cards = listOf(card),
        activeModeId = "m1",
        pageLanguages = listOf("en", "pl"),
        pageNames = listOf("A", "B")
    )
    private val mode = StudyMode("m1", "Mode", listOf(ShowPageStep(pageIndex = 1)))
    private val valid = StoreData(listOf(deck), listOf(mode), mapOf("2026-10-15" to 1))

    private fun withCard(change: Flashcard.() -> Flashcard) =
        valid.copy(groups = listOf(deck.copy(cards = listOf(card.change()))))

    private fun withSrs(change: SrsState.() -> SrsState) = withCard { copy(srsState = srsState.change()) }

    private fun withMode(change: StudyMode.() -> StudyMode) = valid.copy(studyModes = listOf(mode.change()))

    /** Kopia jest odrzucona, a komunikat wskazuje element i pole. */
    private fun assertRejected(data: StoreData, vararg fragments: String) {
        val error = assertThrows(IllegalArgumentException::class.java) { BackupCheck.validate(data) }
        val message = error.message.orEmpty()
        fragments.forEach { assertTrue("'$message' powinien zawierać '$it'", it in message) }
    }

    @Test
    fun `valid backup passes`() {
        BackupCheck.validate(valid)
    }

    @Test
    fun `difficulty outside 1 to 10 or NaN is rejected`() {
        for (difficulty in listOf(0.5, 10.5, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertRejected(withSrs { copy(difficulty = difficulty) }, "card c1", "difficulty")
        }
    }

    @Test
    fun `negative or infinite stability and negative repetitions are rejected`() {
        assertRejected(withSrs { copy(stability = -0.1) }, "card c1", "stability")
        assertRejected(withSrs { copy(stability = Double.POSITIVE_INFINITY) }, "card c1", "stability")
        assertRejected(withSrs { copy(repetitions = -1) }, "card c1", "repetitions")
    }

    @Test
    fun `unknown SRS state and negative review times are rejected`() {
        assertRejected(withSrs { copy(state = 4) }, "card c1", "SRS state")
        assertRejected(withSrs { copy(state = -1) }, "card c1", "SRS state")
        assertRejected(withSrs { copy(lastReviewTimestamp = -1) }, "card c1", "review time")
        assertRejected(withSrs { copy(nextReviewTimestamp = -1) }, "card c1", "review time")
    }

    @Test
    fun `negative deck card and mode timestamps are rejected`() {
        val decks = listOf(
            deck.copy(updatedAt = -1),
            deck.copy(deletedAt = -1),
            deck.copy(archivedAt = -1)
        )
        decks.forEach { assertRejected(valid.copy(groups = listOf(it)), "deck d1", "negative timestamp") }
        assertRejected(withCard { copy(contentUpdatedAt = -1) }, "card c1", "negative timestamp")
        assertRejected(withCard { copy(srsUpdatedAt = -1) }, "card c1", "negative timestamp")
        assertRejected(withCard { copy(deletedAt = -1) }, "card c1", "negative timestamp")
        assertRejected(withMode { copy(updatedAt = -1) }, "mode m1", "negative timestamp")
        assertRejected(withMode { copy(deletedAt = -1) }, "mode m1", "negative timestamp")
    }

    @Test
    fun `built in mode without source id is rejected`() {
        assertRejected(withMode { copy(isBuiltIn = true, builtInSourceId = null) }, "mode m1", "built-in")
        assertRejected(withMode { copy(isBuiltIn = true, builtInSourceId = "a/b") }, "invalid id 'a/b'")
        BackupCheck.validate(withMode { copy(isBuiltIn = true, builtInSourceId = "standard") })
    }

    @Test
    fun `custom mode with source id is rejected`() {
        assertRejected(withMode { copy(builtInSourceId = "standard") }, "mode m1", "custom mode with source id")
    }

    @Test
    fun `step that normalization would change is rejected`() {
        assertRejected(withMode { copy(steps = listOf(WaitStep(ms = -1))) }, "mode m1", "step out of range")
        assertRejected(withMode { copy(steps = listOf(ShowPageStep(pageIndex = -1))) }, "mode m1", "step out of range")
    }

    @Test
    fun `referenced page beyond stored pages is rejected`() {
        val last = PageLimits.MAX_STORED_PAGE_COUNT - 1
        BackupCheck.validate(withMode { copy(steps = listOf(ShowPageStep(pageIndex = last))) })
        assertRejected(
            withMode { copy(steps = listOf(ShowPageStep(pageIndex = last + 1))) },
            "mode m1",
            "page ${last + 1} out of range"
        )
    }
}
