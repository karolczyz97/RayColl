package com.raycoll.app.data.repository

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wspólny sygnał "coś się zmieniło lokalnie" dla wszystkich repozytoriów danych.
 * Nasłuchuje go SyncLifecycle i robi debounced push do Firestore.
 */
@Singleton
class DataMutations @Inject constructor() {
    private val _events = MutableSharedFlow<Unit>(extraBufferCapacity = 64)
    val events: SharedFlow<Unit> = _events

    fun signal() { _events.tryEmit(Unit) }
}
