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
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class GitHubAuthManager(context: Context) {
    private val authService = AuthorizationService(context)
    private val serviceConfig = AuthorizationServiceConfiguration(
        "https://github.com/login/oauth/authorize".toUri(),
        "https://github.com/login/oauth/access_token".toUri()
    )
    private val client = OkHttpClient()

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
        Log.d("RupoopAuth", "Exchanging code for token via Proxy")
        
        val code = response.authorizationCode
        if (code == null) {
            continuation.resumeWithException(Exception("Authorization code is null"))
            return@suspendCancellableCoroutine
        }

        val json = JSONObject()
        json.put("code", code)
        val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

        // Use the Cloudflare Proxy instead of directly hardcoding the secret in the app
        val request = Request.Builder()
            .url(BuildConfig.PROXY_URL + "auth/token")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("RupoopAuth", "Token exchange failed via Proxy", e)
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    val ex = Exception("Proxy returned HTTP ${response.code}: ${response.body?.string()}")
                    Log.e("RupoopAuth", "Token exchange HTTP Error", ex)
                    continuation.resumeWithException(ex)
                    return
                }

                try {
                    val respBody = response.body?.string()
                    val jsonResponse = JSONObject(respBody ?: "{}")
                    val token = jsonResponse.optString("access_token", "")
                    if (token.isNotEmpty()) {
                        Log.d("RupoopAuth", "Token received from Proxy")
                        continuation.resume(token)
                    } else {
                        throw Exception("Token is missing in Proxy response. Response: $respBody")
                    }
                } catch (e: Exception) {
                    Log.e("RupoopAuth", "Failed to parse Proxy token response", e)
                    continuation.resumeWithException(e)
                }
            }
        })
    }
}
