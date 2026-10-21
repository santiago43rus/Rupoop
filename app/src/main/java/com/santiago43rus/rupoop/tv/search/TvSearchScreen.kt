package com.santiago43rus.rupoop.tv.search

import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.santiago43rus.rupoop.data.SearchResult
import com.santiago43rus.rupoop.data.SearchSource
import com.santiago43rus.rupoop.tv.components.TvRow

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvSearchScreen(
    searchQuery: String,
    selectedSearchSource: SearchSource,
    searchResults: List<SearchResult>,
    searchSuggestions: List<String>,
    searchHistory: List<String>,
    isSearching: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onSearchSourceSelect: (SearchSource) -> Unit,
    onPerformSearch: (String) -> Unit,
    onRemoveSearchQuery: (String) -> Unit,
    onClearSearchHistory: () -> Unit,
    onVideoSelected: (SearchResult, List<SearchResult>) -> Unit,
    onVideoMenuClick: (SearchResult) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val isSpeechAvailable = remember(context) { SpeechRecognizer.isRecognitionAvailable(context) }
    var queryText by remember(searchQuery) { mutableStateOf(searchQuery) }
    val focusManager = LocalFocusManager.current
    val searchFocusRequester = remember { FocusRequester() }

    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!spokenText.isNullOrBlank()) {
            queryText = spokenText
            onSearchQueryChange(spokenText)
            onPerformSearch(spokenText)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Поле ввода поискового запроса для ТВ
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = queryText,
                onValueChange = {
                    queryText = it
                    onSearchQueryChange(it)
                },
                label = { Text("Поиск видео", color = colorScheme.onSurfaceVariant) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        focusManager.clearFocus()
                        onPerformSearch(queryText)
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = colorScheme.onSurface,
                    unfocusedTextColor = colorScheme.onSurface,
                    focusedBorderColor = colorScheme.primary,
                    unfocusedBorderColor = colorScheme.border,
                    focusedLabelColor = colorScheme.primary,
                    unfocusedLabelColor = colorScheme.onSurfaceVariant
                ),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(searchFocusRequester)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && (event.key == Key.Enter || event.key == Key.NumPadEnter)) {
                            focusManager.clearFocus()
                            onPerformSearch(queryText)
                            true
                        } else false
                    }
            )

            // Кнопка поиска
            Button(
                onClick = {
                    focusManager.clearFocus()
                    onPerformSearch(queryText)
                },
                colors = ButtonDefaults.colors(
                    containerColor = colorScheme.surfaceVariant,
                    contentColor = colorScheme.onSurface,
                    focusedContainerColor = colorScheme.primary,
                    focusedContentColor = colorScheme.onPrimary
                )
            ) {
                Text("Искать")
            }

            // Голосовой ввод через SpeechRecognizer intent
            if (isSpeechAvailable) {
                IconButton(
                    onClick = {
                        try {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(RecognizerIntent.EXTRA_PROMPT, "Скажите поисковый запрос")
                            }
                            speechLauncher.launch(intent)
                        } catch (_: Exception) {}
                    },
                    colors = IconButtonDefaults.colors(
                        containerColor = colorScheme.surfaceVariant,
                        contentColor = colorScheme.onSurface,
                        focusedContainerColor = colorScheme.primary,
                        focusedContentColor = colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Default.Mic, contentDescription = "Голосовой поиск")
                }
            }
        }

        // Фильтры по источникам (Rutube, VK, OK, Lordfilm)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .focusRestorer(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SearchSource.entries.forEach { source ->
                val isSelected = selectedSearchSource == source
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        onSearchSourceSelect(source)
                        if (queryText.isNotBlank()) {
                            onPerformSearch(queryText)
                        }
                    },
                    colors = FilterChipDefaults.colors(
                        containerColor = colorScheme.surfaceVariant,
                        contentColor = colorScheme.onSurface,
                        focusedContainerColor = colorScheme.primary,
                        focusedContentColor = colorScheme.onPrimary,
                        selectedContainerColor = colorScheme.surfaceVariant,
                        selectedContentColor = colorScheme.primary,
                        focusedSelectedContainerColor = colorScheme.primary,
                        focusedSelectedContentColor = colorScheme.onPrimary
                    )
                ) {
                    Text(
                        text = source.displayName,
                        fontSize = 14.sp
                    )
                }
            }
        }

        // Подсказки и история поиска
        if (searchResults.isEmpty() && !isSearching) {
            if (queryText.isNotBlank() && searchSuggestions.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Подсказки", color = colorScheme.onSurfaceVariant, fontSize = 15.sp)
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRestorer(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(searchSuggestions) { suggestion ->
                            Surface(
                                onClick = {
                                    queryText = suggestion
                                    onSearchQueryChange(suggestion)
                                    onPerformSearch(suggestion)
                                },
                                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = colorScheme.surfaceVariant,
                                    contentColor = colorScheme.onSurface,
                                    focusedContainerColor = colorScheme.primary,
                                    focusedContentColor = colorScheme.onPrimary
                                )
                            ) {
                                Text(
                                    text = suggestion,
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            } else if (searchHistory.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("История поиска", color = colorScheme.onSurfaceVariant, fontSize = 15.sp)
                        Button(
                            onClick = onClearSearchHistory,
                            colors = ButtonDefaults.colors(
                                containerColor = colorScheme.surfaceVariant,
                                contentColor = colorScheme.onSurface,
                                focusedContainerColor = colorScheme.primary,
                                focusedContentColor = colorScheme.onPrimary
                            )
                        ) {
                            Text("Очистить историю", fontSize = 13.sp)
                        }
                    }
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRestorer(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(searchHistory) { historyItem ->
                            Surface(
                                onClick = {
                                    queryText = historyItem
                                    onSearchQueryChange(historyItem)
                                    onPerformSearch(historyItem)
                                },
                                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = colorScheme.surfaceVariant,
                                    contentColor = colorScheme.onSurface,
                                    focusedContainerColor = colorScheme.primary,
                                    focusedContentColor = colorScheme.onPrimary
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        Icons.Default.History,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = historyItem,
                                        fontSize = 14.sp
                                    )
                                    IconButton(
                                        onClick = { onRemoveSearchQuery(historyItem) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Clear, contentDescription = "Удалить", modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Результаты поиска
        if (isSearching) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Поиск видео...", color = colorScheme.onSurfaceVariant, fontSize = 16.sp)
            }
        } else if (searchResults.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .focusRestorer(),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                val chunkedResults = searchResults.chunked(4)
                items(chunkedResults) { rowVideos ->
                    TvRow(
                        title = "",
                        videos = rowVideos,
                        onVideoClick = { onVideoSelected(it, searchResults) },
                        onVideoMenuClick = onVideoMenuClick
                    )
                }
            }
        } else if (queryText.isNotBlank()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Ничего не найдено", color = colorScheme.onSurfaceVariant, fontSize = 16.sp)
            }
        }
    }
}
