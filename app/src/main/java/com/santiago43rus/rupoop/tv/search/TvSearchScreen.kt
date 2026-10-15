package com.santiago43rus.rupoop.tv.search

import android.content.Context
import android.speech.SpeechRecognizer
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
import androidx.compose.ui.graphics.Color
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isSpeechAvailable = remember(context) { SpeechRecognizer.isRecognitionAvailable(context) }
    var queryText by remember(searchQuery) { mutableStateOf(searchQuery) }
    val focusManager = LocalFocusManager.current
    val searchFocusRequester = remember { FocusRequester() }

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
                label = { Text("Поиск видео", color = Color(0xFF8B949E)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        focusManager.clearFocus()
                        onPerformSearch(queryText)
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF58A6FF),
                    unfocusedBorderColor = Color(0xFF30363D)
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
                    containerColor = Color(0xFF238636),
                    focusedContainerColor = Color(0xFF2EA043)
                )
            ) {
                Text("Искать", color = Color.White)
            }

            // Голосовой ввод (показывается только если доступен на устройстве)
            if (isSpeechAvailable) {
                IconButton(
                    onClick = {
                        // Опциональный голосовой ввод
                    },
                    colors = IconButtonDefaults.colors(
                        containerColor = Color(0xFF21262D),
                        focusedContainerColor = Color(0xFF30363D)
                    )
                ) {
                    Icon(Icons.Default.Mic, contentDescription = "Голосовой поиск", tint = Color.White)
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
                        containerColor = Color(0xFF1E2228),
                        focusedContainerColor = Color(0xFF282E38),
                        selectedContainerColor = Color(0xFF58A6FF),
                        focusedSelectedContainerColor = Color(0xFF79B8FF)
                    )
                ) {
                    Text(
                        text = source.displayName,
                        color = if (isSelected) Color.Black else Color.White,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // Подсказки и история поиска (если строка не пустая и нет активных результатов)
        if (searchResults.isEmpty() && !isSearching) {
            if (queryText.isNotBlank() && searchSuggestions.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Подсказки", color = Color(0xFF8B949E), fontSize = 14.sp)
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
                                    containerColor = Color(0xFF1E2228),
                                    focusedContainerColor = Color(0xFF282E38)
                                )
                            ) {
                                Text(
                                    text = suggestion,
                                    color = Color.White,
                                    fontSize = 13.sp,
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
                        Text("История поиска", color = Color(0xFF8B949E), fontSize = 14.sp)
                        Button(
                            onClick = onClearSearchHistory,
                            colors = ButtonDefaults.colors(
                                containerColor = Color.Transparent,
                                focusedContainerColor = Color(0xFF21262D)
                            )
                        ) {
                            Text("Очистить историю", color = Color(0xFFDA3633), fontSize = 12.sp)
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
                                    containerColor = Color(0xFF1E2228),
                                    focusedContainerColor = Color(0xFF282E38)
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
                                        tint = Color(0xFF8B949E),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = historyItem,
                                        color = Color.White,
                                        fontSize = 13.sp
                                    )
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
                Text("Поиск видео...", color = Color.Gray, fontSize = 16.sp)
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
                        onVideoClick = { onVideoSelected(it, searchResults) }
                    )
                }
            }
        } else if (queryText.isNotBlank()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Ничего не найдено", color = Color.Gray, fontSize = 16.sp)
            }
        }
    }
}
