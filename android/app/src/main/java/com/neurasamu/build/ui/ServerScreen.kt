package com.neurasamu.build.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neurasamu.build.data.AtomicStorage
import com.neurasamu.build.data.ModelEntry
import com.neurasamu.build.data.ModelKind
import com.neurasamu.build.data.ServerRunState
import androidx.compose.runtime.collectAsState
import com.neurasamu.build.server.LogLevel
import com.neurasamu.build.server.ServerMonitor
import com.neurasamu.build.state.SaMuAppState
import com.neurasamu.build.state.SaMuRepository
import com.neurasamu.build.ui.modals.ConfirmDeleteModal
import com.neurasamu.build.ui.modals.ConnectOtherDeviceModal
import com.neurasamu.build.ui.modals.ModelSettingsModal
import com.neurasamu.build.ui.modals.ServerSettingsModal
import com.neurasamu.build.ui.modals.samuTextFieldColors
import com.neurasamu.build.ui.theme.LocalFontScale
import com.neurasamu.build.ui.theme.LocalSaMuColors
import com.neurasamu.build.ui.theme.SaMuRadius

@Composable
fun ServerScreen(state: SaMuAppState) {
    val colors = LocalSaMuColors.current

    var showServerSettings by remember { mutableStateOf(false) }
    var showConnectGuide by remember { mutableStateOf(false) }
    var editingModel by remember { mutableStateOf<ModelEntry?>(null) }
    var deletingModel by remember { mutableStateOf<ModelEntry?>(null) }

    val addModelLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            SaMuRepository.addModelFromUri(uri)
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .navigationBarsPadding()
    ) {
        val isWide = maxWidth >= 768.dp

        if (isWide) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1.25f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    ServerControlsCard(
                        state = state,
                        onOpenSettings = { showServerSettings = true },
                        onOpenConnectGuide = { showConnectGuide = true }
                    )
                    ModelsSectionCard(
                        state = state,
                        onAddModel = { addModelLauncher.launch(arrayOf("*/*")) },
                        onOpenModelSettings = { editingModel = it },
                        onDeleteModel = { deletingModel = it }
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(0.9f)
                        .verticalScroll(rememberScrollState())
                ) {
                    RealTimeMonitorCard(state = state)
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ServerControlsCard(
                    state = state,
                    onOpenSettings = { showServerSettings = true },
                    onOpenConnectGuide = { showConnectGuide = true }
                )
                ModelsSectionCard(
                    state = state,
                    onAddModel = { addModelLauncher.launch(arrayOf("*/*")) },
                    onOpenModelSettings = { editingModel = it },
                    onDeleteModel = { deletingModel = it }
                )
                RealTimeMonitorCard(state = state)
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (showServerSettings) {
        ServerSettingsModal(
            settings = state.settings,
            activeModel = state.activeModel,
            hardware = state.hardware,
            onSave = { SaMuRepository.saveServerSettings(it) },
            onDismiss = { showServerSettings = false }
        )
    }

    if (showConnectGuide) {
        ConnectOtherDeviceModal(
            port = state.settings.port,
            lanIp = state.lanIp,
            lanAccess = state.settings.lanAccess,
            requireAuth = state.settings.requireAuth,
            onCopied = { SaMuRepository.showToast(it) },
            onDismiss = { showConnectGuide = false }
        )
    }

    editingModel?.let { entry ->
        ModelSettingsModal(
            entry = entry,
            hardware = state.hardware,
            onPickExtraFile = { uri, role, cb -> SaMuRepository.copyExtraFileUri(uri, role, cb) },
            onSave = { SaMuRepository.saveModelProfile(it) },
            onDismiss = { editingModel = null }
        )
    }

    deletingModel?.let { entry ->
        ConfirmDeleteModal(
            title = "Delete Model",
            message = "Delete '${entry.displayName}' (${AtomicStorage.formatBytes(entry.sizeBytes)}) from device storage?",
            onConfirm = { SaMuRepository.deleteModel(entry) },
            onDismiss = { deletingModel = null }
        )
    }
}

// ==================== SERVER CONTROLS CARD ====================

@Composable
private fun ServerControlsCard(
    state: SaMuAppState,
    onOpenSettings: () -> Unit,
    onOpenConnectGuide: () -> Unit
) {
    val colors = LocalSaMuColors.current
    val scale = LocalFontScale.current
    val clipboard = LocalClipboardManager.current

    val statusDotColor = when (state.serverState) {
        ServerRunState.PROCESSING -> colors.primary
        ServerRunState.RUNNING -> colors.success
        ServerRunState.STARTING -> colors.warning
        ServerRunState.STOPPED -> colors.textTertiary
    }
    val mon by ServerMonitor.snapshot.collectAsState()
    val effectiveState = when {
        state.serverState == ServerRunState.STOPPED -> ServerRunState.STOPPED
        state.serverState == ServerRunState.STARTING -> ServerRunState.STARTING
        mon.activeRequests > 0 -> ServerRunState.PROCESSING
        else -> ServerRunState.RUNNING
    }
    val statusLabel = when (effectiveState) {
        ServerRunState.PROCESSING -> "Processing (${mon.activeRequests} active)"
        ServerRunState.RUNNING -> "Server Running"
        ServerRunState.STARTING -> "Starting Engine..."
        ServerRunState.STOPPED -> "Server Stopped"
    }

    val modelApiName = state.settings.modelApiName.ifBlank {
        state.activeModel?.displayName ?: "samu-local"
    }
    val nativeMax = state.activeModel?.nativeContext ?: 0
    val ctxReadout = if (nativeMax > 0) {
        "${if (state.settings.contextSize == 0) nativeMax else state.settings.contextSize} / $nativeMax max"
    } else {
        if (state.settings.contextSize == 0) "Model Max" else "${state.settings.contextSize}"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SaMuRadius.lg)
            .background(colors.surface)
            .border(1.dp, colors.divider, SaMuRadius.lg)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(SaMuRadius.full)
                    .background(statusDotColor)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = statusLabel,
                color = colors.onSurface,
                fontSize = (15f * scale).sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { SaMuRepository.refreshLanIp() }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Refresh, "Refresh IP", tint = colors.textSecondary, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onOpenSettings, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Settings, "Server Settings", tint = colors.textSecondary, modifier = Modifier.size(18.dp))
            }
        }

        // Editable Model Name (API)
        OutlinedTextField(
            value = state.settings.modelApiName,
            onValueChange = { SaMuRepository.updateModelApiName(it) },
            label = { Text("Model Name (API)") },
            placeholder = { Text(state.activeModel?.displayName ?: "samu-local") },
            singleLine = true,
            trailingIcon = {
                IconButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(modelApiName))
                        SaMuRepository.showToast("Model name copied")
                    }
                ) {
                    Icon(Icons.Default.ContentCopy, "Copy model name", tint = colors.textSecondary, modifier = Modifier.size(16.dp))
                }
            },
            colors = samuTextFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )

        // URL Target Switcher
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(SaMuRadius.sm)
                    .background(colors.background)
                    .border(1.dp, colors.divider, SaMuRadius.sm)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val other = state.settings.useOtherDeviceUrl
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(SaMuRadius.xs)
                        .background(if (!other) colors.primary else Color.Transparent)
                        .clickable { SaMuRepository.setUseOtherDeviceUrl(false) }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "This Device",
                        color = if (!other) Color.White else colors.textSecondary,
                        fontSize = (11.5f * scale).sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(SaMuRadius.xs)
                        .background(if (other) colors.primary else Color.Transparent)
                        .clickable { SaMuRepository.setUseOtherDeviceUrl(true) }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Other Device",
                        color = if (other) Color.White else colors.textSecondary,
                        fontSize = (11.5f * scale).sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (state.settings.useOtherDeviceUrl) {
                OutlinedButton(
                    onClick = onOpenConnectGuide,
                    shape = SaMuRadius.sm,
                    border = BorderStroke(1.dp, colors.primary),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Wifi, null, tint = colors.primary, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Setup Guide", color = colors.primary, fontSize = (11.5f * scale).sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Readout rows (monospace)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(SaMuRadius.sm)
                .background(colors.background)
                .border(1.dp, colors.divider, SaMuRadius.sm)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            ReadoutRow("Active URL", state.activeEndpointUrl)
            ReadoutRow("Model Name", modelApiName)
            ReadoutRow("Context Window", ctxReadout)
            if (state.settings.requireAuth) {
                ReadoutRow("API Key", state.settings.apiKey.ifBlank { "(not set)" })
            }
        }

        // Status / Error feedback
        if (!state.serverErrorText.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(SaMuRadius.sm)
                    .background(colors.errorBg)
                    .border(1.dp, colors.error, SaMuRadius.sm)
                    .padding(10.dp)
            ) {
                Text(
                    text = state.serverErrorText,
                    color = colors.error,
                    fontSize = (12f * scale).sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        } else if (state.serverStatusText.isNotBlank()) {
            Text(
                text = state.serverStatusText,
                color = if (state.isServerOnline) colors.success else colors.textSecondary,
                fontSize = (11.5f * scale).sp
            )
        }

        // Quick Copy Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QuickCopyButton(
                label = "Copy URL",
                modifier = Modifier.weight(1f),
                onClick = {
                    clipboard.setText(AnnotatedString(state.activeEndpointUrl))
                    SaMuRepository.showToast("URL copied")
                }
            )
            QuickCopyButton(
                label = "Copy Model",
                modifier = Modifier.weight(1f),
                onClick = {
                    clipboard.setText(AnnotatedString(modelApiName))
                    SaMuRepository.showToast("Model copied")
                }
            )
            QuickCopyButton(
                label = "Copy Key",
                modifier = Modifier.weight(1f),
                onClick = {
                    val key = if (state.settings.requireAuth) state.settings.apiKey else "not-required"
                    clipboard.setText(AnnotatedString(key))
                    SaMuRepository.showToast(if (state.settings.requireAuth) "API key copied" else "Auth disabled (copied 'not-required')")
                }
            )
        }

        // Full-width Start / Stop Button
        val isStopped = state.serverState == ServerRunState.STOPPED
        val isStarting = state.serverState == ServerRunState.STARTING
        Button(
            onClick = {
                if (isStopped) SaMuRepository.startServer() else SaMuRepository.stopServer()
            },
            shape = SaMuRadius.sm,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isStopped) colors.primary else colors.error,
                contentColor = Color.White
            ),
            modifier = Modifier.fillMaxWidth().height(46.dp)
        ) {
            if (isStarting) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Stop / Cancel", fontWeight = FontWeight.Bold, fontSize = (14f * scale).sp)
            } else if (isStopped) {
                Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text("Start Server", fontWeight = FontWeight.Bold, fontSize = (14f * scale).sp)
            } else {
                Icon(Icons.Default.Stop, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text("Stop Server", fontWeight = FontWeight.Bold, fontSize = (14f * scale).sp)
            }
        }
    }
}

@Composable
private fun ReadoutRow(label: String, value: String) {
    val colors = LocalSaMuColors.current
    val scale = LocalFontScale.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = colors.textTertiary,
            fontSize = (11f * scale).sp,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(
            text = value,
            color = colors.onSurface,
            fontSize = (11.5f * scale).sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun QuickCopyButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = LocalSaMuColors.current
    val scale = LocalFontScale.current
    OutlinedButton(
        onClick = onClick,
        shape = SaMuRadius.sm,
        border = BorderStroke(1.dp, colors.divider),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
        modifier = modifier.height(36.dp)
    ) {
        Icon(Icons.Default.ContentCopy, null, tint = colors.textSecondary, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, color = colors.onSurface, fontSize = (11f * scale).sp, maxLines = 1)
    }
}

// ==================== MODELS SECTION CARD ====================

@Composable
private fun ModelsSectionCard(
    state: SaMuAppState,
    onAddModel: () -> Unit,
    onOpenModelSettings: (ModelEntry) -> Unit,
    onDeleteModel: (ModelEntry) -> Unit
) {
    val colors = LocalSaMuColors.current
    val scale = LocalFontScale.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SaMuRadius.lg)
            .background(colors.surface)
            .border(1.dp, colors.divider, SaMuRadius.lg)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Models (${state.models.size})",
                color = colors.onSurface,
                fontSize = (15f * scale).sp,
                fontWeight = FontWeight.Bold
            )
            Button(
                onClick = onAddModel,
                enabled = !state.isImportingModel,
                shape = SaMuRadius.sm,
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = Color.White),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.height(36.dp)
            ) {
                if (state.isImportingModel) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Importing...", fontSize = (12f * scale).sp)
                } else {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add Model", fontSize = (12f * scale).sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (state.models.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(SaMuRadius.md)
                    .background(colors.background)
                    .border(1.dp, colors.divider, SaMuRadius.md)
                    .padding(18.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No models yet. Tap '+ Add Model' to import .gguf, .safetensors, .ckpt, or .onnx files.",
                    color = colors.textSecondary,
                    fontSize = (12.5f * scale).sp
                )
            }
        } else {
            state.models.forEach { entry ->
                val isLoaded = state.activeModelPath == entry.path
                ModelRowCard(
                    entry = entry,
                    isLoaded = isLoaded,
                    isServerOnline = state.isServerOnline,
                    onLoad = { SaMuRepository.loadModelAndStart(entry) },
                    onSettings = { onOpenModelSettings(entry) },
                    onDelete = { onDeleteModel(entry) }
                )
            }
        }
    }
}

@Composable
private fun ModelRowCard(
    entry: ModelEntry,
    isLoaded: Boolean,
    isServerOnline: Boolean,
    onLoad: () -> Unit,
    onSettings: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = LocalSaMuColors.current
    val scale = LocalFontScale.current

    val rowBg = if (isLoaded) colors.primary.copy(alpha = 0.14f) else colors.background
    val rowBorder = if (isLoaded) colors.primary.copy(alpha = 0.55f) else colors.divider

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SaMuRadius.md)
            .background(rowBg)
            .border(1.dp, rowBorder, SaMuRadius.md)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.displayName,
                    color = colors.onSurface,
                    fontSize = (13.5f * scale).sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = AtomicStorage.formatBytes(entry.sizeBytes),
                    color = colors.textSecondary,
                    fontSize = (11f * scale).sp
                )
            }

            // Load / Status Action
            if (entry.profile.kind == ModelKind.TEXT_VISION) {
                if (isLoaded) {
                    Box(
                        modifier = Modifier
                            .clip(SaMuRadius.xs)
                            .background(if (isServerOnline) colors.success.copy(alpha = 0.18f) else colors.primary.copy(alpha = 0.18f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (isServerOnline) "RUNNING" else "LOADED",
                            color = if (isServerOnline) colors.success else colors.primary,
                            fontSize = (10.5f * scale).sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    OutlinedButton(
                        onClick = onLoad,
                        shape = SaMuRadius.sm,
                        border = BorderStroke(1.dp, colors.primary),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Load", color = colors.primary, fontSize = (11.5f * scale).sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .clip(SaMuRadius.xs)
                        .background(colors.success.copy(alpha = 0.18f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("READY", color = colors.success, fontSize = (10.5f * scale).sp, fontWeight = FontWeight.Bold)
                }
            }

            IconButton(onClick = onSettings, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Tune, "Model Settings", tint = colors.textSecondary, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Delete, "Delete Model", tint = colors.error, modifier = Modifier.size(18.dp))
            }
        }

        // Compact Badges Row
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ModelBadge(entry.profile.kind.badge, colors.surfaceVariant, colors.onSurface)
            if (entry.nativeContext > 0) {
                val ctxText = if (entry.nativeContext >= 1024) "${entry.nativeContext / 1024}K ctx" else "${entry.nativeContext} ctx"
                ModelBadge(ctxText, colors.surfaceVariant, colors.textSecondary)
            }
            if (entry.hasMmproj) {
                ModelBadge("VISION", colors.primary.copy(alpha = 0.2f), colors.primary)
            }
            if (entry.extraFilesCount > 0) {
                ModelBadge("+${entry.extraFilesCount} files", colors.success.copy(alpha = 0.18f), colors.success)
            }
        }
    }
}

@Composable
private fun ModelBadge(text: String, bg: Color, fg: Color) {
    Box(
        modifier = Modifier
            .clip(SaMuRadius.xs)
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            color = fg,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace
        )
    }
}

// ==================== REAL-TIME MONITOR CARD ====================

@Composable
private fun RealTimeMonitorCard(state: SaMuAppState) {
    val colors = LocalSaMuColors.current
    val scale = LocalFontScale.current
    val mon by ServerMonitor.snapshot.collectAsState()
    val effectiveState = when {
        state.serverState == ServerRunState.STOPPED -> ServerRunState.STOPPED
        state.serverState == ServerRunState.STARTING -> ServerRunState.STARTING
        mon.activeRequests > 0 -> ServerRunState.PROCESSING
        else -> ServerRunState.RUNNING
    }

    val pillColor = when (effectiveState) {
        ServerRunState.PROCESSING -> colors.primary
        ServerRunState.RUNNING -> colors.success
        ServerRunState.STARTING -> colors.warning
        ServerRunState.STOPPED -> colors.textTertiary
    }
    val pillText = when (effectiveState) {
        ServerRunState.PROCESSING -> "Generating · ${String.format("%.1f", mon.liveTokensPerSec)} tok/s"
        ServerRunState.RUNNING -> "Idle — Ready"
        ServerRunState.STARTING -> "Starting..."
        ServerRunState.STOPPED -> "Offline"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SaMuRadius.lg)
            .background(colors.surface)
            .border(1.dp, colors.divider, SaMuRadius.lg)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Real-Time Monitor",
                    color = colors.onSurface,
                    fontSize = (15f * scale).sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = state.hardware?.tierLabel ?: "Detecting hardware...",
                    color = colors.textSecondary,
                    fontSize = (11f * scale).sp
                )
            }
            Box(
                modifier = Modifier
                    .clip(SaMuRadius.xl)
                    .background(pillColor.copy(alpha = 0.15f))
                    .border(1.dp, pillColor.copy(alpha = 0.45f), SaMuRadius.xl)
                    .padding(horizontal = 9.dp, vertical = 4.dp)
            ) {
                Text(
                    text = pillText,
                    color = pillColor,
                    fontSize = (10.5f * scale).sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // 2x2 Stat Tiles
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            MonitorStatTile("Active", mon.activeRequests.toString(), Modifier.weight(1f))
            MonitorStatTile("Requests", mon.totalRequests.toString(), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            MonitorStatTile("Live Speed (t/s)", String.format("%.1f", mon.liveTokensPerSec), Modifier.weight(1f))
            MonitorStatTile("Tokens", mon.totalTokens.toString(), Modifier.weight(1f))
        }

        HorizontalDivider(color = colors.divider)

        Text(
            text = "LIVE ACTIVITY",
            color = colors.textTertiary,
            fontSize = (10f * scale).sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 160.dp, max = 280.dp)
                .clip(SaMuRadius.sm)
                .background(colors.background)
                .border(1.dp, colors.divider, SaMuRadius.sm)
                .verticalScroll(rememberScrollState())
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (mon.logs.isEmpty()) {
                Text(
                    text = "Waiting for server activity...",
                    color = colors.textTertiary,
                    fontSize = (11.5f * scale).sp,
                    fontFamily = FontFamily.Monospace
                )
            } else {
                mon.logs.asReversed().forEach { entry ->
                    val tagColor = when (entry.level) {
                        LogLevel.INFO -> colors.textSecondary
                        LogLevel.ACTIVE -> colors.primary
                        LogLevel.OK -> colors.success
                        LogLevel.ERR -> colors.error
                    }
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = entry.timestamp,
                            color = colors.textTertiary,
                            fontSize = (10.5f * scale).sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = entry.level.tag.padEnd(6),
                            color = tagColor,
                            fontSize = (10.5f * scale).sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = entry.message,
                            color = colors.onSurface,
                            fontSize = (11f * scale).sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MonitorStatTile(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = LocalSaMuColors.current
    val scale = LocalFontScale.current
    Column(
        modifier = modifier
            .clip(SaMuRadius.sm)
            .background(colors.background)
            .border(1.dp, colors.divider, SaMuRadius.sm)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(label, color = colors.textTertiary, fontSize = (10.5f * scale).sp)
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            color = colors.onSurface,
            fontSize = (17f * scale).sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}
