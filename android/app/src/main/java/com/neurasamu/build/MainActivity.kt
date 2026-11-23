package com.neurasamu.build

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.neurasamu.build.data.AppTab
import com.neurasamu.build.data.ServerRunState
import com.neurasamu.build.state.SaMuAppState
import com.neurasamu.build.state.SaMuRepository
import com.neurasamu.build.ui.ChatScreen
import com.neurasamu.build.ui.ServerScreen
import com.neurasamu.build.ui.modals.QuickGuideModal
import com.neurasamu.build.ui.modals.SaMuToastPill
import com.neurasamu.build.ui.theme.LocalSaMuColors
import com.neurasamu.build.ui.theme.SaMuRadius
import com.neurasamu.build.ui.theme.SamuTheme

class MainActivity : ComponentActivity() {

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* optional */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        SaMuRepository.init(applicationContext)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            val state by SaMuRepository.state.collectAsState()
            SamuTheme(
                themeMode = state.chatPrefs.themeMode,
                fontScale = state.chatPrefs.fontScale
            ) {
                SaMuLabRoot(state = state)
            }
        }
    }
}

@Composable
private fun SaMuLabRoot(state: SaMuAppState) {
    val colors = LocalSaMuColors.current
    val stateHolder = rememberSaveableStateHolder()
    var showGuide by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            GlobalTopHeader(
                state = state,
                onSelectTab = { SaMuRepository.setTab(it) },
                onOpenGuide = { showGuide = true },
                onToggleTheme = { SaMuRepository.cycleThemeQuick() }
            )

            // Dual Live Body — state preserved across tab switches & backgrounding
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                stateHolder.SaveableStateProvider(key = state.activeTab.name) {
                    when (state.activeTab) {
                        AppTab.CHAT -> ChatScreen(state = state)
                        AppTab.SERVER -> ServerScreen(state = state)
                    }
                }
            }
        }

        SaMuToastPill(
            message = state.toastMessage,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 86.dp)
        )
    }

    if (showGuide) {
        QuickGuideModal(onDismiss = { showGuide = false })
    }
}

@Composable
private fun GlobalTopHeader(
    state: SaMuAppState,
    onSelectTab: (AppTab) -> Unit,
    onOpenGuide: () -> Unit,
    onToggleTheme: () -> Unit
) {
    val colors = LocalSaMuColors.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .statusBarsPadding()
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 10.dp)
        ) {
            val compactWidth = maxWidth < 400.dp

            // 1. LEFT ZONE: App Icon + SaMu Lab
            Row(
                modifier = Modifier.align(Alignment.CenterStart),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = "SaMu Lab",
                        modifier = Modifier.size(24.dp)
                    )
                }
                if (!compactWidth) {
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = "SaMu Lab",
                        color = colors.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // 2. EXACT CENTER ZONE: Pill Switcher [ Chat | Server ]
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(SaMuRadius.xl)
                    .background(colors.background)
                    .border(1.dp, colors.divider, SaMuRadius.xl)
                    .padding(3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                val chatActive = state.activeTab == AppTab.CHAT
                val serverActive = state.activeTab == AppTab.SERVER

                // Chat Tab Pill
                Row(
                    modifier = Modifier
                        .clip(SaMuRadius.xl)
                        .background(if (chatActive) colors.primary else Color.Transparent)
                        .clickable { onSelectTab(AppTab.CHAT) }
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Forum,
                        contentDescription = "Chat",
                        tint = if (chatActive) Color.White else colors.textSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = "Chat",
                        color = if (chatActive) Color.White else colors.textSecondary,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (state.isStreamingChat) {
                        Spacer(Modifier.width(5.dp))
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(SaMuRadius.full)
                                .background(if (chatActive) Color.White else colors.primary)
                        )
                    }
                }

                // Server Tab Pill
                val serverDotColor = when (state.serverState) {
                    ServerRunState.PROCESSING -> colors.primary
                    ServerRunState.RUNNING -> colors.success
                    ServerRunState.STARTING -> colors.warning
                    ServerRunState.STOPPED -> null
                }
                Row(
                    modifier = Modifier
                        .clip(SaMuRadius.xl)
                        .background(if (serverActive) colors.primary else Color.Transparent)
                        .clickable { onSelectTab(AppTab.SERVER) }
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Dns,
                        contentDescription = "Server",
                        tint = if (serverActive) Color.White else colors.textSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = "Server",
                        color = if (serverActive) Color.White else colors.textSecondary,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (serverDotColor != null) {
                        Spacer(Modifier.width(5.dp))
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(SaMuRadius.full)
                                .background(if (serverActive && serverDotColor == colors.primary) Color.White else serverDotColor)
                        )
                    }
                }
            }

            // 3. RIGHT ZONE: Compact Live Status Badge + Quick Guide + Theme Toggle
            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                val badgeDot = when (state.serverState) {
                    ServerRunState.PROCESSING -> colors.primary
                    ServerRunState.RUNNING -> colors.success
                    ServerRunState.STARTING -> colors.warning
                    ServerRunState.STOPPED -> colors.textTertiary
                }
                val badgeText = when (state.serverState) {
                    ServerRunState.PROCESSING -> "Busy · ${String.format("%.1f", state.monitor.liveTokensPerSec)} t/s"
                    ServerRunState.RUNNING -> "Online :${state.settings.port}"
                    ServerRunState.STARTING -> "Starting"
                    ServerRunState.STOPPED -> "Offline"
                }

                Row(
                    modifier = Modifier
                        .clip(SaMuRadius.xl)
                        .background(colors.background)
                        .border(1.dp, colors.divider, SaMuRadius.xl)
                        .clickable { onSelectTab(AppTab.SERVER) }
                        .padding(horizontal = 7.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(SaMuRadius.full)
                            .background(badgeDot)
                    )
                    if (!compactWidth) {
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = badgeText,
                            color = colors.onSurface,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                IconButton(onClick = onOpenGuide, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.HelpOutline, "Quick Guide", tint = colors.textSecondary, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onToggleTheme, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = if (colors.isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = "Toggle Theme",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        HorizontalDivider(thickness = 1.dp, color = colors.divider)
    }
}
