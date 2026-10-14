package com.raycoll.app.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object LocalDates {
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    // atZone().toLocalDate() zamiast LocalDate.ofInstant — to drugie istnieje na Androidzie dopiero od API 34
    fun localDate(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

    fun dayKey(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        localDate(epochMillis, zone).format(formatter)
}
