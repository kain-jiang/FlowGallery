package com.flowgallery.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.flowgallery.app.data.source.SourceType

/**
 * Shared source-type icon mapping — used by the home folder picker AND the
 * settings list badge so both stay consistent.
 */
fun sourceBadgeIcon(source: SourceType): ImageVector = when (source) {
    SourceType.LOCAL -> Icons.Filled.Storage
    SourceType.SMB -> Icons.Filled.Lan
    SourceType.FTP -> Icons.Filled.Cloud
    SourceType.SFTP -> Icons.Filled.Lock
    SourceType.WEBDAV -> Icons.Filled.CloudQueue
}

/**
 * Shared folder-TYPE icon mapping (normal / pack) — settings badge, the
 * type-selection dialog and anywhere else showing a folder type use this so
 * they never drift apart. NOTE: GridView is reserved for the home "All"
 * filter, so PACK keeps Collections.
 */
fun folderTypeIcon(type: com.flowgallery.app.data.model.FolderType): ImageVector =
    if (type == com.flowgallery.app.data.model.FolderType.PACK) {
        Icons.Filled.Collections
    } else {
        Icons.Filled.Folder
    }

/**
 * Small round badge pinned to a corner of an icon. Local = neutral,
 * external sources = accent.
 */
@Composable
fun SourceTypeBadge(
    source: SourceType,
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
    iconSize: Dp = 11.dp
) {
    val isLocal = source == SourceType.LOCAL
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                if (isLocal) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.primary
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = sourceBadgeIcon(source),
            contentDescription = source.label,
            tint = if (isLocal) MaterialTheme.colorScheme.onSurfaceVariant else Color.White,
            modifier = Modifier.size(iconSize)
        )
    }
}
