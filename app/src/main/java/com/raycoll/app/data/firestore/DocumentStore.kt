package com.raycoll.app.data.firestore

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

data class Doc(val id: String, val data: Map<String, Any?>)
data class Docs(val docs: List<Doc>, val fromCache: Boolean)

sealed interface FieldOp {
    data object ServerTime : FieldOp
    data class Increment(val by: Long) : FieldOp
}

sealed interface WriteOp {
    val path: String
    data class Set(override val path: String, val data: Map<String, Any?>, val merge: Boolean = false) : WriteOp
    data class Update(override val path: String, val fields: Map<String, Any?>) : WriteOp
    data class Delete(override val path: String) : WriteOp
}

interface DocumentStore {
    fun observe(collectionPath: String): Flow<Docs>
    suspend fun cached(collectionPath: String): List<Doc>
    suspend fun cachedDoc(path: String): Doc?
    fun write(op: WriteOp)
    fun writeAll(ops: List<WriteOp>)
    val rejections: SharedFlow<Int>
    val offline: StateFlow<Boolean>
    suspend fun awaitPendingWrites(timeoutMs: Long): Boolean
}

data class UserPaths(val uid: String) {
    fun decks() = "users/$uid/decks"
    fun deck(id: String) = "${decks()}/$id"
    fun cards(deckId: String) = "${deck(deckId)}/cards"
    fun card(deckId: String, id: String) = "${cards(deckId)}/$id"
    fun modes() = "users/$uid/studyModes"
    fun mode(id: String) = "${modes()}/$id"
    fun activity() = "users/$uid/activity"
    fun activityDay(day: String) = "${activity()}/$day"
}
