package com.raycoll.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class IdsTest {
    @Test
    fun `newId returns UUID v4 string`() {
        val id = Ids.newId()
        assertEquals(36, id.length)
        assertEquals('-', id[8])
        assertEquals('-', id[13])
        assertEquals('-', id[18])
        assertEquals('-', id[23])
        assertEquals('4', id[14])
        val id2 = Ids.newId()
        assertNotEquals(id, id2)
    }
}
