package com.neurasamu.build

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.neurasamu.build.data.AppTab
import com.neurasamu.build.data.ServerRunState
import com.neurasamu.build.data.ThemeMode
import com.neurasamu.build.server.ServerMonitor
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
            val systemDark = isSystemInDarkTheme()
            val isDark = when (state.chatPrefs.themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> systemDark
            }

            SideEffect {
                val style = if (isDark) {
                    SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                } else {
                    SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }

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
                activeTab = state.activeTab,
                isStreamingChat = state.isStreamingChat,
                serverState = state.serverState,
                port = state.settings.port,
                onSelectTab = { SaMuRepository.setTab(it) },
                onOpenGuide = { showGuide = true },
                onToggleTheme = { SaMuRepository.cycleThemeQuick() }
            )

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
    activeTab: AppTab,
    isStreamingChat: Boolean,
    serverState: ServerRunState,
    port: Int,
    onSelectTab: (AppTab) -> Unit,
    onOpenGuide: () -> Unit,
    onToggleTheme: () -> Unit
) {
    val colors = LocalSaMuColors.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val isPhoneNarrow = screenWidthDp < 540
    val monitor by ServerMonitor.snapshot.collectAsState()

    val effectiveServerState = when {
        serverState == ServerRunState.STOPPED -> ServerRunState.STOPPED
        serverState == ServerRunState.STARTING -> ServerRunState.STARTING
        monitor.activeRequests > 0 -> ServerRunState.PROCESSING
        else -> ServerRunState.RUNNING
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 1. LEFT ZONE: App Icon (+ brand label only when space allows)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(colors.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = "SaMu Lab",
                        modifier = Modifier.size(25.dp)
                    )
                }
                if (!isPhoneNarrow) {
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = "SaMu Lab",
                        color = colors.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // 2. EXACT CENTER ZONE: Pill Switcher [ Chat | Server ] (never collides!)
            Row(
                modifier = Modifier
                    .clip(SaMuRadius.xl)
                    .background(colors.background)
                    .border(1.dp, colors.divider, SaMuRadius.xl)
                    .padding(3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                val chatActive = activeTab == AppTab.CHAT
                val serverActive = activeTab == AppTab.SERVER

                Row(
                    modifier = Modifier
                        .clip(SaMuRadius.xl)
                        .background(if (chatActive) colors.primary else Color.Transparent)
                        .clickable { onSelectTab(AppTab.CHAT) }
                        .padding(horizontal = 11.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Forum,
                        contentDescription = "Chat",
                        tint = if (chatActive) Color.White else colors.textSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Chat",
                        color = if (chatActive) Color.White else colors.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    if (isStreamingChat) {
                        Spacer(Modifier.width(4.dp))
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(SaMuRadius.full)
                                .background(if (chatActive) Color.White else colors.primary)
                        )
                    }
                }

                val serverDotColor = when (effectiveServerState) {
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
                        .padding(horizontal = 11.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Dns,
                        contentDescription = "Server",
                        tint = if (serverActive) Color.White else colors.textSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Server",
                        color = if (serverActive) Color.White else colors.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    if (serverDotColor != null) {
                        Spacer(Modifier.width(4.dp))
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(SaMuRadius.full)
                                .background(
                                    if (serverActive && serverDotColor == colors.primary) Color.White
                                    else serverDotColor
                                )
                        )
                    }
                }
            }

            // 3. RIGHT ZONE: Compact Status Badge + Quick Guide + Theme Toggle
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                val badgeDot = when (effectiveServerState) {
                    ServerRunState.PROCESSING -> colors.primary
                    ServerRunState.RUNNING -> colors.success
                    ServerRunState.STARTING -> colors.warning
                    ServerRunState.STOPPED -> colors.textTertiary
                }
                val badgeText = when (effectiveServerState) {
                    ServerRunState.PROCESSING ->
                        if (isPhoneNarrow) "${String.format("%.1f", monitor.liveTokensPerSec)}t/s"
                        else "Busy · ${String.format("%.1f", monitor.liveTokensPerSec)} t/s"
                    ServerRunState.RUNNING ->
                        if (isPhoneNarrow) ":$port" else "Online :$port"
                    ServerRunState.STARTING -> "..."
                    ServerRunState.STOPPED ->
                        if (isPhoneNarrow) "Off" else "Offline"
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
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = badgeText,
                        color = colors.onSurface,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }

                IconButton(onClick = onOpenGuide, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.HelpOutline, "Quick Guide", tint = colors.textSecondary, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onToggleTheme, modifier = Modifier.size(32.dp)) {
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
