package com.example.ui.auth

import android.content.Context
import android.content.Intent
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

sealed interface AuthUiResult {
  data class Success(val user: FirebaseUser) : AuthUiResult
  data object Cancelled : AuthUiResult
  data class Error(val message: String) : AuthUiResult
}

object GoogleSignInManager {

  fun authStateFlow(auth: FirebaseAuth = FirebaseAuth.getInstance()): Flow<FirebaseUser?> = callbackFlow {
    trySend(auth.currentUser)
    val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
      trySend(firebaseAuth.currentUser)
    }
    auth.addAuthStateListener(listener)
    awaitClose { auth.removeAuthStateListener(listener) }
  }

  /**
   * Explicit interactive Google Sign-In flow triggered by the user button.
   * Uses GetSignInWithGoogleOption paired with context.getString(R.string.default_web_client_id).
   */
  suspend fun signInInteractive(
    activityContext: Context,
    auth: FirebaseAuth = FirebaseAuth.getInstance()
  ): AuthUiResult {
    return try {
      val serverClientId = activityContext.getString(R.string.default_web_client_id)
      val option = GetSignInWithGoogleOption.Builder(serverClientId)
        .build()
      val request = GetCredentialRequest.Builder()
        .addCredentialOption(option)
        .build()
      val credentialManager = CredentialManager.create(activityContext)
      val result = credentialManager.getCredential(
        context = activityContext,
        request = request
      )
      handleCredentialResponse(result.credential, auth)
    } catch (e: CancellationException) {
      throw e
    } catch (_: GetCredentialCancellationException) {
      AuthUiResult.Cancelled
    } catch (e: NoCredentialException) {
      AuthUiResult.Error("No Google account available on this device. Please add a Google account in device settings.")
    } catch (e: GetCredentialException) {
      AuthUiResult.Error(e.errorMessage?.toString() ?: e.localizedMessage ?: "Google sign-in failed")
    } catch (e: Exception) {
      AuthUiResult.Error(e.localizedMessage ?: "Authentication failed")
    }
  }

  /**
   * Silent / bottom-sheet auto-sign-in attempt for returning authorized accounts only.
   */
  suspend fun trySilentSignIn(
    activityContext: Context,
    auth: FirebaseAuth = FirebaseAuth.getInstance()
  ): AuthUiResult {
    if (auth.currentUser != null) {
      return AuthUiResult.Success(auth.currentUser!!)
    }
    return try {
      val serverClientId = activityContext.getString(R.string.default_web_client_id)
      val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(true)
        .setServerClientId(serverClientId)
        .setAutoSelectEnabled(true)
        .build()
      val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()
      val credentialManager = CredentialManager.create(activityContext)
      val result = credentialManager.getCredential(
        context = activityContext,
        request = request
      )
      handleCredentialResponse(result.credential, auth)
    } catch (e: CancellationException) {
      throw e
    } catch (_: GetCredentialCancellationException) {
      AuthUiResult.Cancelled
    } catch (_: NoCredentialException) {
      AuthUiResult.Cancelled
    } catch (_: GetCredentialException) {
      AuthUiResult.Cancelled
    }
  }

  private suspend fun handleCredentialResponse(
    credential: androidx.credentials.Credential,
    auth: FirebaseAuth
  ): AuthUiResult {
    if (credential is CustomCredential &&
      credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
    ) {
      val googleToken = GoogleIdTokenCredential.createFrom(credential.data)
      val firebaseCredential = GoogleAuthProvider.getCredential(googleToken.idToken, null)
      val authResult = auth.signInWithCredential(firebaseCredential).await()
      val user = authResult.user
      return if (user != null) {
        AuthUiResult.Success(user)
      } else {
        AuthUiResult.Error("Firebase authentication returned no active user.")
      }
    }
    return AuthUiResult.Error("Unsupported credential type returned from Google Credential Manager.")
  }

  /**
   * Detaches all active Firestore listeners first inside NonCancellable, clears Credential Manager
   * session state, then signs out of FirebaseAuth so sign-out cannot race into PERMISSION_DENIED.
   */
  suspend fun signOutSafely(
    context: Context,
    onBeforeSignOutCleanup: suspend () -> Unit,
    auth: FirebaseAuth = FirebaseAuth.getInstance()
  ) {
    withContext(NonCancellable) {
      runCatching { onBeforeSignOutCleanup() }
      runCatching {
        CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
      }
      auth.signOut()
    }
  }
}
