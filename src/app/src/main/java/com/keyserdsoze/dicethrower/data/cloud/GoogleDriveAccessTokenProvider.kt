package com.keyserdsoze.dicethrower.data.cloud

import android.accounts.Account
import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class GoogleDriveAccessTokenProvider(
    context: Context,
    private val accountEmail: () -> String?,
) : DriveAccessTokenProvider {
    private val authorizationClient = Identity.getAuthorizationClient(context.applicationContext)
    private val driveScope = Scope(DRIVE_APPDATA_SCOPE)

    override suspend fun accessToken(): String = suspendCoroutine { continuation ->
        val email = accountEmail()?.takeIf(String::isNotBlank)
        if (email == null) {
            continuation.resumeWithException(
                CloudAuthorizationException("No Google account is connected for Drive app-data access"),
            )
            return@suspendCoroutine
        }
        val request = AuthorizationRequest.builder()
            .setAccount(Account(email, GOOGLE_ACCOUNT_TYPE))
            .setRequestedScopes(listOf(driveScope))
            .build()
        authorizationClient.authorize(request)
            .addOnSuccessListener { result ->
                val token = result.accessToken
                when {
                    result.hasResolution() -> continuation.resumeWithException(
                        CloudAuthorizationException("Google Drive app-data authorization requires user action"),
                    )
                    token.isNullOrBlank() -> continuation.resumeWithException(
                        CloudAuthorizationException("Google Drive did not return an access token"),
                    )
                    else -> continuation.resume(token)
                }
            }
            .addOnFailureListener { error ->
                continuation.resumeWithException(
                    CloudAuthorizationException("Unable to authorize Google Drive app-data access", error),
                )
            }
    }

    private companion object {
        const val GOOGLE_ACCOUNT_TYPE = "com.google"
    }
}
