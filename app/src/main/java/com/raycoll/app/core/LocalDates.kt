package com.raycoll.app.core

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date

object LocalDates {
    private val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun dateString(date: LocalDate): String = formatter.format(date)

    fun today(): String = dateString(LocalDate.now())

    fun dateString(date: Date): String = dateString(
        date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
    )
}
