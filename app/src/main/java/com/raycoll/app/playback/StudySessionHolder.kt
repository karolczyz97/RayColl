package com.raycoll.app.playback

import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.domain.session.SessionDeps
import com.raycoll.app.domain.session.SessionEngine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StudySessionHolder @Inject constructor(
    private val depsAdapter: SessionDepsAdapter,
) {
    var engine: SessionEngine? = null
        private set
    var groupId: String = ""
        private set
    var groupName: String = ""
        private set

    fun create(group: FlashcardGroup, cards: List<com.raycoll.app.domain.model.Flashcard>) {
        groupId = group.id
        groupName = group.name
        engine = SessionEngine(group, cards, depsAdapter, kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main))
    }

    fun attach(): SessionEngine? = engine
    fun release() { engine?.stop(); engine = null }
}
