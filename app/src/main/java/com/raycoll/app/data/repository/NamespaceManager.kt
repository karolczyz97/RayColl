package com.raycoll.app.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NamespaceManager @Inject constructor() {
    private val _activeNamespace = MutableStateFlow("")
    val activeNamespace: StateFlow<String> = _activeNamespace.asStateFlow()

    fun setNamespace(ns: String) { _activeNamespace.value = ns }
    fun resetToGuest() { _activeNamespace.value = "" }
    fun current(): String = _activeNamespace.value
}
