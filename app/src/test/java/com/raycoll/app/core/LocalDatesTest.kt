package com.raycoll.app.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

class LocalDatesTest {
    @Test
    fun `dayKey returns yyyy-MM-dd`() {
        val utc = ZoneId.of("UTC")
        assertEquals("2024-01-15", LocalDates.dayKey(1705276800000L, utc))
    }

    @Test
    fun `dayKey handles timezone`() {
        val warsaw = ZoneId.of("Europe/Warsaw")
        val utc = ZoneId.of("UTC")
        val midnightUtc = 1705276800000L // 2024-01-15 00:00 UTC
        val utcKey = LocalDates.dayKey(midnightUtc, utc)
        val warsawKey = LocalDates.dayKey(midnightUtc, warsaw)
        assertEquals("2024-01-15", utcKey)
        assertEquals("2024-01-15", warsawKey)
    }

    @Test
    fun `dayKey uses system default when no zone given`() {
        val key = LocalDates.dayKey(1705276800000L)
        assertEquals(10, key.length)
        assertEquals('-', key[4])
        assertEquals('-', key[7])
    }
}
