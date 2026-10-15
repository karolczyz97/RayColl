package com.raycoll.app.data.sync

import com.raycoll.app.domain.model.StoreData
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudCommitTest {
    @Test
    fun `no op preserves the baseline without a root read while replacement alone is published`() {
        val data = StoreData()
        assertFalse(cloudCommitRequired(data, data, SyncDiff.diffSave(data, data)))
        val reset = data.copy(replacementAt = 100, replacementId = "reset")
        assertTrue(cloudCommitRequired(data, reset, SyncDiff.diffSave(data, reset)))
        assertFalse(cloudCommitRequired(reset, reset, SyncDiff.diffSave(reset, reset)))
    }
}
