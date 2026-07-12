package com.raycoll.app.core

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object LocalDates {
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun dayKey(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val date = LocalDate.ofInstant(
            java.time.Instant.ofEpochMilli(epochMillis),
            zone
        )
        return date.format(formatter)
    }
}
