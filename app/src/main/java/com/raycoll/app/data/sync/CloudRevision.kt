package com.raycoll.app.data.sync

import com.google.firebase.Timestamp
import com.raycoll.app.data.remote.firestore.FirestoreSchema
import com.raycoll.app.domain.model.StoreData

internal const val FIELD_SYNC_TOKEN = "syncToken"

/** Token rozróżnia również zapisy o tym samym czasie; stara chmura ma tylko updatedAt. */
internal data class CloudRevision(val updatedAt: Timestamp?, val token: String?)

internal class CloudChangedException : Exception("Cloud changed during synchronization; reload required")

internal fun cloudRevision(root: Map<String, Any?>): CloudRevision =
    CloudRevision(root[FirestoreSchema.FIELD_UPDATED_AT] as? Timestamp, root[FIELD_SYNC_TOKEN] as? String)

internal fun requireCloudBaseline(root: Map<String, Any?>, expected: CloudRevision, data: StoreData) {
    val generation = FirestoreSchema.replacementFromMap(root)
    if (cloudRevision(root) != expected || generation != (data.replacementAt to data.replacementId)) {
        throw CloudChangedException()
    }
}
