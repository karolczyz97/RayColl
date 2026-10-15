package com.raycoll.app.data.sync

import com.google.firebase.Timestamp
import com.raycoll.app.data.remote.firestore.FirestoreSchema
import com.raycoll.app.domain.model.StoreData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CloudRevisionTest {
    private val revision = CloudRevision(Timestamp(10, 0), "ours")
    private fun root(at: Long = 0, id: String = "", token: String = "ours") = mapOf(
        FirestoreSchema.FIELD_UPDATED_AT to revision.updatedAt,
        FIELD_SYNC_TOKEN to token,
        FirestoreSchema.FIELD_REPLACEMENT_AT to at,
        FirestoreSchema.FIELD_REPLACEMENT_ID to id
    )

    @Test fun `legacy cloud and a migrated local database share the zero generation`() {
        assertEquals(0L to "", FirestoreSchema.replacementFromMap(emptyMap()))
        requireCloudBaseline(emptyMap(), CloudRevision(null, null), StoreData())
    }

    @Test fun `a concurrent reset cannot be overwritten by an old generation`() {
        assertThrows(CloudChangedException::class.java) {
            requireCloudBaseline(root(at = 20, id = "reset"), revision, StoreData())
        }
    }

    @Test fun `a concurrent edit with the same timestamp still invalidates the transaction`() {
        assertThrows(CloudChangedException::class.java) {
            requireCloudBaseline(root(token = "other-device"), revision, StoreData())
        }
    }

    @Test fun `subsequent chunks must match the newly committed generation`() {
        val reset = StoreData(replacementAt = 20, replacementId = "reset")
        requireCloudBaseline(root(20, "reset"), revision, reset)
        assertThrows(CloudChangedException::class.java) {
            requireCloudBaseline(root(21, "newer-reset"), revision, reset)
        }
    }

    @Test fun `corrupt generation is never silently downgraded to legacy data`() {
        listOf(
            mapOf("replacementAt" to "oops"),
            mapOf("replacementAt" to -1),
            mapOf("replacementAt" to 1.5, "replacementId" to "bad"),
            mapOf("replacementAt" to 5, "replacementId" to "")
        ).forEach { root ->
            assertThrows(Exception::class.java) { FirestoreSchema.replacementFromMap(root) }
        }
    }
}
