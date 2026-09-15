package com.nikibonev.tempo.data.repository

import android.app.Activity
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.nikibonev.tempo.data.model.AccountState
import com.nikibonev.tempo.util.awaitResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AuthRepository(private val context: Context) {
    private val firebaseApp: FirebaseApp? = FirebaseApp.getApps(context).firstOrNull()
    private val auth: FirebaseAuth? = firebaseApp?.let { FirebaseAuth.getInstance(it) }
    private val credentialManager = CredentialManager.create(context)

    private val _state = MutableStateFlow(currentState())
    val state: StateFlow<AccountState> = _state.asStateFlow()

    init {
        auth?.addAuthStateListener { _state.value = currentState() }
    }

    fun isFirebaseConfigured(): Boolean = firebaseApp != null
    fun firebaseAppOrNull(): FirebaseApp? = firebaseApp

    suspend fun signInWithEmail(email: String, password: String): Result<Unit> = authAction {
        require(email.isNotBlank()) { "Enter your email address." }
        require(password.isNotBlank()) { "Enter your password." }
        requireAuth().signInWithEmailAndPassword(email.trim(), password).awaitResult()
    }

    suspend fun createAccount(email: String, password: String): Result<Unit> = authAction {
        require(email.isNotBlank()) { "Enter your email address." }
        require(password.length >= 6) { "Use at least 6 characters for your password." }
        val result = requireAuth().createUserWithEmailAndPassword(email.trim(), password).awaitResult()
        runCatching { result.user?.sendEmailVerification()?.awaitResult() }
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> = authAction {
        require(email.isNotBlank()) { "Enter your email address first." }
        requireAuth().sendPasswordResetEmail(email.trim()).awaitResult()
        _state.value = currentState().copy(message = "Password reset email sent.")
    }

    suspend fun signInWithGoogle(activity: Activity): Result<Unit> = authAction {
        val auth = requireAuth()
        val clientId = resolveWebClientId()
            ?: error("Google sign-in is not configured yet. Add your Firebase google-services.json first.")

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(true)
            .setServerClientId(clientId)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
        val result = CredentialManager.create(activity).getCredential(activity, request)
        val credential = result.credential
        require(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "Google returned an unsupported credential type."
        }
        val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
        val firebaseCredential = GoogleAuthProvider.getCredential(googleCredential.idToken, null)
        auth.signInWithCredential(firebaseCredential).awaitResult()
    }

    suspend fun signOut(): Result<Unit> = runCatching {
        auth?.signOut()
        runCatching { credentialManager.clearCredentialState(ClearCredentialStateRequest()) }
        _state.value = currentState().copy(message = "Signed out. Local guest data remains on this device.")
    }

    suspend fun deleteFirebaseAccount(): Result<Unit> = authAction {
        val user = requireAuth().currentUser ?: error("You are not signed in.")
        user.delete().awaitResult()
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }

    private suspend fun authAction(block: suspend () -> Unit): Result<Unit> {
        _state.value = _state.value.copy(busy = true, message = null)
        val result = runCatching { block() }
        _state.value = currentState().copy(
            busy = false,
            message = result.exceptionOrNull()?.friendlyAuthMessage(),
        )
        return result
    }

    private fun currentState(): AccountState {
        val user = auth?.currentUser
        return AccountState(
            uid = user?.uid,
            email = user?.email,
            displayName = user?.displayName,
            photoUrl = user?.photoUrl?.toString(),
            firebaseAvailable = firebaseApp != null,
        )
    }

    private fun resolveWebClientId(): String? {
        val id = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        if (id == 0) return null
        return context.getString(id).takeIf { it.isNotBlank() && !it.contains("YOUR_", ignoreCase = true) }
    }

    private fun requireAuth(): FirebaseAuth = auth ?: error(
        "Cloud accounts are not configured in this build. You can keep using Tempo locally, or add Firebase configuration to enable sign-in.",
    )

    private fun Throwable.friendlyAuthMessage(): String {
        val raw = localizedMessage.orEmpty()
        return when {
            raw.contains("password", ignoreCase = true) && raw.contains("invalid", ignoreCase = true) -> "That email/password combination did not work."
            raw.contains("network", ignoreCase = true) -> "Network error. Your local tracking data is safe; try sign-in again when connected."
            raw.contains("credential", ignoreCase = true) && raw.contains("cancel", ignoreCase = true) -> "Google sign-in was cancelled."
            raw.isNotBlank() -> raw
            else -> "Authentication failed. Please try again."
        }
    }
}
