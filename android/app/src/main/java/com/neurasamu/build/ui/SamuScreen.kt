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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.neurasamu.build.model.SamuModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SamuRoot(vm: SamuViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { vm.importModel(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SaMu Lab", fontWeight = FontWeight.Bold) },
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
            Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(horizontal = 16.dp)
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
            Spacer(Modifier.height(80.dp))
        }
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
                Box(
                    Modifier
                        .size(10.dp)
                        .background(
                            if (s.serverRunning) Color(0xFF22C55E) else Color(0xFF6B7280),
                            RoundedCornerShape(50)
                ))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (s.serverRunning) "Server running" else "Server stopped",
                    fontWeight = FontWeight.SemiBold
                )
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
                    Icon(
                        if (s.serverRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                        null
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (s.serverRunning) "Stop" else "Start")
                }
            }
            if (s.busy) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun CopyRow(label: String, value: String, ctx: Context) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$label: ", fontWeight = FontWeight.Medium)
        Text(
            value,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f),
            maxLines = 1
        )
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
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(m.displayName, fontWeight = FontWeight.SemiBold)
                Text(
                    "%.1f MB".format(m.sizeBytes / 1_048_576.0),
                    style = MaterialTheme.typography.bodySmall
                )
                if (active) Text("ACTIVE", color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelSmall)
            }
            TextButton(onClick = { vm.loadModel(m) }) { Text("Load") }
            IconButton(onClick = { vm.delete(m) }) { Icon(Icons.Default.Delete, null) }
        }
    }
}
