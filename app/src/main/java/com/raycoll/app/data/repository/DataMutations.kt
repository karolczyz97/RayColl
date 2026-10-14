package com.raycoll.app.data.repository

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/** Jak pilnie wysłać lokalną zmianę do chmury. */
enum class CloudWriteMode {
    /** Zwykła edycja — wysyłka chwilę po ostatniej zmianie. */
    DEBOUNCED,

    /** Ocena w sesji nauki — wysyłka co kilka ocen i na końcu sesji. */
    STUDY,

    /** Import, usunięcie, archiwizacja, przywrócenie — od razu. */
    IMMEDIATE
}

/** Sygnał "dane lokalne się zmieniły" ze wszystkich repozytoriów; słucha go synchronizacja. */
@Singleton
class DataMutations @Inject constructor() {
    // kanał bez limitu: żaden sygnał nie ginie, nawet gdy synchronizacja jeszcze nie słucha
    private val channel = Channel<CloudWriteMode>(Channel.UNLIMITED)

    /** Jeden odbiorca (synchronizacja): drugi kolektor dostawałby co drugi sygnał. */
    val events: Flow<CloudWriteMode> = channel.receiveAsFlow()

    fun signal(mode: CloudWriteMode) {
        channel.trySend(mode)
    }
}
