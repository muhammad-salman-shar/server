package com.neurasamu.build.ui.modals

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.neurasamu.build.data.ApiConfig
import com.neurasamu.build.data.AtomicStorage
import com.neurasamu.build.data.ChatPrefs
import com.neurasamu.build.data.ExtraFileRole
import com.neurasamu.build.data.ExtraModelFile
import com.neurasamu.build.data.ModelEntry
import com.neurasamu.build.data.ModelKind
import com.neurasamu.build.data.ModelProfile
import com.neurasamu.build.data.ServerSettings
import com.neurasamu.build.data.ThemeMode
import com.neurasamu.build.data.ThinkingOverride
import com.neurasamu.build.data.UrlSanitizer
import com.neurasamu.build.engine.HardwareProfile
import com.neurasamu.build.ui.theme.LocalFontScale
import com.neurasamu.build.ui.theme.LocalSaMuColors
import com.neurasamu.build.ui.theme.SaMuRadius
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt

// ==================== UNIFIED MODAL SHELL ====================

@Composable
fun SaMuModalShell(
    title: String,
    subtitle: String? = null,
    onDismiss: () -> Unit,
    saveLabel: String = "Save",
    saveEnabled: Boolean = true,
    isDestructive: Boolean = false,
    showFooter: Boolean = true,
    onSave: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LocalSaMuColors.current
    val scale = LocalFontScale.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.70f))
                .clickable(onClick = onDismiss)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .clip(SaMuRadius.lg)
                    .background(colors.surface)
                    .border(1.dp, colors.divider, SaMuRadius.lg)
                    .clickable(enabled = false, onClick = {})
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            color = colors.onSurface,
                            fontSize = (16f * scale).sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (!subtitle.isNullOrBlank()) {
                            Text(
                                text = subtitle,
                                color = colors.primary,
                                fontSize = (11.5f * scale).sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.Close, "Close", tint = colors.textSecondary, modifier = Modifier.size(18.dp))
                    }
                }
                HorizontalDivider(thickness = 1.dp, color = colors.divider)

                // Scrollable Body
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = content
                )

                // Footer
                if (showFooter) {
                    HorizontalDivider(thickness = 1.dp, color = colors.divider)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            shape = SaMuRadius.sm,
                            border = BorderStroke(1.dp, colors.divider),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.onSurface),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text("Cancel", fontSize = (13f * scale).sp)
                        }
                        if (onSave != null) {
                            Spacer(Modifier.width(10.dp))
                            Button(
                                onClick = onSave,
                                enabled = saveEnabled,
                                shape = SaMuRadius.sm,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isDestructive) colors.error else colors.primary,
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
                            ) {
                                Text(saveLabel, fontSize = (13f * scale).sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun samuTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = LocalSaMuColors.current.primary,
    unfocusedBorderColor = LocalSaMuColors.current.divider,
    focusedLabelColor = LocalSaMuColors.current.primary,
    unfocusedLabelColor = LocalSaMuColors.current.textSecondary,
    focusedTextColor = LocalSaMuColors.current.onSurface,
    unfocusedTextColor = LocalSaMuColors.current.onSurface,
    cursorColor = LocalSaMuColors.current.primary,
    focusedContainerColor = LocalSaMuColors.current.background,
    unfocusedContainerColor = LocalSaMuColors.current.background
)

@Composable
fun SectionLabel(text: String) {
    val colors = LocalSaMuColors.current
    val scale = LocalFontScale.current
    Text(
        text = text.uppercase(),
        color = colors.textTertiary,
        fontSize = (10f * scale).sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.5.sp
    )
}

// ==================== 1. API EDIT MODAL ====================

@Composable
fun ApiEditModal(
    initial: ApiConfig?,
    onSave: (ApiConfig) -> Unit,
    onDismiss: () -> Unit
) {
    var label by remember { mutableStateOf(initial?.label ?: "") }
    var baseUrl by remember { mutableStateOf(initial?.baseUrl ?: "") }
    var modelName by remember { mutableStateOf(initial?.modelName ?: "") }
    var apiKey by remember { mutableStateOf(initial?.apiKey ?: "") }
    var systemPrompt by remember { mutableStateOf(initial?.systemPrompt ?: "") }

    val canSave = label.isNotBlank() && baseUrl.isNotBlank() && modelName.isNotBlank()

    SaMuModalShell(
        title = if (initial == null) "Add Custom API" else "Edit API",
        subtitle = "OpenAI-compatible endpoint",
        onDismiss = onDismiss,
        saveEnabled = canSave,
        onSave = {
            onSave(
                ApiConfig(
                    id = initial?.id ?: UUID.randomUUID().toString(),
                    label = label.trim(),
                    baseUrl = UrlSanitizer.sanitize(baseUrl),
                    modelName = modelName.trim(),
                    apiKey = apiKey.trim(),
                    systemPrompt = systemPrompt.trim(),
                    isBuiltIn = false
                )
            )
            onDismiss()
        }
    ) {
        OutlinedTextField(
            value = label,
            onValueChange = { label = it },
            label = { Text("Label *") },
            placeholder = { Text("e.g. Groq / OpenRouter / Home PC") },
            singleLine = true,
            colors = samuTextFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            label = { Text("Base URL *") },
            placeholder = { Text("https://api.openai.com/v1") },
            singleLine = true,
            colors = samuTextFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = modelName,
            onValueChange = { modelName = it },
            label = { Text("Model Name *") },
            placeholder = { Text("e.g. gpt-4o-mini or llama-3.3-70b") },
            singleLine = true,
            colors = samuTextFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text("API Key (optional)") },
            placeholder = { Text("sk-...") },
            singleLine = true,
            colors = samuTextFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = systemPrompt,
            onValueChange = { systemPrompt = it },
            label = { Text("System Prompt (optional)") },
            placeholder = { Text("Custom persona or instructions for this API...") },
            minLines = 3,
            maxLines = 5,
            colors = samuTextFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ==================== 2. CHAT SETTINGS MODAL ====================

@Composable
fun ChatSettingsModal(
    prefs: ChatPrefs,
    onSave: (ChatPrefs) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalSaMuColors.current
    var theme by remember { mutableStateOf(prefs.themeMode) }
    var fontScale by remember { mutableFloatStateOf(prefs.fontScale) }
    var customEnabled by remember { mutableStateOf(prefs.customInstructionsEnabled) }
    var customText by remember { mutableStateOf(prefs.customInstructions) }

    SaMuModalShell(
        title = "Chat Settings",
        subtitle = "Appearance & Global Instructions",
        onDismiss = onDismiss,
        onSave = {
            onSave(
                prefs.copy(
                    themeMode = theme,
                    fontScale = (fontScale * 10f).roundToInt() / 10f,
                    customInstructionsEnabled = customEnabled,
                    customInstructions = customText.trim()
                )
            )
            onDismiss()
        }
    ) {
        SectionLabel("Theme")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ThemeMode.entries.forEach { mode ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clip(SaMuRadius.sm)
                        .background(if (theme == mode) colors.surfaceVariant else colors.background)
                        .border(
                            1.dp,
                            if (theme == mode) colors.primary else colors.divider,
                            SaMuRadius.sm
                        )
                        .clickable { theme = mode }
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    RadioButton(
                        selected = theme == mode,
                        onClick = { theme = mode },
                        colors = RadioButtonDefaults.colors(selectedColor = colors.primary),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(mode.label, color = colors.onSurface, fontSize = 12.5.sp)
                }
            }
        }

        HorizontalDivider(color = colors.divider)
        SectionLabel("Font Size (${String.format("%.1fx", fontScale)})")
        Slider(
            value = fontScale,
            onValueChange = { fontScale = ((it * 10f).roundToInt() / 10f).coerceIn(0.8f, 1.4f) },
            valueRange = 0.8f..1.4f,
            steps = 5,
            colors = SliderDefaults.colors(
                thumbColor = colors.primary,
                activeTrackColor = colors.primary,
                inactiveTrackColor = colors.surfaceVariant
            )
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(SaMuRadius.sm)
                .background(colors.background)
                .border(1.dp, colors.divider, SaMuRadius.sm)
                .padding(10.dp)
        ) {
            Text(
                text = "Preview: SaMu Lab renders crisp text at ${String.format("%.1fx", fontScale)}.",
                color = colors.onSurface,
                fontSize = (14f * fontScale).sp,
                lineHeight = (14f * fontScale * 1.55f).sp
            )
        }

        HorizontalDivider(color = colors.divider)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Global Custom Instructions", color = colors.onSurface, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                Text("Prepended to every chat across all APIs", color = colors.textSecondary, fontSize = 11.sp)
            }
            Switch(
                checked = customEnabled,
                onCheckedChange = { customEnabled = it },
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = colors.primary)
            )
        }
        if (customEnabled) {
            OutlinedTextField(
                value = customText,
                onValueChange = { customText = it },
                label = { Text("Instructions") },
                placeholder = { Text("Be concise, direct, and show code examples...") },
                minLines = 3,
                maxLines = 6,
                colors = samuTextFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ==================== 3. SERVER SETTINGS MODAL ====================

@Composable
fun ServerSettingsModal(
    settings: ServerSettings,
    activeModel: ModelEntry?,
    hardware: HardwareProfile?,
    onSave: (ServerSettings) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalSaMuColors.current
    val nativeMax = activeModel?.nativeContext ?: 0

    var portText by remember { mutableStateOf(settings.port.toString()) }
    var lanAccess by remember { mutableStateOf(settings.lanAccess) }
    var threadsText by remember { mutableStateOf(settings.cpuThreads.toString()) }
    var ctxText by remember { mutableStateOf(settings.contextSize.toString()) }
    var requireAuth by remember { mutableStateOf(settings.requireAuth) }
    var apiKey by remember { mutableStateOf(settings.apiKey) }

    val parsedPort = portText.toIntOrNull()
    val portError = parsedPort == null || parsedPort !in 1024..65535
    val parsedCtx = ctxText.toIntOrNull() ?: 0
    val ctxTooHigh = nativeMax > 0 && parsedCtx > nativeMax
    val keyTooShort = requireAuth && apiKey.trim().length < 8
    val canSave = !portError && !ctxTooHigh && !keyTooShort

    SaMuModalShell(
        title = "Server Settings",
        subtitle = "Restart server to apply port or engine changes",
        onDismiss = onDismiss,
        saveEnabled = canSave,
        onSave = {
            onSave(
                settings.copy(
                    port = (parsedPort ?: 8080).coerceIn(1024, 65535),
                    lanAccess = lanAccess,
                    cpuThreads = (threadsText.toIntOrNull() ?: 0).coerceAtLeast(0),
                    contextSize = parsedCtx.coerceAtLeast(0),
                    requireAuth = requireAuth,
                    apiKey = apiKey.trim()
                )
            )
            onDismiss()
        }
    ) {
        // Hardware Auto-Tune Banner
        if (hardware != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(SaMuRadius.sm)
                    .background(colors.primary.copy(alpha = 0.12f))
                    .border(1.dp, colors.primary.copy(alpha = 0.35f), SaMuRadius.sm)
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Memory, null, tint = colors.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(hardware.tierLabel, color = colors.onSurface, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Recommended: ${hardware.recommendedThreads} threads · ctx ${hardware.recommendedContext}",
                        color = colors.textSecondary,
                        fontSize = 11.sp
                    )
                }
                OutlinedButton(
                    onClick = {
                        threadsText = hardware.recommendedThreads.toString()
                        val tunedCtx = if (nativeMax > 0) {
                            minOf(hardware.recommendedContext, nativeMax)
                        } else hardware.recommendedContext
                        ctxText = tunedCtx.toString()
                    },
                    shape = SaMuRadius.sm,
                    border = BorderStroke(1.dp, colors.primary),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, null, tint = colors.primary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Auto-Tune", color = colors.primary, fontSize = 11.5.sp)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = portText,
                onValueChange = { portText = it.filter(Char::isDigit).take(5) },
                label = { Text("Port (1024–65535)") },
                isError = portError,
                singleLine = true,
                colors = samuTextFieldColors(),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = threadsText,
                onValueChange = { threadsText = it.filter(Char::isDigit).take(2) },
                label = { Text("CPU Threads (0=Auto)") },
                singleLine = true,
                colors = samuTextFieldColors(),
                modifier = Modifier.weight(1f)
            )
        }

        // Context Window with MODEL MAX + Max quick-fill
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Context Window (0 = model max)", color = colors.textSecondary, fontSize = 11.5.sp)
                if (nativeMax > 0) {
                    Text(
                        text = "MODEL MAX: $nativeMax",
                        color = colors.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = ctxText,
                    onValueChange = { ctxText = it.filter(Char::isDigit).take(7) },
                    isError = ctxTooHigh,
                    singleLine = true,
                    colors = samuTextFieldColors(),
                    modifier = Modifier.weight(1f)
                )
                if (nativeMax > 0) {
                    OutlinedButton(
                        onClick = { ctxText = nativeMax.toString() },
                        shape = SaMuRadius.sm,
                        border = BorderStroke(1.dp, colors.primary),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp)
                    ) {
                        Text("Max", color = colors.primary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    }
                }
            }
            if (ctxTooHigh) {
                Text(
                    text = "Exceeds model native max ($nativeMax). Lower it to prevent crash.",
                    color = colors.error,
                    fontSize = 11.sp
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("LAN Access (0.0.0.0)", color = colors.onSurface, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                Text("Allow other devices on Wi-Fi / Hotspot to connect", color = colors.textSecondary, fontSize = 11.sp)
            }
            Switch(
                checked = lanAccess,
                onCheckedChange = { lanAccess = it },
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = colors.primary)
            )
        }

        HorizontalDivider(color = colors.divider)

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Require API Key", color = colors.onSurface, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                Text("Protect endpoints with Authorization: Bearer <key>", color = colors.textSecondary, fontSize = 11.sp)
            }
            Switch(
                checked = requireAuth,
                onCheckedChange = {
                    requireAuth = it
                    if (it && apiKey.trim().length < 8) {
                        apiKey = ServerSettings.generateApiKey()
                    }
                },
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = colors.primary)
            )
        }

        if (requireAuth) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("Custom API Key (min 8 chars)") },
                    isError = keyTooShort,
                    singleLine = true,
                    colors = samuTextFieldColors(),
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(
                    onClick = { apiKey = ServerSettings.generateApiKey() },
                    shape = SaMuRadius.sm,
                    border = BorderStroke(1.dp, colors.primary),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 14.dp)
                ) {
                    Icon(Icons.Default.Key, null, tint = colors.primary, modifier = Modifier.size(16.dp))
                }
            }
            if (keyTooShort) {
                Text("API key must be at least 8 characters.", color = colors.error, fontSize = 11.sp)
            }
        }
    }
}

// ==================== 4. MODEL SETTINGS MODAL ====================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModelSettingsModal(
    entry: ModelEntry,
    hardware: HardwareProfile?,
    onPickExtraFile: (Uri, ExtraFileRole, (ExtraModelFile) -> Unit) -> Unit,
    onSave: (ModelProfile) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalSaMuColors.current
    var kind by remember { mutableStateOf(entry.profile.kind) }
    var mmprojPath by remember { mutableStateOf(entry.profile.mmprojPath) }
    var thinkingMode by remember { mutableStateOf(entry.profile.thinkingMode) }
    var gpuLayersText by remember { mutableStateOf(entry.profile.gpuLayers.toString()) }
    var extraFiles by remember { mutableStateOf(entry.profile.extraFiles) }
    var selectedRole by remember { mutableStateOf(ExtraFileRole.VAE) }
    var customArgs by remember { mutableStateOf(entry.profile.customArgs) }

    val mmprojPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            onPickExtraFile(uri, ExtraFileRole.OTHER) { copied ->
                mmprojPath = copied.path
            }
        }
    }

    val extraPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            onPickExtraFile(uri, selectedRole) { copied ->
                extraFiles = extraFiles + copied
            }
        }
    }

    SaMuModalShell(
        title = "Model Settings",
        subtitle = entry.displayName,
        onDismiss = onDismiss,
        onSave = {
            onSave(
                entry.profile.copy(
                    kind = kind,
                    mmprojPath = mmprojPath,
                    thinkingMode = thinkingMode,
                    gpuLayers = gpuLayersText.toIntOrNull() ?: -1,
                    extraFiles = extraFiles,
                    customArgs = customArgs.trim()
                )
            )
            onDismiss()
        }
    ) {
        if (hardware != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(SaMuRadius.sm)
                    .background(colors.background)
                    .border(1.dp, colors.divider, SaMuRadius.sm)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Memory, null, tint = colors.primary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(hardware.tierLabel, color = colors.textSecondary, fontSize = 11.5.sp)
            }
        }

        SectionLabel("Model Type")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ModelKind.entries.forEach { mk ->
                val active = kind == mk
                Box(
                    modifier = Modifier
                        .clip(SaMuRadius.sm)
                        .background(if (active) colors.primary else colors.background)
                        .border(1.dp, if (active) colors.primary else colors.divider, SaMuRadius.sm)
                        .clickable { kind = mk }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = mk.label,
                        color = if (active) Color.White else colors.onSurface,
                        fontSize = 12.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }

        HorizontalDivider(color = colors.divider)
        SectionLabel("Vision / Audio Projector (mmproj)")
        if (!mmprojPath.isNullOrBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(SaMuRadius.sm)
                    .background(colors.background)
                    .border(1.dp, colors.primary.copy(alpha = 0.4f), SaMuRadius.sm)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = File(mmprojPath!!).name,
                    color = colors.onSurface,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { mmprojPath = null }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Delete, "Clear mmproj", tint = colors.error, modifier = Modifier.size(16.dp))
                }
            }
        }
        OutlinedButton(
            onClick = { mmprojPicker.launch(arrayOf("*/*")) },
            shape = SaMuRadius.sm,
            border = BorderStroke(1.dp, colors.divider),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.UploadFile, null, tint = colors.primary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                if (mmprojPath.isNullOrBlank()) "Pick mmproj-*.gguf" else "Change mmproj file",
                color = colors.onSurface,
                fontSize = 12.5.sp
            )
        }

        HorizontalDivider(color = colors.divider)
        SectionLabel("Thinking Support Override")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            ThinkingOverride.entries.forEach { ov ->
                val active = thinkingMode == ov
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(SaMuRadius.sm)
                        .background(if (active) colors.surfaceVariant else colors.background)
                        .border(1.dp, if (active) colors.primary else colors.divider, SaMuRadius.sm)
                        .clickable { thinkingMode = ov }
                        .padding(vertical = 7.dp, horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = ov.label,
                        color = if (active) colors.primary else colors.onSurface,
                        fontSize = 11.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        OutlinedTextField(
            value = gpuLayersText,
            onValueChange = { s ->
                val filtered = s.filterIndexed { idx, c -> c.isDigit() || (idx == 0 && c == '-') }
                gpuLayersText = filtered.take(4)
            },
            label = { Text("GPU Offload Layers (-1 = Auto, 0 = CPU)") },
            singleLine = true,
            colors = samuTextFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )

        HorizontalDivider(color = colors.divider)
        SectionLabel("Extra Files (VAE / CLIP / T5 / LoRA / Vocoder)")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ExtraFileRole.entries.forEach { role ->
                val active = selectedRole == role
                Box(
                    modifier = Modifier
                        .clip(SaMuRadius.xs)
                        .background(if (active) colors.primary else colors.background)
                        .border(1.dp, if (active) colors.primary else colors.divider, SaMuRadius.xs)
                        .clickable { selectedRole = role }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = role.label,
                        color = if (active) Color.White else colors.textSecondary,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        OutlinedButton(
            onClick = { extraPicker.launch(arrayOf("*/*")) },
            shape = SaMuRadius.sm,
            border = BorderStroke(1.dp, colors.divider),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.UploadFile, null, tint = colors.primary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Attach ${selectedRole.label} File", color = colors.onSurface, fontSize = 12.5.sp)
        }
        extraFiles.forEach { ef ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(SaMuRadius.sm)
                    .background(colors.background)
                    .border(1.dp, colors.divider, SaMuRadius.sm)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(SaMuRadius.xs)
                        .background(colors.success.copy(alpha = 0.16f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(ef.role.label, color = colors.success, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = ef.displayName,
                    color = colors.onSurface,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { extraFiles = extraFiles.filter { it.id != ef.id } },
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(Icons.Default.Delete, "Remove", tint = colors.error, modifier = Modifier.size(15.dp))
                }
            }
        }

        OutlinedTextField(
            value = customArgs,
            onValueChange = { customArgs = it },
            label = { Text("Custom Engine Flags (optional)") },
            placeholder = { Text("e.g. --jinja --flash-attn") },
            singleLine = true,
            colors = samuTextFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ==================== 5. SELECT TEXT MODAL ====================

@Composable
fun SelectTextModal(
    text: String,
    onDismiss: () -> Unit,
    onCopied: () -> Unit
) {
    val colors = LocalSaMuColors.current
    val clipboard = LocalClipboardManager.current

    SaMuModalShell(
        title = "Select Text",
        subtitle = "Long-press to highlight or copy all",
        onDismiss = onDismiss,
        saveLabel = "Copy All",
        onSave = {
            clipboard.setText(AnnotatedString(text))
            onCopied()
            onDismiss()
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(SaMuRadius.sm)
                .background(colors.background)
                .border(1.dp, colors.divider, SaMuRadius.sm)
                .padding(12.dp)
        ) {
            SelectionContainer {
                Text(
                    text = text,
                    color = colors.onSurface,
                    fontSize = 13.5.sp,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

// ==================== 6. IMAGE LIGHTBOX MODAL ====================

@Composable
fun ImageLightboxModal(
    imagePath: String,
    fileName: String,
    onDismiss: () -> Unit
) {
    var bmp by remember(imagePath) { mutableStateOf<Bitmap?>(null) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val sizeStr = remember(imagePath) { AtomicStorage.formatBytes(File(imagePath).length()) }

    LaunchedEffect(imagePath) {
        bmp = withContext(Dispatchers.IO) {
            try { BitmapFactory.decodeFile(imagePath) } catch (_: Exception) { null }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, gestureZoom, _ ->
                        zoom = (zoom * gestureZoom).coerceIn(1f, 5f)
                        offset = if (zoom > 1f) offset + pan else Offset.Zero
                    }
                }
        ) {
            if (bmp != null) {
                Image(
                    bitmap = bmp!!.asImageBitmap(),
                    contentDescription = fileName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .graphicsLayer(
                            scaleX = zoom,
                            scaleY = zoom,
                            translationX = offset.x,
                            translationY = offset.y
                        )
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(40.dp)
                    .clip(SaMuRadius.full)
                    .background(Color.Black.copy(alpha = 0.6f))
            ) {
                Icon(Icons.Default.Close, "Close", tint = Color.White)
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
                    .clip(SaMuRadius.xl)
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "$fileName · $sizeStr",
                    color = Color.White,
                    fontSize = 12.sp
                )
            }
        }
    }
}

// ==================== 9. RENAME & CONFIRM DELETE MODALS ====================

@Composable
fun RenameChatModal(
    currentTitle: String,
    onRename: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(currentTitle) }
    SaMuModalShell(
        title = "Rename Chat",
        onDismiss = onDismiss,
        saveEnabled = title.isNotBlank(),
        onSave = {
            onRename(title.trim())
            onDismiss()
        }
    ) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Chat Title") },
            singleLine = true,
            colors = samuTextFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun ConfirmDeleteModal(
    title: String,
    message: String,
    confirmLabel: String = "Delete",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalSaMuColors.current
    SaMuModalShell(
        title = title,
        onDismiss = onDismiss,
        saveLabel = confirmLabel,
        isDestructive = true,
        onSave = {
            onConfirm()
            onDismiss()
        }
    ) {
        Text(
            text = message,
            color = colors.onSurface,
            fontSize = 13.5.sp,
            lineHeight = 20.sp
        )
    }
}

// ==================== 10. FLOATING BOTTOM TOAST PILL ====================

@Composable
fun SaMuToastPill(
    message: String?,
    modifier: Modifier = Modifier
) {
    val colors = LocalSaMuColors.current
    AnimatedVisibility(
        visible = !message.isNullOrBlank(),
        enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .clip(SaMuRadius.xl)
                .background(colors.surfaceVariant)
                .border(1.dp, colors.primary, SaMuRadius.xl)
                .padding(horizontal = 16.dp, vertical = 9.dp)
        ) {
            Text(
                text = message.orEmpty(),
                color = colors.onSurface,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
