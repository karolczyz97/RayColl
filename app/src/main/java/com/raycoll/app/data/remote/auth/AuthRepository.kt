package com.raycoll.app.data.remote.auth

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.raycoll.app.R
import com.raycoll.app.data.remote.FirebaseConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

sealed class SignInResult {
    data class Success(val uid: String) : SignInResult()
    data object Cancelled : SignInResult()
    data class Error(val messageRes: Int) : SignInResult()
}

@Singleton
class AuthRepository @Inject constructor() {
    private val auth: FirebaseAuth by lazy {
        FirebaseAuth.getInstance().also { instance ->
            instance.addAuthStateListener { _authState.value = it.currentUser?.uid ?: "" }
        }
    }

    private val _authState = MutableStateFlow("")
    val authState: StateFlow<String> = _authState.asStateFlow()

    fun currentUser(): FirebaseUser? = auth.currentUser
    fun isSignedIn(): Boolean = auth.currentUser != null
    fun currentUid(): String = auth.currentUser?.uid ?: ""
    fun currentEmail(): String = auth.currentUser?.email ?: ""

    /** Logowanie Google przez Credential Manager; wymaga kontekstu Activity. */
    suspend fun signInWithGoogle(activityContext: Context): SignInResult {
        val credentialManager = CredentialManager.create(activityContext)
        val googleIdOption = GetGoogleIdOption.Builder()
            .setServerClientId(FirebaseConfig.WEB_CLIENT_ID)
            .setFilterByAuthorizedAccounts(false)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val credential = try {
            credentialManager.getCredential(activityContext, request).credential
        } catch (_: GetCredentialCancellationException) {
            return SignInResult.Cancelled
        } catch (_: GetCredentialException) {
            return SignInResult.Error(R.string.auth_error_play_services)
        }

        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            return SignInResult.Error(R.string.auth_error_no_token)
        }

        val idToken = try {
            GoogleIdTokenCredential.createFrom(credential.data).idToken
        } catch (_: Exception) {
            return SignInResult.Error(R.string.auth_error_invalid_token)
        }

        return try {
            val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
            val user = auth.signInWithCredential(firebaseCredential).await().user
                ?: return SignInResult.Error(R.string.auth_error_login_failed)
            _authState.value = user.uid
            SignInResult.Success(user.uid)
        } catch (_: Exception) {
            SignInResult.Error(R.string.auth_error_login_failed)
        }
    }

    fun signOut() {
        auth.signOut()
        _authState.value = ""
    }

    suspend fun signOut(activityContext: Context) {
        auth.signOut()
        _authState.value = ""
        try {
            CredentialManager.create(activityContext)
                .clearCredentialState(ClearCredentialStateRequest())
        } catch (_: Exception) {
            // brak stanu credentiali nie jest błędem wylogowania
        }
    }

    fun onAuthStateChanged(uid: String?) {
        _authState.value = uid ?: ""
    }
}
