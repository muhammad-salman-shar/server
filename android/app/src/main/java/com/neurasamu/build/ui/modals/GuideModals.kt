package com.neurasamu.build.ui.modals

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neurasamu.build.ui.theme.LocalSaMuColors
import com.neurasamu.build.ui.theme.SaMuRadius

// ==================== 7. QUICK GUIDE MODAL ====================

@Composable
fun QuickGuideModal(onDismiss: () -> Unit) {
    val colors = LocalSaMuColors.current
    val items = listOf(
        "Dual Live Tabs [Chat | Server]" to "Switch freely anytime — local chat and LAN API serving run simultaneously without stopping.",
        "Mode Switcher (Chat / Image / Audio / Video)" to "Use the compact pill next to the input bar to switch between text/vision chat, image gen, TTS audio, and video gen.",
        "Thinking Effort & Live Skip" to "Pick Auto, Off, Low, High, Max, or Ultra in the top-left pill; tap 'Skip Thinking' mid-stream for an instant direct answer.",
        "Universal Offline File Attach" to "Attach images, PDFs, DOCX/XLSX/PPTX/EPUB, audio, or any code/text file — parsed 100% offline on device.",
        "Model Settings & mmproj" to "Tap the Tune icon on any model to set type (LLM/Image/Audio/Video), attach mmproj for Vision, or add VAE/CLIP/LoRA files.",
        "Auto Native Context" to "Adding or loading a GGUF model reads its exact binary header context so models never crash from out-of-range context.",
        "This Device vs Other Device URLs" to "Use 127.0.0.1 locally or switch to Other Device to copy your Wi-Fi LAN URL and view the Setup Guide.",
        "Custom API Key Security" to "Enable Require API Key in Server Settings to lock all endpoints behind Bearer authentication.",
        "Real-Time Monitor" to "Watch active requests, live tokens/sec, total tokens, and color-coded engine logs on the Server tab."
    )

    SaMuModalShell(
        title = "SaMu Lab Quick Guide",
        subtitle = "Universal Local AI Studio v1.1.0",
        onDismiss = onDismiss,
        showFooter = false
    ) {
        items.forEach { (heading, desc) ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(SaMuRadius.sm)
                    .background(colors.background)
                    .border(1.dp, colors.divider, SaMuRadius.sm)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(heading, color = colors.primary, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                Text(desc, color = colors.onSurface, fontSize = 12.sp, lineHeight = 17.sp)
            }
        }
    }
}

// ==================== 8. CONNECT OTHER DEVICE MODAL ====================

@Composable
fun ConnectOtherDeviceModal(
    port: Int,
    lanIp: String,
    lanAccess: Boolean,
    requireAuth: Boolean,
    onCopied: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalSaMuColors.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Wi-Fi / Hotspot", "USB Debugging", "Remote Tunnel")

    SaMuModalShell(
        title = "Connect Other Device",
        subtitle = "Use SaMu Lab from a laptop, tablet, or another phone",
        onDismiss = onDismiss,
        showFooter = false
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(SaMuRadius.sm)
                .background(colors.background)
                .border(1.dp, colors.divider, SaMuRadius.sm)
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            tabs.forEachIndexed { idx, label ->
                val active = selectedTab == idx
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(SaMuRadius.xs)
                        .background(if (active) colors.primary else Color.Transparent)
                        .clickable { selectedTab = idx }
                        .padding(vertical = 7.dp, horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (active) Color.White else colors.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1
                    )
                }
            }
        }

        when (selectedTab) {
            0 -> {
                if (!lanAccess) {
                    WarningBanner("LAN Access is OFF in Server Settings. Enable it and restart the server so other devices can reach this phone.")
                }
                if (lanIp == "127.0.0.1") {
                    WarningBanner("No Wi-Fi / Hotspot IP detected (currently 127.0.0.1). Connect both devices to the same Wi-Fi or turn on Mobile Hotspot, then tap Refresh IP.")
                }
                CopyableSnippetCard(
                    label = "OpenAI-Compatible Base URL",
                    code = "http://$lanIp:$port/v1",
                    onCopied = onCopied
                )
                CopyableSnippetCard(
                    label = "Quick curl health check",
                    code = "curl http://$lanIp:$port/health",
                    onCopied = onCopied
                )
            }

            1 -> {
                Text(
                    text = "Connect this phone via USB cable with USB Debugging enabled, then run this command on your PC/laptop:",
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
                CopyableSnippetCard(
                    label = "1. Forward port over USB (ADB)",
                    code = "adb forward tcp:$port tcp:$port",
                    onCopied = onCopied
                )
                CopyableSnippetCard(
                    label = "Reverse (if laptop hosts client)",
                    code = "adb reverse tcp:$port tcp:$port",
                    onCopied = onCopied
                )
                CopyableSnippetCard(
                    label = "2. Localhost Base URL on PC",
                    code = "http://127.0.0.1:$port/v1",
                    onCopied = onCopied
                )
            }

            2 -> {
                if (!requireAuth) {
                    WarningBanner("Security Warning: Require API Key is OFF! Turn on an API key in Server Settings before exposing your server over a public tunnel.")
                }
                CopyableSnippetCard(
                    label = "SSH Quick Tunnel (localhost.run)",
                    code = "ssh -R 80:127.0.0.1:$port nokey@localhost.run",
                    onCopied = onCopied
                )
                CopyableSnippetCard(
                    label = "Cloudflare Quick Tunnel",
                    code = "cloudflared tunnel --url http://127.0.0.1:$port",
                    onCopied = onCopied
                )
                CopyableSnippetCard(
                    label = "Private Mesh VPN (Tailscale / ZeroTier)",
                    code = "http://<your-tailscale-ip>:$port/v1",
                    onCopied = onCopied
                )
            }
        }
    }
}

@Composable
private fun WarningBanner(text: String) {
    val colors = LocalSaMuColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SaMuRadius.sm)
            .background(colors.warning.copy(alpha = 0.14f))
            .border(1.dp, colors.warning.copy(alpha = 0.5f), SaMuRadius.sm)
            .padding(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(Icons.Default.Warning, null, tint = colors.warning, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = colors.onSurface, fontSize = 11.5.sp, lineHeight = 16.sp)
    }
}

@Composable
private fun CopyableSnippetCard(
    label: String,
    code: String,
    onCopied: (String) -> Unit
) {
    val colors = LocalSaMuColors.current
    val clipboard = LocalClipboardManager.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SaMuRadius.sm)
            .background(colors.background)
            .border(1.dp, colors.divider, SaMuRadius.sm)
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = colors.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            IconButton(
                onClick = {
                    clipboard.setText(AnnotatedString(code))
                    onCopied("Copied to clipboard")
                },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Default.ContentCopy, "Copy", tint = colors.primary, modifier = Modifier.size(15.dp))
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = code,
            color = colors.codeText,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}
