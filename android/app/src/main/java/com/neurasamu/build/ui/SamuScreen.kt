package com.neurasamu.build.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.neurasamu.build.data.ApiKey
import com.neurasamu.build.data.AppSettings
import com.neurasamu.build.model.SamuModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SamuRoot(vm: SamuViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showSelfTest by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showApiKeys by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { vm.importModel(it) } }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(16.dp))
                Text("SaMu Lab", Modifier.padding(16.dp),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold)
                Text("NeuraSamu", Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary)
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                DrawerItem("Server settings", Icons.Default.Settings) {
                    scope.launch { drawerState.close() }
                    showSettings = true
                }
                DrawerItem("API keys", Icons.Default.Key) {
                    scope.launch { drawerState.close() }
                    showApiKeys = true
                }
                DrawerItem("Self-test", Icons.Default.BugReport) {
                    scope.launch { drawerState.close() }
                    vm.runSelfTest(); showSelfTest = true
                }
                DrawerItem("About", Icons.Default.Info) {
                    scope.launch { drawerState.close() }
                    showSettings = true
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("SaMu Lab", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, "menu")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = { picker.launch(arrayOf("*/*")) },
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("Add model") }
                )
            }
        ) { pad ->
            Column(
                Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp)
            ) {
                ServerCard(state, vm, ctx)
                Spacer(Modifier.height(12.dp))
                Text("Models", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.models, key = { it.id }) { m ->
                        ModelRow(m, state.active?.id == m.id, vm)
                    }
                }
                state.message?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary)
                }
                val err = state.errorDetail
                if (err != null) {
                    Spacer(Modifier.height(8.dp))
                    ErrorBox(err, onDismiss = vm::clearError)
                }
                Spacer(Modifier.height(80.dp))
            }
        }
    }

    if (showSelfTest) {
        SelfTestDialog(state.selfTestResult, state.busy, onDismiss = { showSelfTest = false })
    }
    if (showSettings) {
        SettingsDialog(state.settings, onSave = { vm.updateSettings(it); showSettings = false },
            onDismiss = { showSettings = false })
    }
    if (showApiKeys) {
        ApiKeysDialog(state.apiKeys, onAdd = vm::addApiKey,
            onDelete = vm::deleteApiKey, onToggle = vm::toggleApiKey,
            onDismiss = { showApiKeys = false })
    }
}

@Composable
private fun DrawerItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
                       onClick: () -> Unit) {
    NavigationDrawerItem(
        label = { Text(label) },
        icon = { Icon(icon, null) },
        selected = false,
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
    )
}

@Composable
private fun ServerCard(s: UiState, vm: SamuViewModel, ctx: Context) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(
                    if (s.serverRunning) Color(0xFF22C55E) else Color(0xFF6B7280),
                    RoundedCornerShape(50)))
                Spacer(Modifier.width(8.dp))
                Text(if (s.serverRunning) "Server running" else "Server stopped",
                    fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                if (s.active != null) {
                    Text("Port ${s.settings.port}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
            if (s.active != null) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = s.modelName,
                    onValueChange = vm::updateModelName,
                    label = { Text("Model name (API)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (s.serverRunning) {
                Spacer(Modifier.height(8.dp))
                CopyRow("URL", s.url, ctx)
                Spacer(Modifier.height(4.dp))
                val activeKey = s.apiKeys.firstOrNull { it.enabled }?.key
                CopyRow("Key", activeKey ?: "(auth OFF — no keys)", ctx)
            Spacer(Modifier.height(12.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { if (s.serverRunning) vm.stopServer() else vm.startServer() },
                    enabled = s.active != null,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(if (s.serverRunning) Icons.Default.Stop else Icons.Default.PlayArrow, null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (s.serverRunning) "Stop" else "Start")
                }
                if (s.active != null && !s.serverRunning) {
                    OutlinedButton(onClick = { vm.unloadModel() }) { Text("Unload") }
                }
            }
            if (s.busy) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Spacer(Modifier.height(4.dp))
                Text(s.busyLabel, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ErrorBox(detail: String, onDismiss: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF3B1212)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("Error", color = Color(0xFFFF8080), fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(detail, fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                color = Color(0xFFEDEDED), maxLines = 12)
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = onDismiss) { Text("Dismiss") }
        }
    }
}

@Composable
private fun CopyRow(label: String, value: String, ctx: Context) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$label: ", fontWeight = FontWeight.Medium)
        Text(value, fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f), maxLines = 1)
        IconButton(onClick = {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText(label, value))
        }) { Icon(Icons.Default.ContentCopy, "copy") }
    }
}

@Composable
private fun ModelRow(m: SamuModel, active: Boolean, vm: SamuViewModel) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(m.displayName, fontWeight = FontWeight.SemiBold)
                Text("%.1f MB".format(m.sizeBytes / 1_048_576.0),
                    style = MaterialTheme.typography.bodySmall)
                if (active) Text("ACTIVE", color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelSmall)
            }
            TextButton(onClick = { vm.loadModel(m) }) { Text("Load") }
            IconButton(onClick = { vm.deleteModel(m) }) { Icon(Icons.Default.Delete, null) }
        }
    }
}

@Composable
private fun SelfTestDialog(result: String?, busy: Boolean, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("SamuEngine self-test") },
        text = {
            if (busy || result == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Running…")
                }
            } else {
                Box(Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                    Text(result, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsDialog(s: AppSettings, onSave: (AppSettings) -> Unit,
                           onDismiss: () -> Unit) {
    var port by remember { mutableStateOf(s.port.toString()) }
    var lan by remember { mutableStateOf(s.lanEnabled) }
    var autoStart by remember { mutableStateOf(s.autoStart) }
    var parallel by remember { mutableStateOf(s.parallelSlots.toString()) }
    var temp by remember { mutableStateOf(s.defaultTemperature.toString()) }
    var topP by remember { mutableStateOf(s.defaultTopP.toString()) }
    var maxTok by remember { mutableStateOf(s.defaultMaxTokens.toString()) }
    var template by remember { mutableStateOf(s.chatTemplate) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Server settings") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).heightIn(max = 500.dp)) {
                OutlinedTextField(value = port, onValueChange = { port = it.filter { c -> c.isDigit() } },
                    label = { Text("Port") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = lan, onCheckedChange = { lan = it })
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("LAN access")
                        Text("Off = 127.0.0.1 only", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = autoStart, onCheckedChange = { autoStart = it })
                    Spacer(Modifier.width(8.dp))
                    Text("Auto-start on boot")
                }
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                Text("Generation defaults", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = parallel,
                    onValueChange = { parallel = it.filter { c -> c.isDigit() } },
                    label = { Text("Parallel slots (1-4)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = temp, onValueChange = { temp = it },
                    label = { Text("Temperature") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = topP, onValueChange = { topP = it },
                    label = { Text("Top-P") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = maxTok, onValueChange = { maxTok = it.filter { c -> c.isDigit() } },
                    label = { Text("Max tokens") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                Text("Chat template", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                listOf("auto", "gemma", "qwen", "llama3", "phi", "chatml").forEach { opt ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = template == opt, onClick = { template = opt })
                        Text(opt)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(AppSettings(
                    port = port.toIntOrNull() ?: 8080,
                    lanEnabled = lan,
                    autoStart = autoStart,
                    parallelSlots = (parallel.toIntOrNull() ?: 1).coerceIn(1, 4),
                    defaultTemperature = temp.toFloatOrNull() ?: 0.7f,
                    defaultTopP = topP.toFloatOrNull() ?: 0.9f,
                    defaultMaxTokens = maxTok.toIntOrNull() ?: 512,
                    chatTemplate = template
                ))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ApiKeysDialog(keys: List<ApiKey>, onAdd: (String) -> Unit,
                          onDelete: (String) -> Unit, onToggle: (String) -> Unit,
                          onDismiss: () -> Unit) {
    var newLabel by remember { mutableStateOf("") }
    val ctx = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("API keys") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).heightIn(max = 500.dp)) {
                Text("Empty list = no auth required", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                keys.forEach { k ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(k.label, fontWeight = FontWeight.SemiBold)
                                Text(k.key, fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp, maxLines = 1)
                            }
                            Switch(checked = k.enabled, onCheckedChange = { onToggle(k.id) })
                            IconButton(onClick = { onDelete(k.id) }) {
                                Icon(Icons.Default.Delete, null)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(value = newLabel, onValueChange = { newLabel = it },
                    label = { Text("Label (e.g. Phone B)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Button(onClick = { onAdd(newLabel); newLabel = "" },
                    modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Generate key")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
