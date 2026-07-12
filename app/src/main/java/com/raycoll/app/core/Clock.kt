package com.raycoll.app.core

import java.time.Clock as JavaClock

interface Clock {
    fun millis(): Long
}

class SystemClock : Clock {
    override fun millis(): Long = System.currentTimeMillis()
}

class FixedClock(private val timestamp: Long) : Clock {
    override fun millis(): Long = timestamp
}

fun Clock(): Clock = SystemClock()
