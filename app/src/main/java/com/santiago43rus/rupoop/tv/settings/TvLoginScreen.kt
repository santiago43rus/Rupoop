package com.santiago43rus.rupoop.tv.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.santiago43rus.rupoop.data.DeviceCodeRequest
import com.santiago43rus.rupoop.network.RetrofitClient
import com.santiago43rus.rupoop.util.generateQrCodeBitmap
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
    val colorScheme = MaterialTheme.colorScheme

    var userCode by remember { mutableStateOf<String?>(null) }
    var verificationUri by remember { mutableStateOf<String?>(null) }
    var statusText by remember { mutableStateOf("Получение кода авторизации...") }
    var isPolling by remember { mutableStateOf(false) }

    val qrTargetUrl = "https://github.com/login/device/select_account"
    val qrBitmap = remember { generateQrCodeBitmap(qrTargetUrl, 380) }

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
                                // Ожидание
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

    val firstButtonFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        firstButtonFocusRequester.requestFocus()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 840.dp)
                .background(colorScheme.surface, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(
                    text = "Вход в аккаунт GitHub",
                    color = colorScheme.onSurface,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // QR код для сканирования камерой
                    qrBitmap?.let { bmp ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(Color.White, RoundedCornerShape(12.dp))
                            ) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "QR-код для входа",
                                    modifier = Modifier
                                        .size(160.dp)
                                        .padding(8.dp)
                                )
                            }
                            Text(
                                text = "Отсканируйте камерой",
                                color = colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Откройте страницу на телефоне или компьютере:\n${verificationUri ?: "https://github.com/login/device/select_account"}",
                            color = colorScheme.primary,
                            fontSize = 15.sp
                        )

                        if (userCode != null) {
                            Text(
                                text = "И введите одноразовый код:",
                                color = colorScheme.onSurfaceVariant,
                                fontSize = 14.sp
                            )
                            Box(
                                modifier = Modifier
                                    .background(colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                            ) {
                                Text(
                                    text = userCode ?: "",
                                    color = colorScheme.primary,
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 4.sp,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                                )
                            }
                        }

                        Text(
                            text = statusText,
                            color = colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(
                        onClick = { startDeviceFlow() },
                        colors = ButtonDefaults.colors(
                            containerColor = colorScheme.primary,
                            contentColor = colorScheme.onPrimary,
                            focusedContainerColor = colorScheme.primary,
                            focusedContentColor = colorScheme.onPrimary
                        ),
                        modifier = Modifier.focusRequester(firstButtonFocusRequester)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Обновить код")
                    }

                    Button(
                        onClick = onBack,
                        colors = ButtonDefaults.colors(
                            containerColor = colorScheme.surfaceVariant,
                            contentColor = colorScheme.onSurface,
                            focusedContainerColor = colorScheme.primary,
                            focusedContentColor = colorScheme.onPrimary
                        )
                    ) {
                        Text("Назад")
                    }
                }
            }
        }
    }
}
