package com.raycoll.app.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object LocalDates {
    // atZone().toLocalDate() zamiast LocalDate.ofInstant — to drugie istnieje na Androidzie dopiero od API 34
    fun localDate(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

    /** Klucz dnia w mapie aktywności: ISO yyyy-MM-dd. */
    fun dayKey(date: LocalDate): String = date.toString()

    fun dayKey(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String = dayKey(localDate(epochMillis, zone))
}
