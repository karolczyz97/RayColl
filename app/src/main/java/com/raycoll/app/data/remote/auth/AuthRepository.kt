package com.raycoll.app.data.remote.auth

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.raycoll.app.data.remote.FirebaseConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

data class Account(val uid: String, val email: String?)

private const val TAG = "AuthRepository"

private fun FirebaseUser.toAccount() = Account(uid, email)

enum class SignInError { PLAY_SERVICES, NO_TOKEN, INVALID_TOKEN, LOGIN_FAILED, LOCAL_DATA }

sealed interface SignInResult {
    data class Success(val account: Account, val isNewUser: Boolean = false) : SignInResult
    data object ChoiceRequired : SignInResult
    data class Failure(val error: SignInError) : SignInResult
}

/** Wynik wyboru konta Google (Credential Manager), przed zalogowaniem w Firebase. */
sealed interface IdTokenResult {
    data class Token(val idToken: String) : IdTokenResult
    data object Cancelled : IdTokenResult
    data class Failure(val error: SignInError) : IdTokenResult
}

/**
 * Logowanie Google w dwóch krokach: wybór konta potrzebuje Activity (arkusz Credential
 * Managera) i powinien żyć tyle co ekran; logowanie tokenem w Firebase już jej nie potrzebuje.
 */
interface GoogleSignIn {
    /** Zalogowane konto; null = gość. */
    val account: StateFlow<Account?>

    suspend fun requestGoogleIdToken(activityContext: Context): IdTokenResult

    suspend fun signInWithIdToken(idToken: String): SignInResult

    suspend fun signOut()
}

/** Konto Google przez Credential Manager + Firebase Auth. */
@Singleton
class AuthRepository internal constructor(
    private val auth: FirebaseAuth,
    private val credentialManager: CredentialManager
) : GoogleSignIn {
    @Inject constructor(auth: FirebaseAuth, @ApplicationContext context: Context) :
        this(auth, CredentialManager.create(context))

    private val _account = MutableStateFlow(auth.currentUser?.takeUnless { it.isAnonymous }?.toAccount())

    override val account: StateFlow<Account?> = _account.asStateFlow()

    init {
        auth.addAuthStateListener {
            _account.value =
                it.currentUser?.takeUnless { user -> user.isAnonymous }?.toAccount()
        }
    }

    val currentUser: FirebaseUser? get() = auth.currentUser
    suspend fun signInAnonymously() = auth.signInAnonymously().await()
    suspend fun linkWithGoogle(idToken: String) = checkNotNull(auth.currentUser)
        .linkWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
    suspend fun deleteCurrentUser() {
        try {
            auth.currentUser?.delete()?.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not delete empty guest account", e)
        }
    }
    suspend fun signInWithCredential(credential: com.google.firebase.auth.AuthCredential): SignInResult = try {
        val result = auth.signInWithCredential(credential).await()
        val user = result.user
        if (user == null) {
            SignInResult.Failure(SignInError.LOGIN_FAILED)
        } else {
            SignInResult.Success(user.toAccount(), result.additionalUserInfo?.isNewUser == true)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Firebase sign-in failed", e)
        SignInResult.Failure(SignInError.LOGIN_FAILED)
    }

    /** Wymaga kontekstu Activity — Credential Manager pokazuje własny arkusz wyboru konta. */
    override suspend fun requestGoogleIdToken(activityContext: Context): IdTokenResult {
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(
                GetSignInWithGoogleOption.Builder(FirebaseConfig.WEB_CLIENT_ID)
                    .build()
            )
            .build()
        val credential = try {
            credentialManager.getCredential(activityContext, request).credential
        } catch (_: GetCredentialCancellationException) {
            return IdTokenResult.Cancelled
        } catch (_: NoCredentialException) {
            return IdTokenResult.Failure(SignInError.NO_TOKEN)
        } catch (e: GetCredentialException) {
            Log.w(TAG, "Google account selection failed", e)
            return IdTokenResult.Failure(SignInError.PLAY_SERVICES)
        }
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            return IdTokenResult.Failure(SignInError.NO_TOKEN)
        }
        return try {
            IdTokenResult.Token(GoogleIdTokenCredential.createFrom(credential.data).idToken)
        } catch (_: GoogleIdTokenParsingException) {
            IdTokenResult.Failure(SignInError.INVALID_TOKEN)
        }
    }

    override suspend fun signInWithIdToken(idToken: String): SignInResult =
        signInWithCredential(GoogleAuthProvider.getCredential(idToken, null))

    override suspend fun signOut() {
        auth.signOut()
        try {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Firebase jest już wylogowane; błąd dostawcy nie może zatrzymać zmiany przestrzeni.
            Log.w(TAG, "Could not clear credential provider state", e)
        }
    }
}
