package com.keyserdsoze.dicethrower

import android.accounts.Account
import android.app.Activity
import android.content.Intent
import android.content.MutableContextWrapper
import androidx.activity.result.IntentSenderRequest
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.keyserdsoze.dicethrower.data.GoogleAccountIdentity

enum class GoogleConnectionFailure {
    CONFIGURATION,
    SIGN_IN,
    DRIVE_AUTHORIZATION,
    DISCONNECT,
}

sealed interface GoogleSignInResult {
    data class Success(val account: GoogleAccountIdentity) : GoogleSignInResult
    data object Canceled : GoogleSignInResult
    data class Failure(val reason: GoogleConnectionFailure) : GoogleSignInResult
}

class GoogleAccountCoordinator(private val activity: Activity) {
    private val credentialManager = CredentialManager.create(activity)
    private val authorizationClient = Identity.getAuthorizationClient(activity)
    private val driveScope = Scope(DRIVE_APPDATA_SCOPE)

    suspend fun signIn(serverClientId: String): GoogleSignInResult {
        if (serverClientId.isBlank()) {
            return GoogleSignInResult.Failure(GoogleConnectionFailure.CONFIGURATION)
        }
        val option = GetSignInWithGoogleOption.Builder(serverClientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()
        return try {
            val credential = credentialManager.getCredential(
                request = request,
                context = MutableContextWrapper(activity),
            ).credential
            val custom = credential as? CustomCredential
                ?: return GoogleSignInResult.Failure(GoogleConnectionFailure.SIGN_IN)
            if (custom.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                return GoogleSignInResult.Failure(GoogleConnectionFailure.SIGN_IN)
            }
            val google = GoogleIdTokenCredential.createFrom(custom.data)
            val email = google.email
            if (email.isNullOrBlank()) {
                GoogleSignInResult.Failure(GoogleConnectionFailure.SIGN_IN)
            } else {
                GoogleSignInResult.Success(
                    GoogleAccountIdentity(
                        uniqueId = google.uniqueId,
                        email = email,
                        displayName = google.displayName,
                    ),
                )
            }
        } catch (_: GetCredentialCancellationException) {
            GoogleSignInResult.Canceled
        } catch (_: GetCredentialException) {
            GoogleSignInResult.Failure(GoogleConnectionFailure.SIGN_IN)
        } catch (_: GoogleIdTokenParsingException) {
            GoogleSignInResult.Failure(GoogleConnectionFailure.SIGN_IN)
        } catch (_: IllegalArgumentException) {
            GoogleSignInResult.Failure(GoogleConnectionFailure.SIGN_IN)
        }
    }

    fun requestDriveAuthorization(
        accountEmail: String,
        onAuthorized: () -> Unit,
        onResolution: (IntentSenderRequest) -> Unit,
        onFailure: () -> Unit,
    ) {
        val request = AuthorizationRequest.builder()
            .setAccount(Account(accountEmail, GOOGLE_ACCOUNT_TYPE))
            .setRequestedScopes(listOf(driveScope))
            .build()
        authorizationClient.authorize(request)
            .addOnSuccessListener { result ->
                if (result.hasResolution()) {
                    val pendingIntent = result.pendingIntent
                    if (pendingIntent == null) {
                        onFailure()
                    } else {
                        onResolution(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
                    }
                } else {
                    onAuthorized()
                }
            }
            .addOnFailureListener { onFailure() }
    }

    fun finishDriveAuthorization(data: Intent?): Boolean {
        if (data == null) return false
        return runCatching {
            authorizationClient.getAuthorizationResultFromIntent(data)
            true
        }.getOrDefault(false)
    }

    fun disconnect(
        accountEmail: String,
        onComplete: () -> Unit,
        onFailure: () -> Unit,
    ) {
        val request = RevokeAccessRequest.builder()
            .setAccount(Account(accountEmail, GOOGLE_ACCOUNT_TYPE))
            .setScopes(listOf(driveScope))
            .build()
        authorizationClient.revokeAccess(request)
            .addOnSuccessListener {
                onComplete()
            }
            .addOnFailureListener { onFailure() }
    }

    suspend fun clearCredentialSession() {
        runCatching {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        }
    }

    companion object {
        const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
        private const val GOOGLE_ACCOUNT_TYPE = "com.google"
    }
}
