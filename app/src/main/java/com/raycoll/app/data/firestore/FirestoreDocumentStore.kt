package com.raycoll.app.data.firestore

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.WriteBatch
import com.raycoll.app.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

@Singleton
class FirestoreDocumentStore @Inject constructor(
    private val firestore: FirebaseFirestore,
    @ApplicationScope private val scope: CoroutineScope,
    @ApplicationContext private val context: Context
) : DocumentStore {
    private val listeners = mutableMapOf<String, Flow<Docs>>()
    override val rejections = MutableSharedFlow<Int>(extraBufferCapacity = 64)
    override val offline = MutableStateFlow(false)

    override fun observe(collectionPath: String): Flow<Docs> = synchronized(listeners) {
        listeners.getOrPut(collectionPath) {
            callbackFlow {
                val registration = firestore.collection(collectionPath).addSnapshotListener(MetadataChanges.INCLUDE) {
                        snapshot,
                        error
                    ->
                    if (error != null) {
                        Log.w("DocumentStore", "Listen failed: $collectionPath", error)
                    }
                    if (snapshot != null) {
                        val cached = snapshot.metadata.isFromCache
                        val network = context.getSystemService(ConnectivityManager::class.java)
                        val capabilities = network.getNetworkCapabilities(network.activeNetwork)
                        offline.value =
                            cached && capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) != true
                        trySend(Docs(snapshot.documents.map { Doc(it.id, it.data.orEmpty()) }, cached))
                    }
                }
                awaitClose { registration.remove() }
            }.shareIn(scope, SharingStarted.WhileSubscribed(5_000), replay = 1)
        }
    }

    override suspend fun cached(collectionPath: String): List<Doc> =
        firestore.collection(collectionPath).get(Source.CACHE).await().documents.map { Doc(it.id, it.data.orEmpty()) }

    override suspend fun cachedDoc(path: String): Doc? = try {
        val snapshot = firestore.document(path).get(Source.CACHE).await()
        if (snapshot.exists()) Doc(snapshot.id, snapshot.data.orEmpty()) else null
    } catch (error: FirebaseFirestoreException) {
        if (error.code == FirebaseFirestoreException.Code.UNAVAILABLE) null else throw error
    }

    override fun write(op: WriteOp) {
        val batch = firestore.batch()
        batch.add(op)
        track(batch.commit(), listOf(op))
    }

    override fun writeAll(ops: List<WriteOp>) {
        ops.chunked(400).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { batch.add(it) }
            track(batch.commit(), chunk)
        }
    }

    private fun WriteBatch.add(op: WriteOp) {
        val ref = firestore.document(op.path)
        when (op) {
            is WriteOp.Set -> if (op.merge) set(ref, fields(op.data), SetOptions.merge()) else set(ref, fields(op.data))
            is WriteOp.Update -> update(ref, fields(op.fields))
            is WriteOp.Delete -> delete(ref)
        }
    }

    private fun fields(data: Map<String, Any?>): Map<String, Any?> = data.mapValues { (_, value) ->
        when (value) {
            FieldOp.ServerTime -> FieldValue.serverTimestamp()
            is FieldOp.Increment -> FieldValue.increment(value.by)
            else -> value
        }
    }

    private fun track(task: Task<Void>, ops: List<WriteOp>) {
        task.addOnFailureListener { error ->
            Log.w("DocumentStore", "Write rejected: ${ops.map { it.path }}", error)
            if ((error as? FirebaseFirestoreException)?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                scope.launch { rejections.emit(ops.size) }
            }
        }
    }

    override suspend fun awaitPendingWrites(timeoutMs: Long): Boolean = try {
        withTimeoutOrNull(timeoutMs) {
            firestore.waitForPendingWrites().await()
            true
        } ?: false
    } catch (error: FirebaseFirestoreException) {
        Log.w("DocumentStore", "Could not confirm pending writes", error)
        false
    }
}
