package com.raycoll.app.data.sync

import com.raycoll.app.domain.model.StoreData
import kotlinx.coroutines.flow.StateFlow

/**
 * Dane konta w chmurze widziane przez synchronizację (implementacja: [SyncEngine] na Firestore).
 * Zapis wysyła różnice względem ostatnio wczytanego/zapisanego stanu chmury („bazy”).
 */
interface CloudSync {
    val status: StateFlow<SyncStatus>

    /** Wczytuje dane konta i ustawia bazę. */
    suspend fun load(uid: String): CloudLoad

    /** Czy od naszego ostatniego odczytu/zapisu nikt inny nie zapisał chmury (inne urządzenie, web). */
    suspend fun hasFreshBaseline(uid: String): Boolean

    /** Wysyła różnice [data] względem bazy; false — brak bazy albo błąd (do ponowienia). */
    suspend fun save(uid: String, data: StoreData): Boolean

    fun clearBaseline()
}

/** Zalogowane konto widziane przez synchronizację (implementacja: AuthRepository). */
interface CurrentUser {
    fun currentUid(): String?
}
