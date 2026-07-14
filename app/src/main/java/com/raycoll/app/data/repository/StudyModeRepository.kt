package com.raycoll.app.data.repository

import androidx.room.withTransaction
import com.raycoll.app.core.Clock
import com.raycoll.app.data.local.db.RayCollDatabase
import com.raycoll.app.data.local.mappers.Mappers
import com.raycoll.app.domain.model.StudyMode
import com.raycoll.app.domain.operations.addStudyMode
import com.raycoll.app.domain.operations.deleteStudyMode
import com.raycoll.app.domain.operations.resetStudyMode
import com.raycoll.app.domain.operations.updateStudyMode
import com.raycoll.app.domain.srs.createSeedModes
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.map

@Singleton
class StudyModeRepository @Inject constructor(
    private val db: RayCollDatabase,
    private val clock: Clock,
    private val namespaceManager: NamespaceManager,
    private val dataMutations: DataMutations,
) {
    private val modeDao = db.studyModeDao()

    fun liveModes() = modeDao.liveModes(namespaceManager.current()).map { entities -> entities.map { Mappers.entityToMode(it) } }

    suspend fun getModes(): List<StudyMode> {
        val ns = namespaceManager.current()
        return modeDao.allModes(ns).map { Mappers.entityToMode(it) }
    }

    suspend fun addMode(name: String, steps: List<com.raycoll.app.domain.model.ModeStep>): String {
        val ns = namespaceManager.current()
        val now = clock.now()
        val (modes, id) = addStudyMode(emptyList(), name, steps, now)
        modeDao.insertMode(Mappers.modeToEntity(modes.last(), ns))
        dataMutations.signal()
        return id
    }

    suspend fun updateMode(mode: StudyMode) {
        val ns = namespaceManager.current()
        val now = clock.now()
        val updated = updateStudyMode(listOf(mode), mode, now)
        modeDao.insertMode(Mappers.modeToEntity(updated[0], ns))
        dataMutations.signal()
    }

    suspend fun deleteMode(modeId: String) {
        val ns = namespaceManager.current()
        modeDao.softDeleteMode(ns, modeId, clock.now())
        dataMutations.signal()
    }

    suspend fun resetMode(modeId: String) {
        val ns = namespaceManager.current()
        val now = clock.now()
        val modes = getModes()
        val updated = resetStudyMode(modes, modeId, now)
        val mode = updated.find { it.id == modeId } ?: return
        modeDao.insertMode(Mappers.modeToEntity(mode, ns))
        dataMutations.signal()
    }

    suspend fun seedIfEmpty() {
        val ns = namespaceManager.current()
        // check + insert w JEDNEJ transakcji — patrz DeckRepository.seedIfEmpty
        db.withTransaction {
            if (modeDao.allModes(ns).isEmpty()) {
                modeDao.insertModes(createSeedModes().map { Mappers.modeToEntity(it, ns) })
            }
        }
    }
}
