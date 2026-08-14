package com.santiago43rus.rupoop.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.NorthWest
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.santiago43rus.rupoop.components.DeleteConfirmationDialog
import com.santiago43rus.rupoop.parser.UniversalVideoParser

@Composable
fun SearchSuggestionsOverlay(
    isSearchExpanded: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchSuggestions: List<String>,
    searchHistory: List<String>,
    onPerformSearch: (String) -> Unit,
    onRemoveSearchQuery: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    isGitHubAuthenticated: Boolean = false
) {
    if (!isSearchExpanded) return

    var queryToDelete by remember { mutableStateOf<String?>(null) }
    val isUrl = UniversalVideoParser.isHttpUrl(searchQuery)

    if (searchQuery.isNotEmpty()) {
        Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background.copy(alpha = 0.98f)) {
            LazyColumn {
                if (isUrl) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPerformSearch(searchQuery) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.PlayCircleFilled,
                                null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Воспроизвести по ссылке",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = searchQuery,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    }
                }
                items(searchSuggestions) { suggestion ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Search,
                            null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = suggestion,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onPerformSearch(suggestion) },
                            fontSize = 14.sp
                        )
                        IconButton(
                            onClick = { onSearchQueryChange(suggestion) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.NorthWest,
                                null,
                                tint = Color.Gray,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    } else if (searchHistory.isNotEmpty()) {
        Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background.copy(alpha = 0.98f)) {
            LazyColumn {
                items(searchHistory) { query ->
                    val isHistoryUrl = UniversalVideoParser.isHttpUrl(query)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (isHistoryUrl) Icons.Default.Link else Icons.Default.History,
                            null,
                            tint = if (isHistoryUrl) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = query,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onPerformSearch(query) },
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        IconButton(
                            onClick = { queryToDelete = query },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                null,
                                tint = Color.Gray,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    queryToDelete?.let { query ->
        DeleteConfirmationDialog(
            title = "Удалить из истории поиска",
            message = "Вы уверены, что хотите удалить «$query» из истории поиска?",
            showGistCheckbox = isGitHubAuthenticated,
            onConfirm = { deleteFromGist ->
                onRemoveSearchQuery(query, deleteFromGist)
                queryToDelete = null
            },
            onDismiss = { queryToDelete = null }
        )
    }
}
