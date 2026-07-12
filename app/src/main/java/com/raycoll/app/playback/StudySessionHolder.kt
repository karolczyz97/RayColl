package com.raycoll.app.playback

import com.raycoll.app.domain.session.SessionEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StudySessionHolder @Inject constructor() {
    private val _engine = MutableStateFlow<SessionEngine?>(null)
    val engineFlow: StateFlow<SessionEngine?> = _engine.asStateFlow()

    private var deckId: String? = null
    private var deckName: String? = null

    fun create(engine: SessionEngine, deckId: String, deckName: String) {
        this._engine.value = engine
        this.deckId = deckId
        this.deckName = deckName
    }

    fun get(): SessionEngine? = _engine.value
    fun deckId(): String? = deckId
    fun deckName(): String? = deckName
    fun isActive(): Boolean = _engine.value != null

    fun release() {
        _engine.value?.stop()
        _engine.value = null
        deckId = null
        deckName = null
    }
}
