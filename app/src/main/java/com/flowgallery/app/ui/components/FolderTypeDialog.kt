package com.flowgallery.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.flowgallery.app.data.model.FolderType

/**
 * Folder-type picker shown right after the SAF folder selection.
 * [recommended] is pre-selected; the user can switch to the other type.
 */
@Composable
fun FolderTypeDialog(
    folderName: String,
    recommended: FolderType,
    onConfirm: (FolderType) -> Unit,
    onDismiss: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    var selected by remember { mutableStateOf(recommended) }

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
                text = stringResource(R.string.folder_type_title),
                style = MaterialTheme.typography.titleLarge,
                color = scheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = folderName,
                color = scheme.onSurfaceVariant,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(16.dp))

            // NORMAL option
            TypeOption(
                icon = com.flowgallery.app.ui.components.folderTypeIcon(FolderType.NORMAL),
                title = stringResource(R.string.folder_type_normal),
                desc = stringResource(R.string.folder_type_normal_desc),
                isSelected = selected == FolderType.NORMAL,
                isRecommended = recommended == FolderType.NORMAL,
                onClick = { selected = FolderType.NORMAL }
            )
            Spacer(Modifier.height(10.dp))

            // PACK option
            TypeOption(
                icon = com.flowgallery.app.ui.components.folderTypeIcon(FolderType.PACK),
                title = stringResource(R.string.folder_type_pack),
                desc = stringResource(R.string.folder_type_pack_desc),
                isSelected = selected == FolderType.PACK,
                isRecommended = recommended == FolderType.PACK,
                onClick = { selected = FolderType.PACK }
            )

            Spacer(Modifier.height(20.dp))

            // Confirm
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(scheme.primaryContainer)
                    .clickable { onConfirm(selected) }
                    .padding(16.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.folder_type_ok),
                    color = scheme.primary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun TypeOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    desc: String,
    isSelected: Boolean,
    isRecommended: Boolean,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) scheme.primaryContainer else scheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (isSelected) scheme.primary else scheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (isSelected) Color.White else scheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    color = scheme.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (isRecommended) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(scheme.primary.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.folder_type_recommended),
                            color = scheme.primary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            Text(
                text = desc,
                color = scheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }
        if (isSelected) {
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = scheme.primary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
