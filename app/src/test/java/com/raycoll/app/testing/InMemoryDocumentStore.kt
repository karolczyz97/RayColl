package com.raycoll.app.testing

import com.google.firebase.Timestamp
import com.raycoll.app.core.Clock
import com.raycoll.app.data.account.CurrentAccount
import com.raycoll.app.data.firestore.Doc
import com.raycoll.app.data.firestore.Docs
import com.raycoll.app.data.firestore.DocumentStore
import com.raycoll.app.data.firestore.FieldOp
import com.raycoll.app.data.firestore.WriteOp
import java.util.Date
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeAccount(initialUid: String = "uid-1") : CurrentAccount {
    override val uid = MutableStateFlow<String?>(initialUid)
    fun signIn(uid: String) {
        this.uid.value = uid
    }
}

class InMemoryDocumentStore(private val clock: Clock) : DocumentStore {
    private val documents = MutableStateFlow<Map<String, Map<String, Any?>>>(emptyMap())
    var failWhen: (WriteOp) -> Boolean = { false }
    override val rejections = MutableSharedFlow<Int>(extraBufferCapacity = 64)
    override val offline = MutableStateFlow(false)

    override fun observe(collectionPath: String) = documents.map { Docs(children(it, collectionPath), false) }
    override suspend fun cached(collectionPath: String) = children(documents.value, collectionPath)
    override suspend fun cachedDoc(path: String) = documents.value[path]?.let { Doc(path.substringAfterLast('/'), it) }

    private fun children(all: Map<String, Map<String, Any?>>, path: String): List<Doc> = all.entries
        .filter { it.key.substringBeforeLast('/') == path }
        .map { Doc(it.key.substringAfterLast('/'), it.value) }

    override fun write(op: WriteOp) = writeAll(listOf(op))

    override fun writeAll(ops: List<WriteOp>) {
        ops.chunked(400).forEach { chunk ->
            check(chunk.none(failWhen)) { "simulated write failure" }
            val next = documents.value.toMutableMap()
            chunk.forEach { op ->
                when (op) {
                    is WriteOp.Delete -> next.remove(op.path)

                    is WriteOp.Set -> next[op.path] =
                        resolve(op.data, if (op.merge) next[op.path].orEmpty() else emptyMap())

                    is WriteOp.Update -> {
                        check(op.path in next) { "Missing document: ${op.path}" }
                        next[op.path] = resolve(op.fields, next.getValue(op.path))
                    }
                }
            }
            documents.value = next
        }
    }

    private fun resolve(fields: Map<String, Any?>, old: Map<String, Any?>) = old + fields.mapValues { (key, value) ->
        when (value) {
            FieldOp.ServerTime -> Timestamp(Date(clock.now()))
            is FieldOp.Increment -> (old[key] as? Number)?.toLong()?.plus(value.by) ?: value.by
            else -> value
        }
    }

    override suspend fun awaitPendingWrites(timeoutMs: Long) = true
}
