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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.flowgallery.app.data.SmbClient
import com.flowgallery.app.data.model.FolderType
import com.flowgallery.app.data.model.SmbConfig
import kotlinx.coroutines.launch

/** Sentinel stored in testResult when the SMB test succeeded. */
private const val OK_MARKER = "OK"

/**
 * Add an SMB share: connection fields + "Test" button + type choice.
 * Matches the app's dialog style (drag handle, rounded surface, primary
 * action button).
 */
@Composable
fun SmbAddDialog(
    onConfirm: (SmbConfig, String?, FolderType) -> Unit,
    onDismiss: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    var host by remember { mutableStateOf("") }
    var share by remember { mutableStateOf("") }
    var path by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var domain by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(FolderType.PACK) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }

    fun currentConfig() = SmbConfig(host.trim(), share.trim(), path.trim(), username, password, domain.trim())

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

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(scheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Lan, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.smb_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = scheme.onSurface
                )
            }
            Spacer(Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                SmbField(stringResource(R.string.smb_host), host, { host = it }, stringResource(R.string.smb_host_hint))
                Spacer(Modifier.height(8.dp))
                SmbField(stringResource(R.string.smb_share), share, { share = it }, stringResource(R.string.smb_share_hint))
                Spacer(Modifier.height(8.dp))
                SmbField(stringResource(R.string.smb_path), path, { path = it }, stringResource(R.string.smb_path_hint))
                Spacer(Modifier.height(8.dp))
                SmbField(stringResource(R.string.smb_user), username, { username = it }, stringResource(R.string.smb_user_hint))
                Spacer(Modifier.height(8.dp))
                SmbField(stringResource(R.string.smb_pass), password, { password = it }, stringResource(R.string.smb_pass_hint), isPassword = true)
                Spacer(Modifier.height(8.dp))
                SmbField(stringResource(R.string.smb_domain), domain, { domain = it }, stringResource(R.string.smb_domain_hint))
                Spacer(Modifier.height(8.dp))
                SmbField(stringResource(R.string.smb_name), name, { name = it }, stringResource(R.string.smb_name_hint))
            }
            Spacer(Modifier.height(12.dp))

            // Type choice (NORMAL / PACK)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TypeChip(stringResource(R.string.folder_type_normal), type == FolderType.NORMAL) { type = FolderType.NORMAL }
                TypeChip(stringResource(R.string.folder_type_pack), type == FolderType.PACK) { type = FolderType.PACK }
            }
            Spacer(Modifier.height(12.dp))

            // Test result line: null = connected ok, else error message
            testResult?.let { result ->
                Text(
                    text = if (result == OK_MARKER) {
                        stringResource(R.string.smb_ok)
                    } else {
                        stringResource(R.string.smb_fail, result)
                    },
                    color = if (result == OK_MARKER) Color(0xFF22C55E) else MaterialTheme.colorScheme.error,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(8.dp))
            }

            // Buttons: Test / Cancel / Add
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(scheme.surfaceVariant)
                        .clickable {
                            scope.launch {
                                testing = true
                                // null from SmbClient.test = connected → OK marker
                                testResult = SmbClient.test(currentConfig()) ?: OK_MARKER
                                testing = false
                            }
                        }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (testing) stringResource(R.string.smb_testing) else stringResource(R.string.smb_test),
                        color = scheme.onSurfaceVariant,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(scheme.surfaceVariant)
                        .clickable(onClick = onDismiss)
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.cancel),
                        color = scheme.onSurfaceVariant,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1.4f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(scheme.primary)
                        .clickable {
                            if (host.isNotBlank() && share.isNotBlank()) {
                                onConfirm(currentConfig(), name.ifBlank { null }, type)
                            }
                        }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.smb_add),
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun SmbField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    hint: String,
    isPassword: Boolean = false
) {
    val scheme = MaterialTheme.colorScheme
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = { Text(hint, color = scheme.outline) },
        singleLine = true,
        visualTransformation = if (isPassword) {
            androidx.compose.ui.text.input.PasswordVisualTransformation()
        } else {
            androidx.compose.ui.text.input.VisualTransformation.None
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = scheme.primary,
            unfocusedBorderColor = scheme.outline,
            focusedTextColor = scheme.onSurface,
            unfocusedTextColor = scheme.onSurface
        )
    )
}

@Composable
private fun TypeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) scheme.primary else scheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.FolderOpen,
            contentDescription = null,
            tint = if (selected) scheme.onPrimary else scheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = if (selected) scheme.onPrimary else scheme.onSurfaceVariant
        )
    }
}
