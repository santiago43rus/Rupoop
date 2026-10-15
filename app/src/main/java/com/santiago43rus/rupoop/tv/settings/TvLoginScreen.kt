package com.santiago43rus.rupoop.tv.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.santiago43rus.rupoop.data.DeviceCodeRequest
import com.santiago43rus.rupoop.network.RetrofitClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvLoginScreen(
    onAuthSuccess: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    var userCode by remember { mutableStateOf<String?>(null) }
    var verificationUri by remember { mutableStateOf<String?>(null) }
    var statusText by remember { mutableStateOf("Получение кода авторизации...") }
    var isPolling by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    fun startDeviceFlow() {
        coroutineScope.launch {
            try {
                statusText = "Подключение к GitHub..."
                val codeResponse = RetrofitClient.deviceAuthApi.getDeviceCode()
                userCode = codeResponse.userCode
                verificationUri = codeResponse.verificationUri
                statusText = "Ожидание ввода кода на устройстве..."
                isPolling = true

                var interval = codeResponse.interval.coerceAtLeast(5)
                val deviceCode = codeResponse.deviceCode

                while (isActive && isPolling) {
                    delay(interval * 1000L)
                    try {
                        val tokenResponse = RetrofitClient.deviceAuthApi.getDeviceToken(DeviceCodeRequest(deviceCode))
                        val accessToken = tokenResponse.accessToken
                        if (!accessToken.isNullOrBlank()) {
                            statusText = "Успешная авторизация!"
                            isPolling = false
                            onAuthSuccess(accessToken)
                            onBack()
                            break
                        }

                        when (tokenResponse.error) {
                            "authorization_pending" -> {
                                // Пользователь ещё не завершил авторизацию
                            }
                            "slow_down" -> {
                                interval += 5
                            }
                            "expired_token" -> {
                                statusText = "Срок действия кода истек. Обновите код."
                                isPolling = false
                                break
                            }
                            "access_denied" -> {
                                statusText = "Доступ запрещен пользователем."
                                isPolling = false
                                break
                            }
                            else -> {
                                if (tokenResponse.error != null) {
                                    statusText = "Ошибка: ${tokenResponse.errorDescription ?: tokenResponse.error}"
                                    isPolling = false
                                    break
                                }
                            }
                        }
                    } catch (_: Exception) {
                        // Ошибка сети при очередном polling-запросе
                    }
                }
            } catch (e: Exception) {
                statusText = "Ошибка: ${e.localizedMessage ?: "Не удалось получить код"}"
                isPolling = false
            }
        }
    }

    LaunchedEffect(Unit) {
        startDeviceFlow()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F1115))
            .padding(48.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            onClick = {},
            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(16.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color(0xFF1E2228),
                focusedContainerColor = Color(0xFF1E2228)
            ),
            modifier = Modifier.widthIn(max = 680.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(36.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(
                    text = "Вход в аккаунт GitHub",
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Откройте страницу на телефоне или компьютере:\n${verificationUri ?: "https://github.com/login/device"}",
                    color = Color(0xFF58A6FF),
                    fontSize = 17.sp,
                    textAlign = TextAlign.Center
                )

                if (userCode != null) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "И введите одноразовый код:",
                            color = Color(0xFF8B949E),
                            fontSize = 15.sp
                        )
                        Surface(
                            onClick = {},
                            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = Color(0xFF282E38),
                                focusedContainerColor = Color(0xFF282E38)
                            )
                        ) {
                            Text(
                                text = userCode ?: "",
                                color = Color.White,
                                fontSize = 36.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 4.sp,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp)
                            )
                        }
                    }
                }

                Text(
                    text = statusText,
                    color = Color(0xFF8B949E),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(
                        onClick = { startDeviceFlow() },
                        colors = ButtonDefaults.colors(
                            containerColor = Color(0xFF238636),
                            focusedContainerColor = Color(0xFF2EA043)
                        )
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Обновить код", color = Color.White)
                    }

                    Button(
                        onClick = onBack,
                        colors = ButtonDefaults.colors(
                            containerColor = Color(0xFF21262D),
                            focusedContainerColor = Color(0xFF30363D)
                        )
                    ) {
                        Text("Назад", color = Color.White)
                    }
                }
            }
        }
    }
}
