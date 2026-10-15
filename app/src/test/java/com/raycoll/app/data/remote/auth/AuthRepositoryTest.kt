package com.raycoll.app.data.remote.auth

import android.content.Context
import android.os.Bundle
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.GetPasswordOption
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.tasks.Tasks
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AuthRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val auth = mock(FirebaseAuth::class.java)
    private val manager = mock(CredentialManager::class.java)
    private val repo = AuthRepository(auth, manager)

    private fun anyRequest(): GetCredentialRequest = any(GetCredentialRequest::class.java)
        ?: GetCredentialRequest(listOf(GetPasswordOption()))

    private fun sameContext(): Context = eq(context) ?: context

    private suspend fun failSelection(error: Exception) {
        `when`(manager.getCredential(sameContext(), anyRequest())).thenAnswer { throw error }
    }

    @Test fun `account cancellation is distinct from missing credentials and provider failure`() = runBlocking {
        failSelection(GetCredentialCancellationException())
        assertEquals(IdTokenResult.Cancelled, repo.requestGoogleIdToken(context))
        org.mockito.Mockito.reset(manager)
        failSelection(NoCredentialException())
        assertEquals(IdTokenResult.Failure(SignInError.NO_TOKEN), repo.requestGoogleIdToken(context))
        org.mockito.Mockito.reset(manager)
        failSelection(GetCredentialProviderConfigurationException())
        assertEquals(IdTokenResult.Failure(SignInError.PLAY_SERVICES), repo.requestGoogleIdToken(context))
    }

    @Test fun `invalid token data is reported instead of signing in`() = runBlocking {
        val credential = CustomCredential(GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL, Bundle())
        `when`(manager.getCredential(sameContext(), anyRequest()))
            .thenReturn(GetCredentialResponse(credential))
        assertEquals(IdTokenResult.Failure(SignInError.INVALID_TOKEN), repo.requestGoogleIdToken(context))
    }

    @Test fun `firebase failure maps to login failure`() = runBlocking {
        `when`(auth.signInWithCredential(any(AuthCredential::class.java)))
            .thenReturn(Tasks.forException<AuthResult>(IllegalStateException("offline")))
        assertEquals(SignInResult.Failure(SignInError.LOGIN_FAILED), repo.signInWithIdToken("token"))
    }

    @Test fun `coroutine cancellation is propagated from credential selection`() {
        runBlocking { failSelection(CancellationException("screen closed")) }
        assertThrows(CancellationException::class.java) { runBlocking { repo.requestGoogleIdToken(context) } }
    }
}
