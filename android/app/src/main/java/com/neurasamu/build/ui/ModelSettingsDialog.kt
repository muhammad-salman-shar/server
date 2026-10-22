package com.neurasamu.build.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neurasamu.build.model.ModelCategory
import com.neurasamu.build.model.SamuModel

@Composable
fun ModelSettingsDialog(
    model: SamuModel,
    onSave: (ModelCategory, String?, Boolean, Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var category by remember { mutableStateOf(model.category) }
    var mmprojPath by remember { mutableStateOf(model.mmprojPath) }
    var mmprojEnabled by remember { mutableStateOf(model.mmprojEnabled) }
    var ctxText by remember { mutableStateOf(model.ctx.toString()) }
    var threadsText by remember { mutableStateOf(model.threads.toString()) }

    val mmprojPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { mmprojPath = it.toString() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Model Settings", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(model.displayName, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary, maxLines = 1)
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).heightIn(max = 520.dp)) {

                // Category
                Text("Category", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                ModelCategory.entries.forEach { c ->
                    Row(verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { category = c }.padding(vertical = 2.dp)) {
                        RadioButton(selected = category == c, onClick = { category = c },
                            modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(c.label, style = MaterialTheme.typography.bodySmall)
                    }
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))

                // Extra file (mmproj)
                Text("Extra file (mmproj / projector)", style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text("Required for vision/audio models. Select the mmproj-*.gguf file.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Spacer(Modifier.height(8.dp))

                if (mmprojPath != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.InsertDriveFile, null, Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    mmprojPath!!.substringAfterLast("/").take(28),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1
                                )
                                IconButton(onClick = { mmprojPath = null },
                                    modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Delete, "remove", Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.error)
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(checked = mmprojEnabled,
                                    onCheckedChange = { mmprojEnabled = it },
                                    modifier = Modifier.scale(0.8f))
                                Spacer(Modifier.width(6.dp))
                                Text(if (mmprojEnabled) "Enabled" else "Disabled",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (mmprojEnabled) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = { mmprojPicker.launch(arrayOf("*/*")) }) {
                        Text("Change file", style = MaterialTheme.typography.labelSmall)
                    }
                } else {
                    OutlinedButton(onClick = { mmprojPicker.launch(arrayOf("*/*")) },
                        modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.InsertDriveFile, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Select mmproj file")
                    }
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))

                // Advanced
                Text("Advanced", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = ctxText,
                        onValueChange = { ctxText = it.filter { c -> c.isDigit() } },
                        label = { Text("Context") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = threadsText,
                        onValueChange = { threadsText = it.filter { c -> c.isDigit() } },
                        label = { Text("Threads") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    category,
                    mmprojPath,
                    mmprojEnabled,
                    ctxText.toIntOrNull()?.coerceIn(512, 131072) ?: 2048,
                    threadsText.toIntOrNull()?.coerceIn(1, 16) ?: 4
                )
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
