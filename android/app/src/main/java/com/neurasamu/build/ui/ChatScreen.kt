package com.neurasamu.build.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neurasamu.build.data.ApiConfig
import com.neurasamu.build.data.AppTab
import com.neurasamu.build.data.ChatAttachment
import com.neurasamu.build.data.ChatMessage
import com.neurasamu.build.data.ChatMode
import com.neurasamu.build.data.ChatSession
import com.neurasamu.build.data.GeneratedMedia
import com.neurasamu.build.data.ThinkingEffort
import com.neurasamu.build.state.SaMuAppState
import com.neurasamu.build.state.SaMuRepository
import com.neurasamu.build.ui.components.GeneratedMediaCard
import com.neurasamu.build.ui.components.MarkdownText
import com.neurasamu.build.ui.components.sharePlainText
import com.neurasamu.build.ui.modals.ApiEditModal
import com.neurasamu.build.ui.modals.ChatSettingsModal
import com.neurasamu.build.ui.modals.ConfirmDeleteModal
import com.neurasamu.build.ui.modals.ImageLightboxModal
import com.neurasamu.build.ui.modals.RenameChatModal
import com.neurasamu.build.ui.modals.SelectTextModal
import com.neurasamu.build.ui.theme.LocalFontScale
import com.neurasamu.build.ui.theme.LocalSaMuColors
import com.neurasamu.build.ui.theme.SaMuRadius
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ChatScreen(state: SaMuAppState) {
    val colors = LocalSaMuColors.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var inputText by remember { mutableStateOf("") }
    var pendingAttachments by remember { mutableStateOf<List<ChatAttachment>>(emptyList()) }

    // Modals state
    var editingApi by remember { mutableStateOf<ApiConfig?>(null) }
    var showAddApiModal by remember { mutableStateOf(false) }
    var deletingApi by remember { mutableStateOf<ApiConfig?>(null) }
    var showChatSettings by remember { mutableStateOf(false) }
    var renamingChat by remember { mutableStateOf<ChatSession?>(null) }
    var deletingChat by remember { mutableStateOf<ChatSession?>(null) }
    var showDeleteAllConfirm by remember { mutableStateOf(false) }
    var selectTextContent by remember { mutableStateOf<String?>(null) }
    var lightboxItem by remember { mutableStateOf<Pair<String, String>?>(null) }
    var pendingExportTranscript by remember { mutableStateOf<String?>(null) }
    var pendingDownloadMedia by remember { mutableStateOf<GeneratedMedia?>(null) }

    val exportTxtLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        val txt = pendingExportTranscript
        pendingExportTranscript = null
        if (uri != null && txt != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    ctx.contentResolver.openOutputStream(uri)?.use { it.write(txt.toByteArray(Charsets.UTF_8)) }
                    SaMuRepository.showToast("Transcript saved")
                } catch (e: Exception) {
                    SaMuRepository.showToast("Export failed: ${e.message}")
                }
            }
        }
    }

    val downloadMediaLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri: Uri? ->
        val media = pendingDownloadMedia
        pendingDownloadMedia = null
        if (uri != null && media != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val src = File(media.localPath)
                    ctx.contentResolver.openOutputStream(uri)?.use { out ->
                        src.inputStream().use { inp -> inp.copyTo(out) }
                    }
                    SaMuRepository.showToast("Saved ${media.fileName}")
                } catch (e: Exception) {
                    SaMuRepository.showToast("Save failed: ${e.message}")
                }
            }
        }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            SaMuRepository.importAttachment(uri) { att ->
                pendingAttachments = pendingAttachments + att
            }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            SaMuRepository.importAttachment(uri) { att ->
                pendingAttachments = pendingAttachments + att.copy(isImage = true)
            }
        }
    }

    val sidebarContent: @Composable (onCloseDrawer: () -> Unit) -> Unit = { closeDrawer ->
        ChatSidebarContent(
            state = state,
            onAddApi = { showAddApiModal = true },
            onNewChat = {
                SaMuRepository.createNewChat()
                closeDrawer()
            },
            onSelectApi = { id ->
                SaMuRepository.selectApi(id)
                closeDrawer()
            },
            onEditApi = { api -> editingApi = api },
            onDeleteApi = { api -> deletingApi = api },
            onSelectChat = { id ->
                SaMuRepository.selectChat(id)
                closeDrawer()
            },
            onRenameChat = { chat -> renamingChat = chat },
            onExportChat = { chat ->
                pendingExportTranscript = SaMuRepository.buildChatTranscript(chat.id)
                exportTxtLauncher.launch("${chat.title.take(24).replace(Regex("[^a-zA-Z0-9_-]"), "_")}.txt")
            },
            onDeleteChat = { chat -> deletingChat = chat },
            onDeleteAllChats = { showDeleteAllConfirm = true }
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(colors.background)) {
        val isWide = maxWidth >= 768.dp
        val drawerState = rememberDrawerState(DrawerValue.Closed)
        var wideSidebarOpen by remember { mutableStateOf(true) }

        val mainChatColumn: @Composable () -> Unit = {
            ChatMainPane(
                state = state,
                inputText = inputText,
                onInputTextChange = { inputText = it },
                pendingAttachments = pendingAttachments,
                onRemoveAttachment = { id -> pendingAttachments = pendingAttachments.filter { it.id != id } },
                onToggleSidebar = {
                    if (isWide) wideSidebarOpen = !wideSidebarOpen
                    else scope.launch {
                        if (drawerState.isOpen) drawerState.close() else drawerState.open()
                    }
                },
                onOpenSettings = { showChatSettings = true },
                onNewChat = { SaMuRepository.createNewChat() },
                onAttachAnyFile = { filePicker.launch(arrayOf("*/*")) },
                onAttachImage = { imagePicker.launch(arrayOf("image/*")) },
                onSend = {
                    val txt = inputText
                    val atts = pendingAttachments
                    inputText = ""
                    pendingAttachments = emptyList()
                    SaMuRepository.sendMessage(txt, atts)
                },
                onEditUserMessage = { msgId ->
                    val edited = SaMuRepository.editUserMessage(msgId)
                    if (edited != null) {
                        inputText = edited.first
                        pendingAttachments = edited.second
                    }
                },
                onOpenSelectText = { selectTextContent = it },
                onOpenLightbox = { path, name -> lightboxItem = path to name },
                onDownloadMedia = { media ->
                    pendingDownloadMedia = media
                    downloadMediaLauncher.launch(media.fileName)
                }
            )
        }

        if (isWide) {
            Row(modifier = Modifier.fillMaxSize()) {
                AnimatedVisibility(
                    visible = wideSidebarOpen,
                    enter = expandHorizontally(tween(220)),
                    exit = shrinkHorizontally(tween(220))
                ) {
                    Row(modifier = Modifier.width(280.dp).fillMaxHeight()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(colors.surface)
                        ) {
                            sidebarContent {}
                        }
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(colors.divider)
                        )
                    }
                }
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    mainChatColumn()
                }
            }
        } else {
            ModalNavigationDrawer(
                drawerState = drawerState,
                scrimColor = Color.Black.copy(alpha = 0.60f),
                drawerContent = {
                    ModalDrawerSheet(
                        drawerContainerColor = colors.surface,
                        windowInsets = WindowInsets(0),
                        modifier = Modifier.width(280.dp)
                    ) {
                        sidebarContent { scope.launch { drawerState.close() } }
                    }
                }
            ) {
                mainChatColumn()
            }
        }
    }

    // ==================== MODALS ====================

    if (showAddApiModal) {
        ApiEditModal(
            initial = null,
            onSave = { SaMuRepository.saveCustomApi(it) },
            onDismiss = { showAddApiModal = false }
        )
    }
    editingApi?.let { api ->
        ApiEditModal(
            initial = api,
            onSave = { SaMuRepository.saveCustomApi(it) },
            onDismiss = { editingApi = null }
        )
    }
    deletingApi?.let { api ->
        ConfirmDeleteModal(
            title = "Delete API",
            message = "Remove '${api.label}' from your saved APIs?",
            onConfirm = { SaMuRepository.deleteCustomApi(api.id) },
            onDismiss = { deletingApi = null }
        )
    }
    if (showChatSettings) {
        ChatSettingsModal(
            prefs = state.chatPrefs,
            onSave = { updated -> SaMuRepository.updateChatPrefs { updated } },
            onDismiss = { showChatSettings = false }
        )
    }
    renamingChat?.let { chat ->
        RenameChatModal(
            currentTitle = chat.title,
            onRename = { newTitle -> SaMuRepository.renameChat(chat.id, newTitle) },
            onDismiss = { renamingChat = null }
        )
    }
    deletingChat?.let { chat ->
        ConfirmDeleteModal(
            title = "Delete Chat",
            message = "Delete '${chat.title}' permanently?",
            onConfirm = { SaMuRepository.deleteChat(chat.id) },
            onDismiss = { deletingChat = null }
        )
    }
    if (showDeleteAllConfirm) {
        ConfirmDeleteModal(
            title = "Delete All Chats",
            message = "This will erase all ${state.chats.size} chat histories permanently.",
            confirmLabel = "Delete All",
            onConfirm = { SaMuRepository.deleteAllChats() },
            onDismiss = { showDeleteAllConfirm = false }
        )
    }
    selectTextContent?.let { txt ->
        SelectTextModal(
            text = txt,
            onDismiss = { selectTextContent = null },
            onCopied = { SaMuRepository.showToast("Copied all text") }
        )
    }
    lightboxItem?.let { (path, name) ->
        ImageLightboxModal(
            imagePath = path,
            fileName = name,
            onDismiss = { lightboxItem = null }
        )
    }
}

// ==================== SIDEBAR / DRAWER ====================

@Composable
private fun ChatSidebarContent(
    state: SaMuAppState,
    onAddApi: () -> Unit,
    onNewChat: () -> Unit,
    onSelectApi: (String) -> Unit,
    onEditApi: (ApiConfig) -> Unit,
    onDeleteApi: (ApiConfig) -> Unit,
    onSelectChat: (String) -> Unit,
    onRenameChat: (ChatSession) -> Unit,
    onExportChat: (ChatSession) -> Unit,
    onDeleteChat: (ChatSession) -> Unit,
    onDeleteAllChats: () -> Unit
) {
    val colors = LocalSaMuColors.current
    val scale = LocalFontScale.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface)
            .navigationBarsPadding()
    ) {
        // Header
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp)) {
            Text(
                text = "SaMu Chat",
                color = colors.onSurface,
                fontSize = (18f * scale).sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "NeuraSamu",
                color = colors.primary,
                fontSize = (11f * scale).sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onAddApi,
                    shape = SaMuRadius.sm,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.weight(1f).height(36.dp)
                ) {
                    Text("+ API", fontSize = (12.5f * scale).sp, fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = onNewChat,
                    shape = SaMuRadius.sm,
                    border = BorderStroke(1.dp, colors.primary),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.primary),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.weight(1f).height(36.dp)
                ) {
                    Text("+ Chat", fontSize = (12.5f * scale).sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        HorizontalDivider(thickness = 1.dp, color = colors.divider)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Section: APIS
            Text(
                text = "APIS",
                color = colors.textTertiary,
                fontSize = (10f * scale).sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )

            // Built-in synced local API always first
            val localApi = state.builtInLocalApi
            val localActive = state.activeApi.id == localApi.id
            SidebarSelectableRow(
                selected = localActive,
                onClick = { onSelectApi(localApi.id) }
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(SaMuRadius.full)
                        .background(if (state.isServerOnline) colors.success else colors.textTertiary)
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = localApi.label,
                        color = colors.onSurface,
                        fontSize = (12.5f * scale).sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${localApi.modelName} · ${if (state.isServerOnline) "Online" else "Stopped"}",
                        color = if (state.isServerOnline) colors.success else colors.textSecondary,
                        fontSize = (10.5f * scale).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Custom APIs
            state.customApis.forEach { api ->
                val isSelected = state.activeApi.id == api.id
                SidebarSelectableRow(
                    selected = isSelected,
                    onClick = { onSelectApi(api.id) }
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = api.label,
                            color = colors.onSurface,
                            fontSize = (12.5f * scale).sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = api.modelName,
                            color = colors.textSecondary,
                            fontSize = (10.5f * scale).sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = { onEditApi(api) }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Edit, "Edit API", tint = colors.textSecondary, modifier = Modifier.size(15.dp))
                    }
                    IconButton(onClick = { onDeleteApi(api) }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, "Delete API", tint = colors.error, modifier = Modifier.size(15.dp))
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(thickness = 1.dp, color = colors.divider)
            Spacer(Modifier.height(4.dp))

            // Section: CHATS
            val showDeleteAll = state.chats.size > 1 || state.activeChat.messages.isNotEmpty()
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "CHATS (${state.chats.size})",
                    color = colors.textTertiary,
                    fontSize = (10f * scale).sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp
                )
                if (showDeleteAll) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(SaMuRadius.xs)
                            .clickable(onClick = onDeleteAllChats)
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.Delete, null, tint = colors.error, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("Delete All", color = colors.error, fontSize = (10.5f * scale).sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            state.chats.forEach { chat ->
                val isSelected = state.activeChat.id == chat.id
                var menuOpen by remember { mutableStateOf(false) }

                SidebarSelectableRow(
                    selected = isSelected,
                    onClick = { onSelectChat(chat.id) }
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = chat.title,
                            color = colors.onSurface,
                            fontSize = (12.5f * scale).sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${chat.messages.size} messages",
                            color = colors.textTertiary,
                            fontSize = (10.5f * scale).sp
                        )
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.MoreVert, "Chat options", tint = colors.textSecondary, modifier = Modifier.size(16.dp))
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                            modifier = Modifier
                                .background(colors.surface)
                                .border(1.dp, colors.divider, SaMuRadius.sm)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Rename", color = colors.onSurface, fontSize = 13.sp) },
                                onClick = {
                                    menuOpen = false
                                    onRenameChat(chat)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Download as TXT", color = colors.onSurface, fontSize = 13.sp) },
                                onClick = {
                                    menuOpen = false
                                    onExportChat(chat)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete", color = colors.error, fontSize = 13.sp) },
                                onClick = {
                                    menuOpen = false
                                    onDeleteChat(chat)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SidebarSelectableRow(
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit
) {
    val colors = LocalSaMuColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SaMuRadius.sm)
            .background(if (selected) colors.surfaceVariant else Color.Transparent)
            .clickable(onClick = onClick)
            .heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(28.dp)
                    .background(colors.primary, SaMuRadius.xs)
            )
        } else {
            Spacer(Modifier.width(3.dp))
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

// ==================== MAIN CHAT PANE ====================

@Composable
private fun ChatMainPane(
    state: SaMuAppState,
    inputText: String,
    onInputTextChange: (String) -> Unit,
    pendingAttachments: List<ChatAttachment>,
    onRemoveAttachment: (String) -> Unit,
    onToggleSidebar: () -> Unit,
    onOpenSettings: () -> Unit,
    onNewChat: () -> Unit,
    onAttachAnyFile: () -> Unit,
    onAttachImage: () -> Unit,
    onSend: () -> Unit,
    onEditUserMessage: (String) -> Unit,
    onOpenSelectText: (String) -> Unit,
    onOpenLightbox: (String, String) -> Unit,
    onDownloadMedia: (GeneratedMedia) -> Unit
) {
    val colors = LocalSaMuColors.current
    val scale = LocalFontScale.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var autoScrollEnabled by remember { mutableStateOf(true) }
    var programmaticScroll by remember { mutableStateOf(false) }
    var prevIndex by remember { mutableIntStateOf(0) }
    var prevOffset by remember { mutableIntStateOf(0) }

    val messages = state.activeChat.messages
    val lastMsgContentLen = messages.lastOrNull()?.content?.length ?: 0

    // CRITICAL SMART AUTO-SCROLL DETECTOR (never fights user scrolling up)
    LaunchedEffect(listState) {
        snapshotFlow {
            Triple(
                listState.isScrollInProgress,
                listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset,
                listState.canScrollForward
            )
        }.collect { (inProgress, pos, canScrollFwd) ->
            val (idx, off) = pos
            if (inProgress && !programmaticScroll) {
                val scrolledUp = idx < prevIndex || (idx == prevIndex && off < prevOffset - 6)
                if (scrolledUp) {
                    autoScrollEnabled = false
                }
            }
            if (!canScrollFwd) {
                autoScrollEnabled = true
            }
            prevIndex = idx
            prevOffset = off
        }
    }

    // Reset auto-scroll when switching chat
    LaunchedEffect(state.activeChat.id) {
        autoScrollEnabled = true
        if (messages.isNotEmpty()) {
            programmaticScroll = true
            try {
                listState.scrollToItem(messages.size) // __bottom_anchor__
            } finally {
                programmaticScroll = false
            }
        }
    }

    // Scroll to __bottom_anchor__ only when autoScrollEnabled == true
    LaunchedEffect(messages.size, lastMsgContentLen) {
        if (autoScrollEnabled && messages.isNotEmpty()) {
            programmaticScroll = true
            try {
                listState.scrollToItem(messages.size)
            } finally {
                programmaticScroll = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .imePadding()
            .navigationBarsPadding()
    ) {
        // CHAT TOP BAR (56dp)
        ChatTopBar(
            state = state,
            onToggleSidebar = onToggleSidebar,
            onOpenSettings = onOpenSettings,
            onNewChat = onNewChat
        )

        // MESSAGE LIST
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (messages.isEmpty()) {
                ChatEmptyState(state = state)
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(messages, key = { it.id }) { msg ->
                        if (msg.role == "user") {
                            UserMessageItem(
                                msg = msg,
                                onEdit = { onEditUserMessage(msg.id) },
                                onSelectText = { onOpenSelectText(msg.content) },
                                onOpenLightbox = onOpenLightbox
                            )
                        } else {
                            val isStreamingThis = state.isStreamingChat && state.streamingMessageId == msg.id
                            AssistantMessageItem(
                                msg = msg,
                                isStreaming = isStreamingThis,
                                isSpeaking = state.speakingMessageId == msg.id,
                                onSkipThinking = { SaMuRepository.skipThinking() },
                                onRegenerate = { SaMuRepository.regenerateMessage(msg.id) },
                                onToggleSpeak = { SaMuRepository.toggleSpeakMessage(msg.id, msg.content) },
                                onSelectText = { onOpenSelectText(msg.content) },
                                onOpenLightbox = onOpenLightbox,
                                onDownloadMedia = onDownloadMedia
                            )
                        }
                    }
                    // 1dp invisible bottom anchor so streaming tall bubbles never jump to their top!
                    item(key = "__bottom_anchor__") {
                        Spacer(modifier = Modifier.height(1.dp))
                    }
                }
            }

            // Floating Jump-to-latest button whenever !autoScrollEnabled
            if (!autoScrollEnabled && messages.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(14.dp)
                        .size(40.dp)
                        .clip(SaMuRadius.full)
                        .background(colors.primary)
                        .clickable {
                            autoScrollEnabled = true
                            scope.launch {
                                programmaticScroll = true
                                try {
                                    listState.animateScrollToItem(messages.size)
                                } finally {
                                    programmaticScroll = false
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowDownward,
                        contentDescription = "Jump to latest",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // CHAT INPUT AREA
        ChatInputBar(
            mode = state.chatPrefs.chatMode,
            inputText = inputText,
            onInputTextChange = onInputTextChange,
            attachments = pendingAttachments,
            isStreaming = state.isStreamingChat,
            onSelectMode = { SaMuRepository.setChatMode(it) },
            onRemoveAttachment = onRemoveAttachment,
            onAttachAnyFile = onAttachAnyFile,
            onAttachImage = onAttachImage,
            onSend = {
                autoScrollEnabled = true
                onSend()
            },
            onStop = { SaMuRepository.stopStreaming() }
        )
    }
}

@Composable
private fun ChatTopBar(
    state: SaMuAppState,
    onToggleSidebar: () -> Unit,
    onOpenSettings: () -> Unit,
    onNewChat: () -> Unit
) {
    val colors = LocalSaMuColors.current
    val scale = LocalFontScale.current
    var thinkMenuOpen by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth().background(colors.surface)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp)
                .padding(horizontal = 6.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onToggleSidebar, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Menu, "Menu", tint = colors.onSurface, modifier = Modifier.size(22.dp))
            }

            // Collapsible Thinking Dropdown (ALWAYS visible in ChatMode.CHAT)
            if (state.chatPrefs.chatMode == ChatMode.CHAT) {
                Box {
                    Row(
                        modifier = Modifier
                            .clip(SaMuRadius.xl)
                            .background(colors.surfaceVariant)
                            .border(1.dp, colors.divider, SaMuRadius.xl)
                            .clickable { thinkMenuOpen = true }
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Psychology, null, tint = colors.primary, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Think: ${state.chatPrefs.thinkingEffort.shortLabel}",
                            color = colors.onSurface,
                            fontSize = (11f * scale).sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                        Icon(Icons.Default.KeyboardArrowDown, null, tint = colors.textSecondary, modifier = Modifier.size(15.dp))
                    }
                    DropdownMenu(
                        expanded = thinkMenuOpen,
                        onDismissRequest = { thinkMenuOpen = false },
                        modifier = Modifier
                            .widthIn(min = 240.dp)
                            .background(colors.surface)
                            .border(1.dp, colors.divider, SaMuRadius.sm)
                    ) {
                        ThinkingEffort.entries.forEach { eff ->
                            val active = state.chatPrefs.thinkingEffort == eff
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = eff.label,
                                                color = if (active) colors.primary else colors.onSurface,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = eff.subtitle,
                                                color = colors.textSecondary,
                                                fontSize = 11.sp
                                            )
                                        }
                                        if (active) {
                                            Icon(Icons.Default.Check, null, tint = colors.primary, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                },
                                onClick = {
                                    thinkMenuOpen = false
                                    SaMuRepository.setThinkingEffort(eff)
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.width(6.dp))
            }

            // Title + Subtitle
            Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
                Text(
                    text = state.activeChat.title,
                    color = colors.onSurface,
                    fontSize = (14.5f * scale).sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${state.activeApi.label} · ${state.activeApi.modelName}",
                    color = colors.primary,
                    fontSize = (10.5f * scale).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Token Context Badge
            val ctxLabel = if (state.settings.contextSize == 0) {
                "${state.liveChatTokensEstimate} / Max"
            } else {
                "${state.liveChatTokensEstimate}/${state.settings.contextSize}"
            }
            Box(
                modifier = Modifier
                    .clip(SaMuRadius.xl)
                    .background(colors.primary.copy(alpha = 0.14f))
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Text(
                    text = ctxLabel,
                    color = colors.primary,
                    fontSize = (10f * scale).sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace
                )
            }

            IconButton(onClick = onOpenSettings, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Default.Settings, "Chat Settings", tint = colors.textSecondary, modifier = Modifier.size(19.dp))
            }
            IconButton(onClick = onNewChat, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Default.Add, "New Chat", tint = colors.onSurface, modifier = Modifier.size(21.dp))
            }
        }
        HorizontalDivider(thickness = 1.dp, color = colors.divider)
    }
}

@Composable
private fun ChatEmptyState(state: SaMuAppState) {
    val colors = LocalSaMuColors.current
    val scale = LocalFontScale.current
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Ready to create & chat",
                color = colors.onSurface,
                fontSize = (18f * scale).sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = when (state.chatPrefs.chatMode) {
                    ChatMode.CHAT -> "Chat with text, vision, PDFs, Office docs, audio, and code — 100% local or via Custom API."
                    ChatMode.IMAGE -> "Describe an image or attach a reference photo for image-to-image generation."
                    ChatMode.AUDIO -> "Type any text to generate offline WAV speech immediately."
                    ChatMode.VIDEO -> "Describe a motion scene or attach a reference frame to generate video."
                },
                color = colors.textSecondary,
                fontSize = (13f * scale).sp
            )
            if (state.activeApi.isBuiltIn && !state.isServerOnline) {
                Spacer(Modifier.height(6.dp))
                Button(
                    onClick = { SaMuRepository.setTab(AppTab.SERVER) },
                    shape = SaMuRadius.sm,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = Color.White)
                ) {
                    Icon(Icons.Default.Dns, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Open Server Tab", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ==================== USER MESSAGE BUBBLE ====================

@Composable
private fun UserMessageItem(
    msg: ChatMessage,
    onEdit: () -> Unit,
    onSelectText: () -> Unit,
    onOpenLightbox: (String, String) -> Unit
) {
    val colors = LocalSaMuColors.current
    val scale = LocalFontScale.current
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1500L)
            copied = false
        }
    }

    val words = remember(msg.content) { msg.content.trim().split(Regex("\\s+")).filter { it.isNotEmpty() } }
    val isLong = words.size > 10
    val displayText = if (isLong && !expanded) words.take(10).joinToString(" ") + "..." else msg.content

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End
    ) {
        val maxBubbleW = LocalConfiguration.current.screenWidthDp.dp * 0.85f
        Column(
            modifier = Modifier
                .widthIn(max = maxBubbleW)
                    .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomStart = 14.dp, bottomEnd = 4.dp))
                    .background(colors.userBubble)
                    .border(
                        1.dp,
                        colors.userBubbleBorder,
                        RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomStart = 14.dp, bottomEnd = 4.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (msg.attachments.isNotEmpty()) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        msg.attachments.forEach { att ->
                            if (att.isImage) {
                                AttachmentImageThumb(
                                    path = att.path,
                                    sizeDp = 72,
                                    onClick = { onOpenLightbox(att.path, att.name) }
                                )
                            } else {
                                AttachmentFileChip(att = att)
                            }
                        }
                    }
                }
                if (msg.content.isNotBlank()) {
                    Text(
                        text = displayText,
                        color = colors.onSurface,
                        fontSize = (14f * scale).sp,
                        lineHeight = (14f * scale * 1.55f).sp
                    )
                    if (isLong) {
                        Text(
                            text = if (expanded) "Show less" else "Show more",
                            color = colors.primary,
                            fontSize = (12f * scale).sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable { expanded = !expanded }
                        )
                    }
                }
            }

        // User Icon-Only Action Bar (aligned END)
        Row(
            modifier = Modifier.padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BubbleActionIcon(Icons.Default.SelectAll, "Select text", onClick = onSelectText)
            BubbleActionIcon(
                icon = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                desc = "Copy",
                tint = if (copied) colors.success else colors.textTertiary,
                onClick = {
                    clipboard.setText(AnnotatedString(msg.content))
                    copied = true
                }
            )
            BubbleActionIcon(Icons.Default.Share, "Share", onClick = { sharePlainText(ctx, msg.content) })
            BubbleActionIcon(Icons.Default.Edit, "Edit", onClick = onEdit)
        }
    }
}

// ==================== ASSISTANT MESSAGE BUBBLE ====================

private data class ParsedThinking(
    val thinkingText: String,
    val answerText: String,
    val hasThinking: Boolean,
    val isStillThinking: Boolean
)

private fun splitThinkingContent(raw: String): ParsedThinking {
    val startIdx = raw.indexOf("<think>")
    if (startIdx < 0) {
        return ParsedThinking("", raw, hasThinking = false, isStillThinking = false)
    }
    val afterStart = startIdx + "<think>".length
    val endIdx = raw.indexOf("</think>", startIndex = afterStart)
    return if (endIdx < 0) {
        ParsedThinking(
            thinkingText = raw.substring(afterStart).trimStart(),
            answerText = raw.substring(0, startIdx).trim(),
            hasThinking = true,
            isStillThinking = true
        )
    } else {
        val think = raw.substring(afterStart, endIdx).trim()
        val before = raw.substring(0, startIdx)
        val after = raw.substring(endIdx + "</think>".length)
        ParsedThinking(
            thinkingText = think,
            answerText = (before + after).trim(),
            hasThinking = true,
            isStillThinking = false
        )
    }
}

@Composable
private fun AssistantMessageItem(
    msg: ChatMessage,
    isStreaming: Boolean,
    isSpeaking: Boolean,
    onSkipThinking: () -> Unit,
    onRegenerate: () -> Unit,
    onToggleSpeak: () -> Unit,
    onSelectText: () -> Unit,
    onOpenLightbox: (String, String) -> Unit,
    onDownloadMedia: (GeneratedMedia) -> Unit
) {
    val colors = LocalSaMuColors.current
    val scale = LocalFontScale.current
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1500L)
            copied = false
        }
    }

    val parsed = remember(msg.content) { splitThinkingContent(msg.content) }
    var userToggledThink by remember(msg.id) { mutableStateOf<Boolean?>(null) }
    val showThinking = userToggledThink ?: (isStreaming && parsed.isStillThinking)

    // Auto-collapse thinking the moment </think> arrives or stream finishes
    LaunchedEffect(parsed.isStillThinking, isStreaming) {
        if (!parsed.isStillThinking || !isStreaming) {
            if (userToggledThink == null) {
                userToggledThink = false
            }
        }
    }

    val bubbleShape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomStart = 4.dp, bottomEnd = 14.dp)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        val maxBubbleW = LocalConfiguration.current.screenWidthDp.dp * 0.85f
        if (msg.isError) {
                Column(
                    modifier = Modifier
                        .widthIn(max = maxBubbleW)
                        .clip(bubbleShape)
                        .background(colors.errorBg)
                        .border(1.dp, colors.error, bubbleShape)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Error: ${msg.errorText ?: "Request failed"}",
                        color = colors.error,
                        fontSize = (13f * scale).sp
                    )
                    Button(
                        onClick = onRegenerate,
                        shape = SaMuRadius.sm,
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = Color.White),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text("Retry", fontSize = (12.5f * scale).sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .widthIn(max = maxBubbleW)
                        .clip(bubbleShape)
                        .background(colors.assistantBubble)
                        .border(1.dp, colors.assistantBubbleBorder, bubbleShape)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isStreaming && msg.content.isEmpty() && msg.media == null) {
                        StreamingPulsingDots()
                    } else {
                        // Thinking Accordion
                        if (parsed.hasThinking) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .clip(SaMuRadius.xl)
                                            .background(colors.surfaceVariant)
                                            .clickable { userToggledThink = !showThinking }
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Psychology, null, tint = colors.primary, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        val label = when {
                                            isStreaming && parsed.isStillThinking ->
                                                if (showThinking) "Thinking... (Hide)" else "Thinking... (Show)"
                                            showThinking -> "Hide Thinking"
                                            else -> "Show Thinking"
                                        }
                                        Text(label, color = colors.textSecondary, fontSize = (11f * scale).sp, fontWeight = FontWeight.Medium)
                                        Icon(
                                            imageVector = if (showThinking) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                            contentDescription = null,
                                            tint = colors.textSecondary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }

                                    // LIVE SKIP BUTTON (visible ONLY while streaming && still thinking)
                                    if (isStreaming && parsed.isStillThinking) {
                                        Row(
                                            modifier = Modifier
                                                .clip(SaMuRadius.xl)
                                                .background(colors.primary.copy(alpha = 0.16f))
                                                .border(1.dp, colors.primary.copy(alpha = 0.6f), SaMuRadius.xl)
                                                .clickable(onClick = onSkipThinking)
                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Skip Thinking",
                                                color = colors.primary,
                                                fontSize = (10.5f * scale).sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Spacer(Modifier.width(2.dp))
                                            Icon(Icons.Default.SkipNext, null, tint = colors.primary, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }

                                if (showThinking && parsed.thinkingText.isNotBlank()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 220.dp)
                                            .clip(SaMuRadius.sm)
                                            .background(colors.surfaceVariant.copy(alpha = 0.35f))
                                            .border(1.dp, colors.divider, SaMuRadius.sm)
                                            .verticalScroll(rememberScrollState())
                                            .padding(8.dp)
                                    ) {
                                        Text(
                                            text = parsed.thinkingText,
                                            color = colors.textSecondary,
                                            fontSize = (10.5f * scale).sp,
                                            lineHeight = (14.5f * scale).sp
                                        )
                                    }
                                }
                            }
                        }

                        // Markdown Body
                        if (parsed.answerText.isNotBlank()) {
                            MarkdownText(markdown = parsed.answerText)
                        }

                        // Generated Media Card
                        msg.media?.let { media ->
                            GeneratedMediaCard(
                                media = media,
                                onOpenLightbox = onOpenLightbox,
                                onDownload = onDownloadMedia,
                                onRegenerate = onRegenerate
                            )
                        }
                    }
                }
            }

        // Assistant Icon-Only Action Bar (aligned START)
        if (!msg.isError && !isStreaming && (msg.content.isNotBlank() || msg.media != null)) {
            Row(
                modifier = Modifier.padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (msg.content.isNotBlank()) {
                    BubbleActionIcon(Icons.Default.SelectAll, "Select text", onClick = onSelectText)
                    BubbleActionIcon(
                        icon = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                        desc = "Copy",
                        tint = if (copied) colors.success else colors.textTertiary,
                        onClick = {
                            clipboard.setText(AnnotatedString(parsed.answerText.ifBlank { msg.content }))
                            copied = true
                        }
                    )
                    BubbleActionIcon(
                        Icons.Default.Share,
                        "Share",
                        onClick = { sharePlainText(ctx, parsed.answerText.ifBlank { msg.content }) }
                    )
                }
                BubbleActionIcon(Icons.Default.Refresh, "Regenerate", onClick = onRegenerate)
                if (msg.content.isNotBlank()) {
                    BubbleActionIcon(
                        icon = if (isSpeaking) Icons.Default.Stop else Icons.Default.VolumeUp,
                        desc = if (isSpeaking) "Stop reading" else "Read aloud",
                        tint = if (isSpeaking) colors.primary else colors.textTertiary,
                        onClick = onToggleSpeak
                    )
                }
            }
        }
    }
}

@Composable
private fun BubbleActionIcon(
    icon: ImageVector,
    desc: String,
    tint: Color = LocalSaMuColors.current.textTertiary,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, modifier = Modifier.size(30.dp)) {
        Icon(icon, desc, tint = tint, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun StreamingPulsingDots() {
    val colors = LocalSaMuColors.current
    val transition = rememberInfiniteTransition(label = "dots")
    val a1 by transition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, delayMillis = 0), RepeatMode.Reverse),
        label = "d1"
    )
    val a2 by transition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, delayMillis = 200), RepeatMode.Reverse),
        label = "d2"
    )
    val a3 by transition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, delayMillis = 400), RepeatMode.Reverse),
        label = "d3"
    )
    Row(
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(a1, a2, a3).forEach { alpha ->
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .alpha(alpha)
                    .clip(SaMuRadius.full)
                    .background(colors.primary)
            )
        }
    }
}

// ==================== ATTACHMENTS & INPUT BAR ====================

@Composable
private fun AttachmentImageThumb(path: String, sizeDp: Int, onClick: () -> Unit) {
    var bmp by remember(path) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(path) {
        bmp = withContext(Dispatchers.IO) {
            try { BitmapFactory.decodeFile(path) } catch (_: Exception) { null }
        }
    }
    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(SaMuRadius.sm)
            .background(LocalSaMuColors.current.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (bmp != null) {
            Image(
                bitmap = bmp!!.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(Icons.Default.Image, null, tint = LocalSaMuColors.current.textTertiary)
        }
    }
}

@Composable
private fun AttachmentFileChip(att: ChatAttachment) {
    val colors = LocalSaMuColors.current
    val ext = att.name.substringAfterLast('.', "").lowercase()
    val icon = when {
        ext == "pdf" -> Icons.Default.PictureAsPdf
        ext in setOf("wav", "mp3", "ogg", "flac", "m4a", "aac") -> Icons.Default.Audiotrack
        ext in setOf("mp4", "mkv", "mov", "webm") -> Icons.Default.Videocam
        ext in setOf("kt", "java", "py", "js", "ts", "cpp", "c", "json", "xml", "html", "css", "sql") -> Icons.Default.Code
        ext in setOf("doc", "docx", "txt", "md", "epub") -> Icons.Default.Description
        else -> Icons.Default.InsertDriveFile
    }
    Row(
        modifier = Modifier
            .clip(SaMuRadius.sm)
            .background(colors.surfaceVariant)
            .border(1.dp, colors.divider, SaMuRadius.sm)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = colors.primary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(5.dp))
        Text(
            text = att.name,
            color = colors.onSurface,
            fontSize = 11.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 120.dp)
        )
    }
}

@Composable
private fun ChatInputBar(
    mode: ChatMode,
    inputText: String,
    onInputTextChange: (String) -> Unit,
    attachments: List<ChatAttachment>,
    isStreaming: Boolean,
    onSelectMode: (ChatMode) -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onAttachAnyFile: () -> Unit,
    onAttachImage: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit
) {
    val colors = LocalSaMuColors.current
    val scale = LocalFontScale.current
    var modeMenuOpen by remember { mutableStateOf(false) }

    val modeIcon = when (mode) {
        ChatMode.CHAT -> Icons.Default.Forum
        ChatMode.IMAGE -> Icons.Default.Image
        ChatMode.AUDIO -> Icons.Default.Audiotrack
        ChatMode.VIDEO -> Icons.Default.Videocam
    }

    Column(modifier = Modifier.fillMaxWidth().background(colors.surface)) {
        HorizontalDivider(thickness = 1.dp, color = colors.divider)

        // Attachment Preview Strip
        if (attachments.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                attachments.forEach { att ->
                    Box {
                        if (att.isImage) {
                            AttachmentImageThumb(path = att.path, sizeDp = 56, onClick = {})
                        } else {
                            AttachmentFileChip(att = att)
                        }
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(18.dp)
                                .clip(SaMuRadius.full)
                                .background(colors.error)
                                .clickable { onRemoveAttachment(att.id) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Close, "Remove", tint = Color.White, modifier = Modifier.size(12.dp))
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Compact Mode Dropdown Pill
            Box {
                Row(
                    modifier = Modifier
                        .height(44.dp)
                        .clip(SaMuRadius.xl)
                        .background(colors.surfaceVariant)
                        .border(1.dp, colors.divider, SaMuRadius.xl)
                        .clickable { modeMenuOpen = true }
                        .padding(horizontal = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(modeIcon, null, tint = colors.primary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = mode.label,
                        color = colors.onSurface,
                        fontSize = (11.5f * scale).sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Icon(Icons.Default.KeyboardArrowDown, null, tint = colors.textSecondary, modifier = Modifier.size(15.dp))
                }
                DropdownMenu(
                    expanded = modeMenuOpen,
                    onDismissRequest = { modeMenuOpen = false },
                    modifier = Modifier
                        .widthIn(min = 210.dp)
                        .background(colors.surface)
                        .border(1.dp, colors.divider, SaMuRadius.sm)
                ) {
                    ChatMode.entries.forEach { m ->
                        val active = m == mode
                        val ic = when (m) {
                            ChatMode.CHAT -> Icons.Default.Forum
                            ChatMode.IMAGE -> Icons.Default.Image
                            ChatMode.AUDIO -> Icons.Default.Audiotrack
                            ChatMode.VIDEO -> Icons.Default.Videocam
                        }
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(ic, null, tint = if (active) colors.primary else colors.textSecondary, modifier = Modifier.size(17.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(m.label, color = if (active) colors.primary else colors.onSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        Text(m.subtitle, color = colors.textSecondary, fontSize = 11.sp)
                                    }
                                }
                            },
                            onClick = {
                                modeMenuOpen = false
                                onSelectMode(m)
                            }
                        )
                    }
                }
            }

            // Context-Aware Attach Icons
            if (mode == ChatMode.CHAT) {
                IconButton(onClick = onAttachAnyFile, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.AttachFile, "Attach file", tint = colors.textSecondary, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onAttachImage, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Image, "Attach image", tint = colors.textSecondary, modifier = Modifier.size(20.dp))
                }
            } else if (mode == ChatMode.IMAGE || mode == ChatMode.VIDEO) {
                IconButton(onClick = onAttachImage, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Image, "Reference image", tint = colors.primary, modifier = Modifier.size(20.dp))
                }
            }

            // Multiline Pill Text Input
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp, max = 120.dp)
                    .clip(SaMuRadius.xl)
                    .background(colors.background)
                    .border(1.dp, colors.divider, SaMuRadius.xl)
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (inputText.isEmpty()) {
                    Text(
                        text = mode.placeholder,
                        color = colors.textTertiary,
                        fontSize = (13.5f * scale).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = inputText,
                    onValueChange = onInputTextChange,
                    textStyle = TextStyle(
                        color = colors.onSurface,
                        fontSize = (14f * scale).sp,
                        lineHeight = (19f * scale).sp
                    ),
                    cursorBrush = SolidColor(colors.primary),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Circular Send / Stop Button (44dp)
            val canSend = inputText.isNotBlank() || attachments.isNotEmpty()
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(SaMuRadius.full)
                    .background(
                        when {
                            isStreaming -> colors.error
                            canSend -> colors.primary
                            else -> colors.primary.copy(alpha = 0.40f)
                        }
                    )
                    .clickable(enabled = isStreaming || canSend) {
                        if (isStreaming) onStop() else onSend()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isStreaming) Icons.Default.Stop else Icons.Default.Send,
                    contentDescription = if (isStreaming) "Stop" else "Send",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
