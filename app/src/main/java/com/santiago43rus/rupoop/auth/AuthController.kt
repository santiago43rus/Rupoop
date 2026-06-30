package com.santiago43rus.rupoop.auth

import android.content.Intent
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.santiago43rus.rupoop.data.GitHubUser
import com.santiago43rus.rupoop.data.SettingsManager
import com.santiago43rus.rupoop.data.UserRegistry
import com.santiago43rus.rupoop.network.RetrofitClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AuthController(
    private val scope: CoroutineScope,
    private val settingsManager: SettingsManager,
    private val authManager: GitHubAuthManager,
    private val syncManager: GistSyncManager,
    private val snackbarMessage: MutableSharedFlow<String>,
    private val onRegistryUpdate: (UserRegistry) -> Unit,
    private val loadHome: (Boolean) -> Unit,
    private val getRegistry: () -> UserRegistry
) {

    // ── Auth state ──
    var isAuthenticating by mutableStateOf(false)
    var isAuthenticated by mutableStateOf(settingsManager.accessToken != null)
    var githubUser by mutableStateOf<GitHubUser?>(null)
    var isAccountMenuExpanded by mutableStateOf(false)

    // ── Initial load / auth ──
    fun initializeApp() {
        val savedToken = settingsManager.accessToken
        if (savedToken != null) {
            scope.launch {
                isAuthenticating = true
                try {
                    val authHeader = "Bearer $savedToken"
                    githubUser = withContext(Dispatchers.IO) { RetrofitClient.gitHubApi.getUser(authHeader) }

                    val now = System.currentTimeMillis()
                    val lastSync = settingsManager.lastSyncTime
                    val freqMs = settingsManager.syncFrequencyHours * 3600000L
                    if (now - lastSync > freqMs || getRegistry().watchHistory.isEmpty()) {
                        val syncedRegistry = withContext(Dispatchers.IO) { syncManager.sync(savedToken) }
                        onRegistryUpdate(syncedRegistry)
                        settingsManager.lastSyncTime = now
                    } else {
                        onRegistryUpdate(getRegistry())
                    }
                    isAuthenticated = true
                } catch (e: Exception) {
                    Log.e("RupoopAuth", "Auth error", e)
                } finally {
                    isAuthenticating = false
                }
            }
        }
    }

    // ── Auth response processing ──
    fun processAuthResponse(response: net.openid.appauth.AuthorizationResponse) {
        scope.launch {
            isAuthenticating = true
            try {
                val token = authManager.exchangeCodeForToken(response)
                settingsManager.accessToken = token
                val authHeader = "Bearer $token"
                githubUser = withContext(Dispatchers.IO) { RetrofitClient.gitHubApi.getUser(authHeader) }
                val syncedRegistry = withContext(Dispatchers.IO) { syncManager.sync(token) }
                onRegistryUpdate(syncedRegistry)
                isAuthenticated = true
            } catch (e: Exception) {
                Log.e("RupoopAuth", "Auth processes error", e)
                snackbarMessage.emit("Ошибка авторизации: ${e.localizedMessage}")
            } finally {
                isAuthenticating = false
                loadHome(false)
            }
        }
    }

    fun onAuthSuccess(token: String) {
        scope.launch {
            isAuthenticating = true
            try {
                settingsManager.accessToken = token
                val authHeader = "Bearer $token"
                githubUser = withContext(Dispatchers.IO) { RetrofitClient.gitHubApi.getUser(authHeader) }
                val syncedRegistry = withContext(Dispatchers.IO) { syncManager.sync(token) }
                onRegistryUpdate(syncedRegistry)
                isAuthenticated = true
            } catch (e: Exception) {
                Log.e("RupoopAuth", "Sync error", e)
            } finally {
                isAuthenticating = false
                loadHome(false)
            }
        }
    }

    fun logout() {
        settingsManager.clearAuth()
        isAuthenticated = false
        githubUser = null
    }
}
