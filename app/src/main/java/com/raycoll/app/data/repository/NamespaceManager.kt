package com.raycoll.app.data.repository

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Która przestrzeń danych lokalnych jest aktywna: "" = gość, inaczej uid konta.
 * Repozytoria czytają i piszą tylko w aktywnej przestrzeni; ekrany obserwują [active].
 */
@Singleton
class NamespaceManager @Inject constructor() {
    private val _active = MutableStateFlow(GUEST)
    val active: StateFlow<String> = _active.asStateFlow()

    fun current(): String = _active.value

    fun switchTo(uid: String) {
        _active.value = uid
    }

    fun switchToGuest() {
        _active.value = GUEST
    }

    companion object {
        const val GUEST = ""
    }
}
