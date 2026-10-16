package com.raycoll.app.data.repository

import com.raycoll.app.core.Clock
import com.raycoll.app.data.account.CurrentAccount
import com.raycoll.app.data.firestore.DocumentStore
import com.raycoll.app.data.firestore.UserPaths
import com.raycoll.app.data.firestore.WriteOp
import com.raycoll.app.data.remote.firestore.FirestoreSchema as Schema
import com.raycoll.app.domain.model.ModeStep
import com.raycoll.app.domain.model.StudyMode
import com.raycoll.app.domain.operations.edited
import com.raycoll.app.domain.operations.newStudyMode
import com.raycoll.app.domain.operations.resetToBuiltIn
import com.raycoll.app.domain.operations.resolveStudyMode
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class StudyModeRepository @Inject constructor(
    private val store: DocumentStore,
    private val account: CurrentAccount,
    private val clock: Clock
) {
    private fun paths() = UserPaths(checkNotNull(account.uid.value))
    fun liveModes() = account.uid.filterNotNull().flatMapLatest { uid ->
        store.observe(UserPaths(uid).modes()).map {
            it.docs.mapNotNull(Schema::readMode).filter { it.deletedAt == null }
        }
    }
    suspend fun getMode(modeId: String) = store.cachedDoc(paths().mode(modeId))?.let(Schema::readMode)?.takeIf {
        it.deletedAt ==
            null
    }
    suspend fun getModeOrDefault(modeId: String) = resolveStudyMode(
        store.cached(paths().modes()).mapNotNull(Schema::readMode).filter {
            it.deletedAt ==
                null
        },
        modeId
    )
    suspend fun addMode(name: String, steps: List<ModeStep>): String {
        val mode = newStudyMode(name, steps, clock.now())
        store.write(WriteOp.Set(paths().mode(mode.id), Schema.modeToMap(mode)))
        return mode.id
    }
    suspend fun updateMode(mode: StudyMode) {
        if (getMode(mode.id) == null) return
        store.write(
            WriteOp.Update(
                paths().mode(mode.id),
                Schema.modeToMap(mode.edited(clock.now())).filterKeys {
                    it !=
                        "deletedAt"
                }
            )
        )
    }
    suspend fun deleteMode(modeId: String) {
        if (getMode(modeId) == null) return
        store.write(WriteOp.Update(paths().mode(modeId), mapOf("deletedAt" to clock.now(), "updatedAt" to clock.now())))
    }

    /** Cofnięcie usunięcia trybu; talie, które go używały, wracają do niego same (wskazują ten sam id). */
    suspend fun restoreMode(modeId: String) {
        val path = paths().mode(modeId)
        if (store.cachedDoc(path)?.data?.get("deletedAt") == null) return
        store.write(WriteOp.Update(path, mapOf("deletedAt" to null, "updatedAt" to clock.now())))
    }
    suspend fun resetMode(modeId: String) {
        val mode = getMode(modeId) ?: return
        updateMode(mode.resetToBuiltIn(clock.now()))
    }
}
