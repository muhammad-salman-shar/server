package com.neurasamu.build.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
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
import com.neurasamu.build.model.SamuModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SamuRoot(vm: SamuViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var showLogs by remember { mutableStateOf(false) }
    var showSelfTest by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { vm.importModel(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SaMu Lab", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { vm.runSelfTest(); showSelfTest = true }) {
                        Icon(Icons.Default.BugReport, "self-test")
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
            val errDetail = state.errorDetail
            if (errDetail != null) {
                Spacer(Modifier.height(8.dp))
                ErrorBox(errDetail, onShowLogs = { vm.viewLogs(); showLogs = true }, onDismiss = vm::clearError)
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { vm.viewLogs(); showLogs = true }) {
                    Text("View engine logs")
                }
                TextButton(onClick = { vm.runSelfTest(); showSelfTest = true }) {
                    Text("Self-test")
                }
            }
            Spacer(Modifier.height(80.dp))
        }
    }

    if (showLogs) {
        LogsDialog(state.logTail, onDismiss = { showLogs = false }, onClear = vm::clearLogs, onRefresh = vm::viewLogs)
    }
    if (showSelfTest) {
        SelfTestDialog(state.selfTestResult, state.busy, onDismiss = { showSelfTest = false })
    }
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
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = s.modelName,
                onValueChange = vm::updateModelName,
                label = { Text("Model name (API)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = s.apiKey,
                onValueChange = vm::updateApiKey,
                label = { Text("API key") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (s.serverRunning) {
                Spacer(Modifier.height(8.dp))
                CopyRow("URL", s.url, ctx)
                Spacer(Modifier.height(4.dp))
                CopyRow("Key", s.apiKey, ctx)
            }
            Spacer(Modifier.height(12.dp))
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
private fun ErrorBox(detail: String, onShowLogs: () -> Unit, onDismiss: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF3B1212)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("Error", color = Color(0xFFFF8080), fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(detail, fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                color = Color(0xFFEDEDED), maxLines = 8)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onShowLogs) { Text("Full logs") }
                TextButton(onClick = onDismiss) { Text("Dismiss") }
            }
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
            IconButton(onClick = { vm.delete(m) }) { Icon(Icons.Default.Delete, null) }
        }
    }
}

@Composable
private fun LogsDialog(log: String, onDismiss: () -> Unit, onClear: () -> Unit, onRefresh: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Engine logs") },
        text = {
            Box(Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                Text(log.ifEmpty { "(empty)" }, fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp, modifier = Modifier.horizontalScroll(rememberScrollState()))
            }
        },
        confirmButton = { TextButton(onClick = onRefresh) { Text("Refresh") } },
        dismissButton = {
            Row {
                TextButton(onClick = onClear) { Text("Clear") }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    )
}

@Composable
private fun SelfTestDialog(result: String?, busy: Boolean, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("llama-server self-test") },
        text = {
            if (busy || result == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Running…")
                }
            } else {
                Box(Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                    Text(result, fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                        modifier = Modifier.horizontalScroll(rememberScrollState()))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
