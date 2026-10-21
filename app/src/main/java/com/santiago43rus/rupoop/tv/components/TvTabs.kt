package com.santiago43rus.rupoop.tv.components

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvTabs(
    tabs: List<String>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme

    TabRow(
        selectedTabIndex = selectedTabIndex,
        modifier = modifier
    ) {
        tabs.forEachIndexed { index, title ->
            val isSelected = selectedTabIndex == index
            Tab(
                selected = isSelected,
                onFocus = { onTabSelected(index) },
                onClick = { onTabSelected(index) },
                colors = TabDefaults.pillIndicatorTabColors(
                    contentColor = colorScheme.onSurfaceVariant,
                    inactiveContentColor = colorScheme.onSurfaceVariant,
                    selectedContentColor = colorScheme.primary,
                    focusedContentColor = colorScheme.onPrimary,
                    focusedSelectedContentColor = colorScheme.onPrimary
                )
            ) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                )
            }
        }
    }
}
