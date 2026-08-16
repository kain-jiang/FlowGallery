package com.flowgallery.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowgallery.app.R
import com.flowgallery.app.data.model.LogEntry
import com.flowgallery.app.viewmodel.GalleryViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Logs tab — error / connection history for diagnostics. */
@Composable
fun LogsScreen(viewModel: GalleryViewModel) {
    val state by viewModel.uiState.collectAsState()
    val scheme = MaterialTheme.colorScheme

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.tab_logs),
                style = MaterialTheme.typography.titleLarge,
                color = scheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            // Clear logs
            IconButton(onClick = viewModel::clearLogs) {
                Icon(
                    Icons.Filled.DeleteSweep,
                    contentDescription = stringResource(R.string.logs_clear),
                    tint = scheme.onSurfaceVariant
                )
            }
        }

        if (state.logs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.logs_empty),
                    color = scheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, end = 16.dp, bottom = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.logs, key = { it.time }) { log ->
                    LogRow(log)
                }
            }
        }
    }
}

@Composable
private fun LogRow(log: LogEntry) {
    val scheme = MaterialTheme.colorScheme
    val time = rememberTimeFormat(log.time)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(scheme.surface)
            .padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = time,
            color = scheme.onSurfaceVariant,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 2.dp)
        )
        Spacer(Modifier.size(10.dp))
        Text(
            text = log.message,
            color = scheme.onSurface,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun rememberTimeFormat(time: Long): String {
    val fmt = remember(time) {
        SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())
    }
    return fmt.format(Date(time))
}
