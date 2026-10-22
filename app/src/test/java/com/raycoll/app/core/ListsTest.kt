package com.raycoll.app.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ListsTest {
    private val letters = listOf("a", "b", "c", "d")

    @Test
    fun `an element moves forward and the ones between shift back`() {
        assertEquals(listOf("b", "c", "a", "d"), letters.moved(0, 2))
    }

    @Test
    fun `an element moves backward and the ones between shift forward`() {
        assertEquals(listOf("a", "d", "b", "c"), letters.moved(3, 1))
    }

    @Test
    fun `moving onto the same place and leaving the original untouched`() {
        assertEquals(letters, letters.moved(2, 2))
        letters.moved(0, 3)
        assertEquals(listOf("a", "b", "c", "d"), letters)
    }
}
