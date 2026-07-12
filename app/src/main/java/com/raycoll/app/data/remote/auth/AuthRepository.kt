package com.raycoll.app.data.remote.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor() {
    private val auth = FirebaseAuth.getInstance()

    private val _authState = MutableStateFlow(auth.currentUser?.uid ?: "")
    val authState: StateFlow<String> = _authState.asStateFlow()

    fun currentUser(): FirebaseUser? = auth.currentUser
    fun isSignedIn(): Boolean = auth.currentUser != null
    fun currentUid(): String = auth.currentUser?.uid ?: ""

    fun signOut() {
        auth.signOut()
        _authState.value = ""
    }

    fun onAuthStateChanged(uid: String?) {
        _authState.value = uid ?: ""
    }
}
