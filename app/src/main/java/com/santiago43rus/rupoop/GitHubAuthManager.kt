package com.santiago43rus.rupoop

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import net.openid.appauth.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class GitHubAuthManager(private val context: Context) {
    private val authService = AuthorizationService(context)
    private val serviceConfig = AuthorizationServiceConfiguration(
        Uri.parse("https://github.com/login/oauth/authorize"),
        Uri.parse("https://github.com/login/oauth/access_token")
    )

    fun createAuthIntent(): Intent {
        Log.d("RupoopAuth", "Starting auth request")
        val authRequest = AuthorizationRequest.Builder(
            serviceConfig,
            BuildConfig.GH_CLIENT_ID,
            ResponseTypeValues.CODE,
            Uri.parse("rupoop://auth")
        ).setScopes("gist").build()
        return authService.getAuthorizationRequestIntent(authRequest)
    }

    suspend fun exchangeCodeForToken(response: AuthorizationResponse): String = suspendCoroutine { continuation ->
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
