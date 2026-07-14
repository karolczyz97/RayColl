package com.raycoll.app.data.repository

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tryb wysyłki mutacji do chmury (polityka z planu T10 / useStorePersistence.ts):
 * - DEBOUNCED — zwykłe edycje (1200 ms debounce),
 * - STUDY — oceny w sesji nauki (flush co 10 ocen + na granicy sesji),
 * - IMMEDIATE — operacje krytyczne (import, usunięcie, archiwizacja, restore).
 */
enum class CloudWriteMode { DEBOUNCED, STUDY, IMMEDIATE }

/**
 * Wspólny sygnał "coś się zmieniło lokalnie" dla wszystkich repozytoriów danych.
 * Nasłuchuje go SyncLifecycle i wypycha zmiany do Firestore wg trybu.
 */
@Singleton
class DataMutations @Inject constructor() {
    private val _events = MutableSharedFlow<CloudWriteMode>(extraBufferCapacity = 64)
    val events: SharedFlow<CloudWriteMode> = _events

    fun signal(mode: CloudWriteMode = CloudWriteMode.DEBOUNCED) { _events.tryEmit(mode) }
}
