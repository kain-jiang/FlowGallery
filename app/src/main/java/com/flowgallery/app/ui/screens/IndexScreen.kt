package com.flowgallery.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowgallery.app.R
import com.flowgallery.app.viewmodel.GalleryViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Index tab — manage the metadata index (run / pause / resume / cancel). */
@Composable
fun IndexScreen(viewModel: GalleryViewModel) {
    val job by viewModel.indexJob.collectAsState()
    val scheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.tab_index),
            style = MaterialTheme.typography.titleLarge,
            color = scheme.onSurface,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Status card
        IndexStatusCard(job = job, viewModel = viewModel)

        Spacer(Modifier.height(14.dp))

        // Folder selection card
        FolderSelectionCard(job = job, viewModel = viewModel)

        Spacer(Modifier.height(14.dp))

        // Info card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(scheme.surface)
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Bolt,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = stringResource(R.string.index_what_title),
                    color = scheme.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.index_what_body),
                color = scheme.onSurfaceVariant,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
        }
    }
}

@Composable
private fun IndexStatusCard(
    job: com.flowgallery.app.viewmodel.IndexJobState,
    viewModel: GalleryViewModel
) {
    val scheme = MaterialTheme.colorScheme
    val running = job.running
    val paused = job.paused

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.surface)
            .padding(16.dp)
    ) {
        // Status header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        when {
                            running && !paused -> scheme.primary
                            running -> scheme.tertiary
                            else -> scheme.outline
                        }
                    )
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = stringResource(
                    when {
                        running && !paused -> R.string.index_status_running
                        running -> R.string.index_status_paused
                        else -> R.string.index_status_idle
                    }
                ),
                color = scheme.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.weight(1f))
            if (running) {
                Text(
                    text = stringResource(
                        R.string.index_progress,
                        job.done, job.total
                    ),
                    color = scheme.primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Progress bar
        if (running) {
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = {
                    if (job.total > 0) job.done.toFloat() / job.total else 0f
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = scheme.primary,
                trackColor = scheme.surfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(
                    R.string.index_progress_detail,
                    (if (job.total > 0) job.done * 100 / job.total else 0),
                    job.extracted
                ),
                color = scheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        } else {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatCell(
                    label = stringResource(R.string.index_stat_entries),
                    value = "${job.entryCount}",
                    modifier = Modifier.weight(1f)
                )
                StatCell(
                    label = stringResource(R.string.index_stat_last),
                    value = if (job.lastIndexedAt > 0) {
                        rememberTime(job.lastIndexedAt)
                    } else stringResource(R.string.index_stat_never),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Actions
        Spacer(Modifier.height(16.dp))
        if (running) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ActionButton(
                    label = stringResource(
                        if (paused) R.string.index_resume else R.string.index_pause
                    ),
                    icon = if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    tint = scheme.primary,
                    onClick = viewModel::toggleIndexPause,
                    modifier = Modifier.weight(1f)
                )
                ActionButton(
                    label = stringResource(R.string.index_cancel),
                    icon = Icons.Filled.Cancel,
                    tint = scheme.error,
                    onClick = viewModel::cancelIndex,
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ActionButton(
                    label = stringResource(R.string.index_start),
                    icon = Icons.Filled.Bolt,
                    tint = scheme.primary,
                    onClick = { viewModel.startIndex(false) },
                    modifier = Modifier.weight(1f)
                )
                ActionButton(
                    label = stringResource(R.string.index_reindex),
                    icon = Icons.Filled.Refresh,
                    tint = scheme.tertiary,
                    onClick = { viewModel.startIndex(true) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun FolderSelectionCard(
    job: com.flowgallery.app.viewmodel.IndexJobState,
    viewModel: GalleryViewModel
) {
    val scheme = MaterialTheme.colorScheme
    val state by viewModel.uiState.collectAsState()
    // ALL folders are selectable — regardless of their enabled state.
    val folders = state.folders
    val selection = job.indexFolders // null = all

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.surface)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.index_folders_title),
                color = scheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            if (!job.running) {
                Text(
                    text = stringResource(
                        if (selection == null || selection.size == folders.size)
                            R.string.index_select_none
                        else R.string.index_select_all
                    ),
                    color = scheme.primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            // All-selected → tap clears; partial → tap selects all.
                            viewModel.setAllIndexFolders(
                                selection != null && selection.size != folders.size
                            )
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))

        if (folders.isEmpty()) {
            Text(
                text = stringResource(R.string.index_folders_empty),
                color = scheme.onSurfaceVariant,
                fontSize = 13.sp
            )
        } else {
            folders.forEach { folder ->
                val checked = selection == null || folder.id in selection
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(enabled = !job.running) {
                            viewModel.toggleIndexFolder(folder.id)
                        }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Custom check chip — purple rounded square + white tick
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (checked) scheme.primary
                                else scheme.surfaceVariant.copy(alpha = 0.7f)
                            )
                            .border(
                                width = 1.dp,
                                color = if (checked) scheme.primary
                                else scheme.outline.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(6.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (checked) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = androidx.compose.ui.graphics.Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Text(
                        text = folder.name,
                        color = scheme.onSurface,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 10.dp)
                    )
                    Text(
                        text = stringResource(
                            R.string.image_count, folder.imageCount
                        ),
                        color = scheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(scheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(12.dp)
    ) {
        Text(
            text = label,
            color = scheme.onSurfaceVariant,
            fontSize = 11.sp
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            color = scheme.onSurface,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(tint.copy(alpha = 0.15f))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.size(6.dp))
        Text(
            text = label,
            color = tint,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun rememberTime(time: Long): String {
    val fmt = remember(time) {
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    }
    return fmt.format(Date(time))
}
