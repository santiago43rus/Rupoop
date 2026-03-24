package com.santiago43rus.rupoop.auth

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.net.toUri
import com.santiago43rus.rupoop.BuildConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import net.openid.appauth.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class GitHubAuthManager(context: Context) {
    private val authService = AuthorizationService(context)
    private val serviceConfig = AuthorizationServiceConfiguration(
        "https://github.com/login/oauth/authorize".toUri(),
        "https://github.com/login/oauth/access_token".toUri()
    )

    fun createAuthIntent(): Intent {
        Log.d("RupoopAuth", "Starting auth request")
        val authRequest = AuthorizationRequest.Builder(
            serviceConfig,
            BuildConfig.GH_CLIENT_ID,
            ResponseTypeValues.CODE,
            "rupoop://auth".toUri()
        ).setScopes("gist").build()
        return authService.getAuthorizationRequestIntent(authRequest)
    }

    suspend fun exchangeCodeForToken(response: AuthorizationResponse): String = suspendCancellableCoroutine { continuation ->
        Log.d("RupoopAuth", "Exchanging code for token")
        val clientSecret = BuildConfig.GH_CLIENT_SECRET
        val clientAuthentication: ClientAuthentication = ClientSecretPost(clientSecret)
        
        val tokenRequest = response.createTokenExchangeRequest()

        authService.performTokenRequest(tokenRequest, clientAuthentication) { tokenResponse, ex ->
            if (tokenResponse != null) {
                val token = tokenResponse.accessToken
                Log.d("RupoopAuth", "Token received")
                if (token != null) {
                    continuation.resume(token)
                } else {
                    continuation.resumeWithException(Exception("Token is null"))
                }
            } else {
                Log.e("RupoopAuth", "Token exchange failed", ex)
                continuation.resumeWithException(ex ?: Exception("Unknown error"))
            }
        }
    }
}
