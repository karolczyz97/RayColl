package com.raycoll.app.data.repository

import com.raycoll.app.core.Clock
import com.raycoll.app.data.local.db.RayCollDatabase
import com.raycoll.app.data.local.mappers.toDomain
import com.raycoll.app.data.local.mappers.toEntity
import com.raycoll.app.domain.model.ModeStep
import com.raycoll.app.domain.model.StudyMode
import com.raycoll.app.domain.operations.edited
import com.raycoll.app.domain.operations.newStudyMode
import com.raycoll.app.domain.operations.resetToBuiltIn
import com.raycoll.app.domain.operations.resolveStudyMode
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/** Tryby nauki w aktywnej przestrzeni danych. */
@Singleton
class StudyModeRepository @Inject constructor(
    db: RayCollDatabase,
    private val clock: Clock,
    private val namespaceManager: NamespaceManager,
    private val dataMutations: DataMutations
) {
    private val modeDao = db.studyModeDao()

    private val ns: String get() = namespaceManager.current()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun liveModes(): Flow<List<StudyMode>> = namespaceManager.active.flatMapLatest { ns ->
        modeDao.liveModes(ns).map { entities -> entities.map { it.toDomain() } }
    }

    suspend fun getMode(modeId: String): StudyMode? = modeDao.getMode(ns, modeId)?.toDomain()

    /** Tryb sesji: przypisany albo — gdy go brak — ten sam domyślny, który pokazują ekrany. */
    suspend fun getModeOrDefault(modeId: String): StudyMode = resolveStudyMode(
        modeDao.allModes(ns).map { it.toDomain() }.filter { it.deletedAt == null },
        modeId
    )

    suspend fun addMode(name: String, steps: List<ModeStep>): String {
        val mode = newStudyMode(name, steps, clock.now())
        modeDao.upsertMode(mode.toEntity(ns))
        dataMutations.signal(CloudWriteMode.DEBOUNCED)
        return mode.id
    }

    suspend fun updateMode(mode: StudyMode) {
        modeDao.upsertMode(mode.edited(clock.now()).toEntity(ns))
        dataMutations.signal(CloudWriteMode.DEBOUNCED)
    }

    suspend fun deleteMode(modeId: String) {
        modeDao.softDeleteMode(ns, modeId, clock.now())
        dataMutations.signal(CloudWriteMode.IMMEDIATE)
    }

    suspend fun resetMode(modeId: String) {
        val mode = getMode(modeId) ?: return
        modeDao.upsertMode(mode.resetToBuiltIn(clock.now()).toEntity(ns))
        dataMutations.signal(CloudWriteMode.DEBOUNCED)
    }
}
