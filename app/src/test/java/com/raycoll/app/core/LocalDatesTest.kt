package com.raycoll.app.core

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

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

    @Test
    fun `dayKey equals the ISO local date in any zone`() {
        val instant = 1705276800000L
        for (zone in listOf(ZoneId.of("UTC"), ZoneId.of("Pacific/Auckland"), ZoneId.of("America/Los_Angeles"))) {
            assertEquals(LocalDates.localDate(instant, zone).toString(), LocalDates.dayKey(instant, zone))
        }
        assertEquals("2024-01-15", LocalDates.dayKey(LocalDates.localDate(instant, ZoneId.of("UTC"))))
        // Auckland jest już następnego dnia
        assertEquals("2024-01-15", LocalDates.dayKey(instant, ZoneId.of("Pacific/Auckland")))
        assertEquals("2024-01-14", LocalDates.dayKey(instant, ZoneId.of("America/Los_Angeles")))
    }
}
