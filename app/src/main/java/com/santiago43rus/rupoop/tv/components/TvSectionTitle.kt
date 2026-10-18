package com.santiago43rus.rupoop.tv.components

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

@Composable
fun TvSectionTitle(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        color = MaterialTheme.colorScheme.onBackground,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(bottom = 12.dp)
    )
}
