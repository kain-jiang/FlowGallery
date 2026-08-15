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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.flowgallery.app.R
import com.flowgallery.app.data.model.Folder
import com.flowgallery.app.ui.theme.Accent
import com.flowgallery.app.ui.theme.AccentMuted
import com.flowgallery.app.ui.theme.FgSecondary
import com.flowgallery.app.ui.theme.Muted
import com.flowgallery.app.ui.theme.Surface
import com.flowgallery.app.ui.theme.Surface2
import com.flowgallery.app.ui.theme.Surface3

/** Bottom-sheet style folder selection modal (mirrors the prototype). */
@Composable
fun FolderSelectionModal(
    folders: List<Folder>,
    onToggle: (Long) -> Unit,
    onAddFolder: () -> Unit,
    onAddSmb: () -> Unit,
    onClose: () -> Unit
) {
    Dialog(onDismissRequest = onClose) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Drag handle
            Box(
                modifier = Modifier
                    .size(width = 40.dp, height = 4.dp)
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.outline)
            )
            Spacer(Modifier.height(20.dp))

            Text(
                text = stringResource(R.string.select_folders),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(16.dp))

            folders.forEach { folder ->
                FolderRow(
                    folder = folder,
                    onClick = { onToggle(folder.id) }
                )
                Spacer(Modifier.height(8.dp))
            }

            // Add new folder
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable(onClick = onAddFolder)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.add_new_folder),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(8.dp))

            // Add SMB network share
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(onClick = onAddSmb)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Lan, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.add_smb_share),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun FolderRow(folder: Folder, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (folder.isSelected) scheme.primaryContainer else scheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(scheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.FolderOpen,
                contentDescription = null,
                tint = scheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = folder.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface
            )
            Text(
                text = stringResource(R.string.image_count, folder.imageCount),
                fontSize = 12.sp,
                color = scheme.onSurfaceVariant
            )
        }
        // Check indicator
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (folder.isSelected) scheme.primary else scheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (folder.isSelected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = stringResource(R.string.cd_selected),
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
