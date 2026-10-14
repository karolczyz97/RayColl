package com.raycoll.app.domain.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class TypedSettingsSerializationTest {
    @Test
    fun `study filter retains existing backup keys`() {
        StudyFilter.entries.forEach { value ->
            val encoded = Json.encodeToString(StudyFilter.serializer(), value)
            assertEquals("\"${value.key}\"", encoded)
            assertEquals(value, Json.decodeFromString(StudyFilter.serializer(), encoded))
        }
    }

    @Test
    fun `card order retains existing backup keys`() {
        CardOrder.entries.forEach { value ->
            val encoded = Json.encodeToString(CardOrder.serializer(), value)
            assertEquals("\"${value.key}\"", encoded)
            assertEquals(value, Json.decodeFromString(CardOrder.serializer(), encoded))
        }
    }

    @Test
    fun `compound choices retain existing backup keys`() {
        BranchRating.entries.forEach { value ->
            val encoded = Json.encodeToString(BranchRating.serializer(), value)
            assertEquals("\"${value.key}\"", encoded)
            assertEquals(value, Json.decodeFromString(BranchRating.serializer(), encoded))
        }
        RevealStyle.entries.forEach { value ->
            val encoded = Json.encodeToString(RevealStyle.serializer(), value)
            assertEquals("\"${value.key}\"", encoded)
            assertEquals(value, Json.decodeFromString(RevealStyle.serializer(), encoded))
        }
    }

    @Test
    fun `unknown serialized settings fall back to safe defaults`() {
        val unknown = "\"unknown\""
        assertEquals(StudyFilter.DEFAULT, Json.decodeFromString(StudyFilter.serializer(), unknown))
        assertEquals(CardOrder.DEFAULT, Json.decodeFromString(CardOrder.serializer(), unknown))
        assertEquals(BranchRating.FROM_ANSWER, Json.decodeFromString(BranchRating.serializer(), unknown))
        assertEquals(RevealStyle.ALL, Json.decodeFromString(RevealStyle.serializer(), unknown))
    }
}
