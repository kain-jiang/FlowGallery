package com.flowgallery.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.flowgallery.app.R
import com.flowgallery.app.data.source.SourceType

/**
 * Source picker shown before adding a folder. Only LOCAL is implemented;
 * external sources toast "in development".
 */
@Composable
fun SourcePickerDialog(
    onPickLocal: () -> Unit,
    onPickExternal: (SourceType) -> Unit,
    onDismiss: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val sources = listOf(
        SourceType.LOCAL to Icons.Filled.Storage,
        SourceType.SMB to Icons.Filled.Lan,
        SourceType.FTP to Icons.Filled.Cloud,
        SourceType.SFTP to Icons.Filled.Lock,
        SourceType.WEBDAV to Icons.Filled.CloudQueue
    )

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(scheme.surface)
                .padding(24.dp)
        ) {
            // Drag handle
            Box(
                modifier = Modifier
                    .size(width = 40.dp, height = 4.dp)
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(2.dp))
                    .background(scheme.outline)
            )
            Spacer(Modifier.height(20.dp))

            Text(
                text = stringResource(R.string.source_picker_title),
                style = MaterialTheme.typography.titleLarge,
                color = scheme.onSurface
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.source_picker_subtitle),
                color = scheme.onSurfaceVariant,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(16.dp))

            sources.forEach { (type, icon) ->
                SourceRow(
                    icon = icon,
                    label = stringResource(sourceLabel(type)),
                    tint = if (type == SourceType.LOCAL) scheme.onSurface
                    else scheme.primary,
                    onClick = {
                        if (type == SourceType.LOCAL) onPickLocal()
                        else onPickExternal(type)
                    }
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun SourceRow(
    icon: ImageVector,
    label: String,
    tint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(scheme.surfaceVariant.copy(alpha = 0.6f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            color = scheme.onSurface,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = if (tint == scheme.onSurface) "" else "…",
            color = scheme.onSurfaceVariant,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun sourceLabel(type: SourceType): Int = when (type) {
    SourceType.LOCAL -> R.string.source_local
    SourceType.SMB -> R.string.source_smb
    SourceType.FTP -> R.string.source_ftp
    SourceType.SFTP -> R.string.source_sftp
    SourceType.WEBDAV -> R.string.source_webdav
}
