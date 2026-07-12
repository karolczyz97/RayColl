package com.raycoll.app.playback

import com.raycoll.app.domain.session.SessionEngine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StudySessionHolder @Inject constructor() {
    private var engine: SessionEngine? = null
    private var deckId: String? = null
    private var deckName: String? = null

    fun create(engine: SessionEngine, deckId: String, deckName: String) {
        this.engine = engine
        this.deckId = deckId
        this.deckName = deckName
    }

    fun get(): SessionEngine? = engine
    fun deckId(): String? = deckId
    fun deckName(): String? = deckName
    fun isActive(): Boolean = engine != null

    fun release() {
        engine?.stop()
        engine = null
        deckId = null
        deckName = null
    }
}
