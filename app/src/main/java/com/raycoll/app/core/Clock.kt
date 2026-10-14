package com.raycoll.app.core

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Odświeżanie terminów na otwartym ekranie, także gdy baza się nie zmienia. */
fun Clock.minuteTicks(): Flow<Long> = flow {
    while (true) {
        emit(now())
        delay(60_000L)
    }
}

/** Źródło czasu (ms od epoki) — wstrzykiwane, żeby testy mogły sterować czasem. */
fun interface Clock {
    fun now(): Long
}

object SystemClock : Clock {
    override fun now(): Long = System.currentTimeMillis()
}
