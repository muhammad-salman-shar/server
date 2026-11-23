package com.neurasamu.build.state

import android.content.Context
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.neurasamu.build.chat.ChatClient
import com.neurasamu.build.chat.FileAnalyzer
import com.neurasamu.build.chat.StreamAbortHandle
import com.neurasamu.build.data.ApiConfig
import com.neurasamu.build.data.AppTab
import com.neurasamu.build.data.AtomicStorage
import com.neurasamu.build.data.BUILTIN_LOCAL_API_ID
import com.neurasamu.build.data.ChatAttachment
import com.neurasamu.build.data.ChatMessage
import com.neurasamu.build.data.ChatMode
import com.neurasamu.build.data.ChatPrefs
import com.neurasamu.build.data.ChatSession
import com.neurasamu.build.data.ExtraFileRole
import com.neurasamu.build.data.ExtraModelFile
import com.neurasamu.build.data.MediaKind
import com.neurasamu.build.data.ModelEntry
import com.neurasamu.build.data.ModelKind
import com.neurasamu.build.data.ModelProfile
import com.neurasamu.build.data.ServerRunState
import com.neurasamu.build.data.ServerSettings
import com.neurasamu.build.data.ThemeMode
import com.neurasamu.build.data.ThinkingEffort
import com.neurasamu.build.data.UrlSanitizer
import com.neurasamu.build.engine.GgufInspector
import com.neurasamu.build.engine.HardwareDetector
import com.neurasamu.build.engine.HardwareProfile
import com.neurasamu.build.engine.SamuEngineRouter
import com.neurasamu.build.server.ApiServer
import com.neurasamu.build.server.LogLevel
import com.neurasamu.build.server.MonitorSnapshot
import com.neurasamu.build.server.SaMuServerService
import com.neurasamu.build.server.ServerMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Locale
import java.util.UUID

data class SaMuAppState(
    val activeTab: AppTab = AppTab.CHAT,
    val settings: ServerSettings = ServerSettings(),
    val chatPrefs: ChatPrefs = ChatPrefs(),
    val customApis: List<ApiConfig> = emptyList(),
    val chats: List<ChatSession> = emptyList(),
    val models: List<ModelEntry> = emptyList(),
    val activeModelPath: String? = null,
    val serverState: ServerRunState = ServerRunState.STOPPED,
    val serverStatusText: String = "",
    val serverErrorText: String? = null,
    val lanIp: String = "127.0.0.1",
    val hardware: HardwareProfile? = null,
    val isImportingModel: Boolean = false,
    val isStreamingChat: Boolean = false,
    val streamingMessageId: String? = null,
    val speakingMessageId: String? = null,
    val toastMessage: String? = null,
    val monitor: MonitorSnapshot = MonitorSnapshot()
) {
    val activeModel: ModelEntry?
        get() = models.firstOrNull { it.path == activeModelPath }

    val builtInLocalApi: ApiConfig
        get() {
            val apiModel = settings.modelApiName.ifBlank {
                activeModel?.displayName ?: "samu-local"
            }
            return ApiConfig(
                id = BUILTIN_LOCAL_API_ID,
                label = "SaMu Lab (Local)",
                baseUrl = "http://127.0.0.1:${settings.port}/v1",
                modelName = apiModel,
                apiKey = if (settings.requireAuth) settings.apiKey else "",
                systemPrompt = "",
                isBuiltIn = true
            )
        }

    val allApis: List<ApiConfig>
        get() = listOf(builtInLocalApi) + customApis

    val activeApi: ApiConfig
        get() = allApis.firstOrNull { it.id == chatPrefs.activeApiId } ?: builtInLocalApi

    val activeChat: ChatSession
        get() = chats.firstOrNull { it.id == chatPrefs.activeChatId }
            ?: chats.firstOrNull()
            ?: ChatSession()

    val isServerOnline: Boolean
        get() = serverState == ServerRunState.RUNNING || serverState == ServerRunState.PROCESSING

    val activeEndpointUrl: String
        get() = if (settings.useOtherDeviceUrl) {
            "http://$lanIp:${settings.port}/v1"
        } else {
            "http://127.0.0.1:${settings.port}/v1"
        }

    val liveChatTokensEstimate: Int
        get() {
            val chars = activeChat.messages.sumOf { it.content.length }
            return (chars / 4).coerceAtLeast(0)
        }
}

object SaMuRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(SaMuAppState())
    val state: StateFlow<SaMuAppState> = _state.asStateFlow()

    @Volatile
    private var initialized = false
    private lateinit var appCtx: Context

    @Volatile
    private var apiServer: ApiServer? = null
    @Volatile
    private var activeAbortHandle: StreamAbortHandle? = null
    private var activeStreamJob: Job? = null
    private var toastJob: Job? = null
    private var serverStartJob: Job? = null

    private var tts: TextToSpeech? = null
    @Volatile
    private var ttsReady = false

    fun init(ctx: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            appCtx = ctx.applicationContext
            initialized = true

            val settings = AtomicStorage.loadSettings(appCtx)
            val customApis = AtomicStorage.loadApis(appCtx)
            var chats = AtomicStorage.loadChats(appCtx)
            var prefs = AtomicStorage.loadChatPrefs(appCtx)

            if (chats.isEmpty()) {
                val first = ChatSession(title = "New Chat")
                chats = listOf(first)
                prefs = prefs.copy(activeChatId = first.id)
                AtomicStorage.saveChats(appCtx, chats)
                AtomicStorage.saveChatPrefs(appCtx, prefs)
            } else if (prefs.activeChatId == null || chats.none { it.id == prefs.activeChatId }) {
                prefs = prefs.copy(activeChatId = chats.first().id)
                AtomicStorage.saveChatPrefs(appCtx, prefs)
            }

            val hw = HardwareDetector.detect(appCtx)
            val ip = detectLanIp()

            _state.value = SaMuAppState(
                settings = settings,
                chatPrefs = prefs,
                customApis = customApis,
                chats = chats,
                activeModelPath = settings.activeModelPath,
                lanIp = ip,
                hardware = hw,
                monitor = ServerMonitor.snapshot.value
            )

            SaMuServerService.onExternalStopRequested = {
                stopServer()
            }

            // ServerMonitor.snapshot is observed directly in isolated UI scopes to keep touch/scroll buttery smooth

            scope.launch {
                refreshModels()
            }
        }
    }

    // ==================== NAVIGATION & TOAST ====================

    fun setTab(tab: AppTab) {
        _state.update { it.copy(activeTab = tab) }
    }

    fun showToast(msg: String) {
        toastJob?.cancel()
        _state.update { it.copy(toastMessage = msg) }
        toastJob = scope.launch {
            delay(2000L)
            _state.update { it.copy(toastMessage = null) }
        }
    }

    // ==================== PREFS & THEME ====================

    fun cycleThemeQuick() {
        val next = when (_state.value.chatPrefs.themeMode) {
            ThemeMode.DARK -> ThemeMode.LIGHT
            ThemeMode.LIGHT -> ThemeMode.DARK
            ThemeMode.SYSTEM -> ThemeMode.DARK
        }
        updateChatPrefs { it.copy(themeMode = next) }
        showToast("Theme: ${next.label}")
    }

    fun updateChatPrefs(transform: (ChatPrefs) -> ChatPrefs) {
        val updated = transform(_state.value.chatPrefs)
        _state.update { it.copy(chatPrefs = updated) }
        scope.launch { AtomicStorage.saveChatPrefs(appCtx, updated) }
    }

    fun setThinkingEffort(effort: ThinkingEffort) {
        updateChatPrefs { it.copy(thinkingEffort = effort) }
    }

    fun setChatMode(mode: ChatMode) {
        updateChatPrefs { it.copy(chatMode = mode) }
    }

    fun selectApi(apiId: String) {
        updateChatPrefs { it.copy(activeApiId = apiId) }
    }

    // ==================== CUSTOM APIS ====================

    fun saveCustomApi(api: ApiConfig) {
        val sanitized = api.copy(
            baseUrl = UrlSanitizer.sanitize(api.baseUrl),
            label = api.label.trim().ifBlank { "Custom API" },
            modelName = api.modelName.trim(),
            apiKey = api.apiKey.trim(),
            systemPrompt = api.systemPrompt.trim(),
            isBuiltIn = false
        )
        val current = _state.value.customApis.toMutableList()
        val idx = current.indexOfFirst { it.id == sanitized.id }
        if (idx >= 0) current[idx] = sanitized else current.add(sanitized)
        _state.update { it.copy(customApis = current) }
        scope.launch { AtomicStorage.saveApis(appCtx, current) }
        selectApi(sanitized.id)
        showToast("API saved: ${sanitized.label}")
    }

    fun deleteCustomApi(apiId: String) {
        val updated = _state.value.customApis.filter { it.id != apiId }
        _state.update { cur ->
            val nextActiveApi = if (cur.chatPrefs.activeApiId == apiId) BUILTIN_LOCAL_API_ID else cur.chatPrefs.activeApiId
            cur.copy(
                customApis = updated,
                chatPrefs = cur.chatPrefs.copy(activeApiId = nextActiveApi)
            )
        }
        scope.launch {
            AtomicStorage.saveApis(appCtx, updated)
            AtomicStorage.saveChatPrefs(appCtx, _state.value.chatPrefs)
        }
        showToast("API deleted")
    }

    // ==================== CHATS ====================

    fun createNewChat() {
        val newChat = ChatSession(title = "New Chat")
        val updated = listOf(newChat) + _state.value.chats
        _state.update {
            it.copy(
                chats = updated,
                chatPrefs = it.chatPrefs.copy(activeChatId = newChat.id)
            )
        }
        scope.launch {
            AtomicStorage.saveChats(appCtx, updated)
            AtomicStorage.saveChatPrefs(appCtx, _state.value.chatPrefs)
        }
    }

    fun selectChat(chatId: String) {
        if (_state.value.chats.none { it.id == chatId }) return
        updateChatPrefs { it.copy(activeChatId = chatId) }
    }

    fun renameChat(chatId: String, newTitle: String) {
        val clean = newTitle.trim().ifBlank { "Untitled Chat" }
        val updated = _state.value.chats.map {
            if (it.id == chatId) it.copy(title = clean, updatedAt = System.currentTimeMillis()) else it
        }
        _state.update { it.copy(chats = updated) }
        scope.launch { AtomicStorage.saveChats(appCtx, updated) }
        showToast("Chat renamed")
    }

    fun deleteChat(chatId: String) {
        val remaining = _state.value.chats.filter { it.id != chatId }
        val finalChats = remaining.ifEmpty { listOf(ChatSession(title = "New Chat")) }
        val nextActiveId = if (_state.value.chatPrefs.activeChatId == chatId) {
            finalChats.first().id
        } else {
            _state.value.chatPrefs.activeChatId ?: finalChats.first().id
        }
        _state.update {
            it.copy(
                chats = finalChats,
                chatPrefs = it.chatPrefs.copy(activeChatId = nextActiveId)
            )
        }
        scope.launch {
            AtomicStorage.saveChats(appCtx, finalChats)
            AtomicStorage.saveChatPrefs(appCtx, _state.value.chatPrefs)
        }
        showToast("Chat deleted")
    }

    fun deleteAllChats() {
        stopStreaming()
        val fresh = listOf(ChatSession(title = "New Chat"))
        _state.update {
            it.copy(
                chats = fresh,
                chatPrefs = it.chatPrefs.copy(activeChatId = fresh.first().id)
            )
        }
        scope.launch {
            AtomicStorage.saveChats(appCtx, fresh)
            AtomicStorage.saveChatPrefs(appCtx, _state.value.chatPrefs)
        }
        showToast("All chats cleared")
    }

    fun buildChatTranscript(chatId: String): String {
        val chat = _state.value.chats.firstOrNull { it.id == chatId } ?: return ""
        return buildString {
            appendLine("=== ${chat.title} ===")
            appendLine()
            chat.messages.forEach { m ->
                val who = if (m.role == "user") "USER" else "ASSISTANT"
                appendLine("[$who]")
                if (m.attachments.isNotEmpty()) {
                    appendLine("Attachments: ${m.attachments.joinToString(", ") { it.name }}")
                }
                if (m.content.isNotBlank()) appendLine(m.content)
                m.media?.let { appendLine("[Generated ${it.kind.name}: ${it.fileName}]") }
                appendLine()
            }
        }
    }

    // ==================== CHAT MESSAGING & STREAMING ====================

    fun importAttachment(uri: Uri, onReady: (ChatAttachment) -> Unit) {
        scope.launch {
            try {
                val displayName = AtomicStorage.queryDisplayName(appCtx, uri)
                val mime = AtomicStorage.queryMimeType(appCtx, uri, displayName)
                val copied = AtomicStorage.copyUriToDir(appCtx, uri, AtomicStorage.attachmentsDir(appCtx), displayName)
                val att = ChatAttachment(
                    name = displayName,
                    path = copied.absolutePath,
                    mimeType = mime,
                    sizeBytes = copied.length(),
                    isImage = FileAnalyzer.isImageFile(displayName, mime)
                )
                withContext(Dispatchers.Main) { onReady(att) }
            } catch (e: Exception) {
                showToast("Attach failed: ${e.message}")
            }
        }
    }

    fun sendMessage(text: String, attachments: List<ChatAttachment>) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() && attachments.isEmpty()) return
        if (_state.value.isStreamingChat) return

        val curState = _state.value
        val mode = curState.chatPrefs.chatMode
        val chatId = curState.activeChat.id

        val userMsg = ChatMessage(
            role = "user",
            content = trimmed,
            attachments = attachments,
            mode = mode
        )
        val assistantMsg = ChatMessage(
            role = "assistant",
            content = "",
            mode = mode
        )

        val isFirstUserMsg = curState.activeChat.messages.none { it.role == "user" }
        val autoTitle = if (isFirstUserMsg && trimmed.isNotEmpty()) {
            if (trimmed.length > 40) trimmed.take(40) + "..." else trimmed
        } else null

        updateChatById(chatId) { chat ->
            chat.copy(
                title = autoTitle ?: chat.title,
                messages = chat.messages + userMsg + assistantMsg,
                updatedAt = System.currentTimeMillis()
            )
        }

        val historyForRequest = _state.value.chats.firstOrNull { it.id == chatId }
            ?.messages
            ?.dropLast(1)
            ?: listOf(userMsg)

        startAssistantGeneration(
            chatId = chatId,
            assistantMsgId = assistantMsg.id,
            mode = mode,
            promptText = trimmed,
            refImagePath = attachments.firstOrNull { it.isImage }?.path,
            history = historyForRequest,
            forceSkipThinking = false
        )
    }

    fun stopStreaming() {
        activeAbortHandle?.abort()
        activeStreamJob?.cancel()
        activeStreamJob = null
        _state.update { it.copy(isStreamingChat = false, streamingMessageId = null) }
    }

    fun skipThinking() {
        val cur = _state.value
        val msgId = cur.streamingMessageId ?: return
        val chat = cur.activeChat
        val handle = activeAbortHandle ?: return

        handle.abortForSkip()
        activeStreamJob?.cancel()

        // Clear partial thinking text and restart immediately with forceSkipThinking = true
        updateMessageContent(chat.id, msgId, content = "", isError = false, errorText = null)
        val msgIdx = chat.messages.indexOfFirst { it.id == msgId }
        val history = if (msgIdx > 0) chat.messages.take(msgIdx) else chat.messages.dropLast(1)
        val lastUser = history.lastOrNull { it.role == "user" }

        startAssistantGeneration(
            chatId = chat.id,
            assistantMsgId = msgId,
            mode = ChatMode.CHAT,
            promptText = lastUser?.content ?: "",
            refImagePath = null,
            history = history,
            forceSkipThinking = true
        )
    }

    fun regenerateMessage(assistantMsgId: String) {
        if (_state.value.isStreamingChat) stopStreaming()
        val chat = _state.value.activeChat
        val idx = chat.messages.indexOfFirst { it.id == assistantMsgId }
        if (idx < 0) return

        val target = chat.messages[idx]
        val prevUser = chat.messages.take(idx).lastOrNull { it.role == "user" }
        val mode = target.media?.let {
            when (it.kind) {
                MediaKind.IMAGE -> ChatMode.IMAGE
                MediaKind.AUDIO -> ChatMode.AUDIO
                MediaKind.VIDEO -> ChatMode.VIDEO
            }
        } ?: target.mode

        val promptText = target.media?.prompt?.ifBlank { prevUser?.content ?: "" } ?: prevUser?.content ?: ""
        val refPath = target.media?.refImagePath ?: prevUser?.attachments?.firstOrNull { it.isImage }?.path

        // Truncate everything after this assistant message and clear it
        val truncated = chat.messages.take(idx) + target.copy(
            content = "",
            media = null,
            isError = false,
            errorText = null,
            timestamp = System.currentTimeMillis()
        )
        updateChatById(chat.id) { it.copy(messages = truncated, updatedAt = System.currentTimeMillis()) }

        startAssistantGeneration(
            chatId = chat.id,
            assistantMsgId = assistantMsgId,
            mode = mode,
            promptText = promptText,
            refImagePath = refPath,
            history = chat.messages.take(idx),
            forceSkipThinking = false
        )
    }

    fun editUserMessage(userMsgId: String): Pair<String, List<ChatAttachment>>? {
        if (_state.value.isStreamingChat) stopStreaming()
        val chat = _state.value.activeChat
        val idx = chat.messages.indexOfFirst { it.id == userMsgId }
        if (idx < 0) return null
        val target = chat.messages[idx]
        val remaining = chat.messages.take(idx)
        updateChatById(chat.id) { it.copy(messages = remaining, updatedAt = System.currentTimeMillis()) }
        return target.content to target.attachments
    }

    private fun startAssistantGeneration(
        chatId: String,
        assistantMsgId: String,
        mode: ChatMode,
        promptText: String,
        refImagePath: String?,
        history: List<ChatMessage>,
        forceSkipThinking: Boolean
    ) {
        val handle = StreamAbortHandle()
        activeAbortHandle = handle

        _state.update {
            it.copy(isStreamingChat = true, streamingMessageId = assistantMsgId)
        }

        val api = _state.value.activeApi
        val prefs = _state.value.chatPrefs
        val globalInstr = if (prefs.customInstructionsEnabled) prefs.customInstructions else null

        activeStreamJob = scope.launch {
            try {
                when (mode) {
                    ChatMode.CHAT -> {
                        val finalText = ChatClient.streamChatCompletion(
                            api = api,
                            history = history,
                            globalInstructions = globalInstr,
                            thinkingEffort = prefs.thinkingEffort,
                            forceSkipThinking = forceSkipThinking,
                            abortHandle = handle,
                            onUpdate = { partial ->
                                if (!handle.isSkipRequested()) {
                                    updateMessageContent(chatId, assistantMsgId, content = partial)
                                }
                            }
                        )
                        if (!handle.isSkipRequested()) {
                            updateMessageContent(chatId, assistantMsgId, content = finalText)
                        }
                    }

                    ChatMode.IMAGE -> {
                        val media = ChatClient.generateImage(appCtx, api, promptText, refImagePath, handle)
                        updateMessageMedia(chatId, assistantMsgId, media)
                    }

                    ChatMode.AUDIO -> {
                        val media = ChatClient.generateAudio(appCtx, api, promptText, handle)
                        updateMessageMedia(chatId, assistantMsgId, media)
                    }

                    ChatMode.VIDEO -> {
                        val media = ChatClient.generateVideo(appCtx, api, promptText, refImagePath, handle)
                        updateMessageMedia(chatId, assistantMsgId, media)
                    }
                }
            } catch (e: Exception) {
                if (!handle.isSkipRequested()) {
                    val friendly = friendlyNetworkError(e, api)
                    updateMessageContent(
                        chatId = chatId,
                        msgId = assistantMsgId,
                        content = "",
                        isError = true,
                        errorText = friendly
                    )
                }
            } finally {
                if (!handle.isSkipRequested()) {
                    _state.update { it.copy(isStreamingChat = false, streamingMessageId = null) }
                    AtomicStorage.saveChats(appCtx, _state.value.chats)
                }
            }
        }
    }

    private fun friendlyNetworkError(e: Exception, api: ApiConfig): String {
        val msg = e.message ?: e.javaClass.simpleName
        return if (api.isBuiltIn && (msg.contains("ECONNREFUSED", true) || msg.contains("failed to connect", true))) {
            "Local server is stopped. Open the Server tab and tap Start Server."
        } else {
            msg
        }
    }

    private fun updateChatById(chatId: String, transform: (ChatSession) -> ChatSession) {
        val updated = _state.value.chats.map { if (it.id == chatId) transform(it) else it }
        _state.update { it.copy(chats = updated) }
        scope.launch { AtomicStorage.saveChats(appCtx, updated) }
    }

    private fun updateMessageContent(
        chatId: String,
        msgId: String,
        content: String,
        isError: Boolean = false,
        errorText: String? = null
    ) {
        _state.update { cur ->
            val updatedChats = cur.chats.map { chat ->
                if (chat.id != chatId) chat else chat.copy(
                    messages = chat.messages.map { m ->
                        if (m.id != msgId) m else m.copy(
                            content = content,
                            isError = isError,
                            errorText = errorText
                        )
                    },
                    updatedAt = System.currentTimeMillis()
                )
            }
            cur.copy(chats = updatedChats)
        }
    }

    private fun updateMessageMedia(chatId: String, msgId: String, media: com.neurasamu.build.data.GeneratedMedia) {
        _state.update { cur ->
            val updatedChats = cur.chats.map { chat ->
                if (chat.id != chatId) chat else chat.copy(
                    messages = chat.messages.map { m ->
                        if (m.id != msgId) m else m.copy(media = media, isError = false, errorText = null)
                    },
                    updatedAt = System.currentTimeMillis()
                )
            }
            cur.copy(chats = updatedChats)
        }
    }

    // ==================== READ ALOUD (ANDROID TTS) ====================

    fun toggleSpeakMessage(msgId: String, text: String) {
        if (_state.value.speakingMessageId == msgId) {
            stopSpeaking()
            return
        }
        stopSpeaking()
        val clean = text.replace(Regex("<think>[\\s\\S]*?</think>"), "").trim()
        if (clean.isEmpty()) return

        _state.update { it.copy(speakingMessageId = msgId) }
        if (tts == null) {
            tts = TextToSpeech(appCtx) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    ttsReady = true
                    tts?.language = Locale.getDefault()
                    speakNow(msgId, clean)
                } else {
                    _state.update { it.copy(speakingMessageId = null) }
                }
            }
        } else if (ttsReady) {
            speakNow(msgId, clean)
        }
    }

    private fun speakNow(msgId: String, text: String) {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(uid: String?) {}
            override fun onDone(uid: String?) {
                if (_state.value.speakingMessageId == msgId) {
                    _state.update { it.copy(speakingMessageId = null) }
                }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(uid: String?) {
                if (_state.value.speakingMessageId == msgId) {
                    _state.update { it.copy(speakingMessageId = null) }
                }
            }
        })
        tts?.speak(text.take(3800), TextToSpeech.QUEUE_FLUSH, null, msgId)
    }

    fun stopSpeaking() {
        try { tts?.stop() } catch (_: Exception) {}
        _state.update { it.copy(speakingMessageId = null) }
    }

    // ==================== MODELS & SETTINGS ====================

    fun refreshModels() {
        scope.launch {
            val dir = AtomicStorage.modelsDir(appCtx)
            val settings = _state.value.settings
            val files = dir.listFiles { f ->
                f.isFile &&
                    AtomicStorage.SUPPORTED_MODEL_EXTENSIONS.any { ext -> f.name.endsWith(ext, ignoreCase = true) } &&
                    !f.name.startsWith("mmproj", ignoreCase = true)
            }?.sortedBy { it.name.lowercase() } ?: emptyList()

            val entries = files.map { f ->
                val profile = settings.profileFor(f.absolutePath)
                val meta = if (f.name.endsWith(".gguf", ignoreCase = true)) {
                    GgufInspector.inspect(f)
                } else null
                ModelEntry(
                    file = f,
                    displayName = f.nameWithoutExtension,
                    sizeBytes = f.length(),
                    profile = profile,
                    ggufMeta = meta
                )
            }

            _state.update { cur ->
                val activePath = cur.activeModelPath?.takeIf { p -> entries.any { it.path == p } }
                cur.copy(models = entries, activeModelPath = activePath)
            }
        }
    }

    fun addModelFromUri(uri: Uri) {
        scope.launch {
            _state.update { it.copy(isImportingModel = true, serverErrorText = null) }
            try {
                val copied = AtomicStorage.copyUriToDir(appCtx, uri, AtomicStorage.modelsDir(appCtx))
                val meta = if (copied.name.endsWith(".gguf", ignoreCase = true)) {
                    GgufInspector.inspect(copied)
                } else null

                val guessedKind = ModelKind.guessFromFileName(copied.name)
                val newProfile = ModelProfile(modelPath = copied.absolutePath, kind = guessedKind)
                val curSettings = _state.value.settings
                val updatedProfiles = curSettings.modelProfiles.toMutableMap().apply {
                    put(copied.absolutePath, newProfile)
                }

                // Smart auto-fill: cap default context to device RAM-safe limit (user can tap Max in Server Settings anytime)
                val hwRec = HardwareDetector.detect(appCtx).recommendedContext
                val autoCtx = if (meta != null && meta.nativeContext > 0) {
                    minOf(meta.nativeContext, hwRec)
                } else curSettings.contextSize
                val updatedSettings = curSettings.copy(
                    modelProfiles = updatedProfiles,
                    contextSize = autoCtx,
                    modelApiName = curSettings.modelApiName.ifBlank { copied.nameWithoutExtension }
                )
                saveSettingsInternal(updatedSettings)
                refreshModels()
                ServerMonitor.log(LogLevel.INFO, "Added model: ${copied.name}")
                showToast("Added ${copied.nameWithoutExtension}")
            } catch (e: Exception) {
                _state.update { it.copy(serverErrorText = "Add model failed: ${e.message}") }
                showToast("Import failed")
            } finally {
                _state.update { it.copy(isImportingModel = false) }
            }
        }
    }

    fun deleteModel(entry: ModelEntry) {
        scope.launch {
            if (_state.value.activeModelPath == entry.path) {
                stopServer()
            }
            try { entry.file.delete() } catch (_: Exception) {}
            val updatedProfiles = _state.value.settings.modelProfiles.toMutableMap().apply {
                remove(entry.path)
            }
            val updatedSettings = _state.value.settings.copy(
                modelProfiles = updatedProfiles,
                activeModelPath = if (_state.value.settings.activeModelPath == entry.path) null else _state.value.settings.activeModelPath
            )
            saveSettingsInternal(updatedSettings)
            refreshModels()
            ServerMonitor.log(LogLevel.INFO, "Deleted model: ${entry.displayName}")
            showToast("Deleted ${entry.displayName}")
        }
    }

    fun saveModelProfile(profile: ModelProfile) {
        val updatedProfiles = _state.value.settings.modelProfiles.toMutableMap().apply {
            put(profile.modelPath, profile)
        }
        saveSettingsInternal(_state.value.settings.copy(modelProfiles = updatedProfiles))
        refreshModels()
        showToast("Model settings saved")
    }

    fun copyExtraFileUri(uri: Uri, role: ExtraFileRole, onCopied: (ExtraModelFile) -> Unit) {
        scope.launch {
            try {
                val name = AtomicStorage.queryDisplayName(appCtx, uri)
                val dest = AtomicStorage.copyUriToDir(appCtx, uri, AtomicStorage.extraFilesDir(appCtx), name)
                val item = ExtraModelFile(role = role, path = dest.absolutePath, displayName = name)
                withContext(Dispatchers.Main) { onCopied(item) }
            } catch (e: Exception) {
                showToast("File import failed: ${e.message}")
            }
        }
    }

    fun saveServerSettings(newSettings: ServerSettings) {
        val cleaned = newSettings.copy(
            port = newSettings.port.coerceIn(1024, 65535),
            cpuThreads = newSettings.cpuThreads.coerceAtLeast(0),
            contextSize = newSettings.contextSize.coerceAtLeast(0)
        )
        saveSettingsInternal(cleaned)
        showToast("Server settings saved")
    }

    fun updateModelApiName(name: String) {
        saveSettingsInternal(_state.value.settings.copy(modelApiName = name))
    }

    fun setUseOtherDeviceUrl(useOther: Boolean) {
        saveSettingsInternal(_state.value.settings.copy(useOtherDeviceUrl = useOther))
        if (useOther) refreshLanIp()
    }

    fun refreshLanIp() {
        val ip = detectLanIp()
        _state.update { it.copy(lanIp = ip) }
        showToast("IP: $ip")
    }

    private fun saveSettingsInternal(s: ServerSettings) {
        _state.update { it.copy(settings = s) }
        scope.launch { AtomicStorage.saveSettings(appCtx, s) }
    }

    // ==================== SERVER & ENGINE LIFECYCLE ====================

    fun loadModelAndStart(entry: ModelEntry) {
        // Smart auto-fill: cap default context to device RAM-safe limit so UI stays responsive on 8GB RAM
        val curSettings = _state.value.settings
        val nativeMax = entry.nativeContext
        val hwRec = HardwareDetector.detect(appCtx).recommendedContext
        val safeDefault = if (nativeMax > 0) minOf(nativeMax, hwRec) else curSettings.contextSize
        val autoCtx = when {
            nativeMax > 0 && (curSettings.contextSize <= 0 || curSettings.contextSize > nativeMax) -> safeDefault
            nativeMax > 0 && curSettings.activeModelPath != entry.path -> safeDefault
            else -> curSettings.contextSize
        }
        val updatedSettings = curSettings.copy(
            activeModelPath = entry.path,
            contextSize = autoCtx,
            modelApiName = entry.displayName
        )
        saveSettingsInternal(updatedSettings)
        _state.update { it.copy(activeModelPath = entry.path) }
        startServer(targetEntry = entry)
    }

    fun startServer(targetEntry: ModelEntry? = null) {
        if (_state.value.serverState == ServerRunState.STARTING) return
        serverStartJob?.cancel()
        serverStartJob = scope.launch {
            val cur = _state.value
            val entry = targetEntry
                ?: cur.activeModel
                ?: cur.models.firstOrNull { it.profile.kind == ModelKind.TEXT_VISION }

            if (entry == null) {
                _state.update {
                    it.copy(
                        serverState = ServerRunState.STOPPED,
                        serverErrorText = "No LLM model found. Add a .gguf model first."
                    )
                }
                showToast("Add a model first")
                return@launch
            }

            _state.update {
                it.copy(
                    serverState = ServerRunState.STARTING,
                    activeModelPath = entry.path,
                    serverStatusText = "Loading ${entry.displayName}...",
                    serverErrorText = null
                )
            }
            ServerMonitor.log(LogLevel.INFO, "Starting engine for ${entry.displayName}...")
            SaMuServerService.startOrUpdate(appCtx, cur.settings.port, ServerRunState.STARTING, entry.displayName)

            try {
                // Stop previous HTTP server if any
                apiServer?.stopServer()
                apiServer = null

                val settings = _state.value.settings
                val hw = HardwareDetector.detect(appCtx)
                val decodeThreads = if (settings.cpuThreads > 0) settings.cpuThreads else hw.recommendedThreads
                val batchThreads = if (settings.cpuThreads > 0) settings.cpuThreads else hw.recommendedBatchThreads
                val nativeMax = entry.nativeContext
                val requestedCtx = when {
                    settings.contextSize == 0 && nativeMax > 0 -> nativeMax
                    settings.contextSize == 0 -> 8000
                    nativeMax > 0 -> settings.contextSize.coerceAtMost(nativeMax)
                    else -> settings.contextSize
                }.coerceAtLeast(512)

                val ngl = if (entry.profile.gpuLayers < 0) hw.recommendedGpuLayers else entry.profile.gpuLayers

                SamuEngineRouter.load(
                    ctx = appCtx,
                    modelFile = entry.file,
                    threads = decodeThreads,
                    threadsBatch = batchThreads,
                    ctxSize = requestedCtx,
                    gpuLayers = ngl
                )

                // Attach mmproj projector if configured
                SamuEngineRouter.freeMmproj()
                val mmprojPath = entry.profile.mmprojPath
                if (!mmprojPath.isNullOrBlank() && File(mmprojPath).exists()) {
                    val ok = SamuEngineRouter.loadMmproj(mmprojPath)
                    ServerMonitor.log(
                        if (ok) LogLevel.OK else LogLevel.ERR,
                        if (ok) "Vision/Audio projector loaded: ${File(mmprojPath).name}" else "Failed to load mmproj"
                    )
                }

                val host = if (settings.lanAccess) "0.0.0.0" else "127.0.0.1"
                val server = ApiServer(
                    ctx = appCtx,
                    port = settings.port,
                    hostname = host,
                    settingsProvider = { _state.value.settings },
                    modelsProvider = { _state.value.models },
                    activeModelProvider = { _state.value.activeModel }
                )
                server.startServer()
                apiServer = server

                val finalName = settings.modelApiName.ifBlank { entry.displayName }
                _state.update {
                    it.copy(
                        serverState = ServerRunState.RUNNING,
                        serverStatusText = "Online on :${settings.port} · ctx $requestedCtx",
                        serverErrorText = null,
                        lanIp = detectLanIp()
                    )
                }
                SaMuServerService.startOrUpdate(appCtx, settings.port, ServerRunState.RUNNING, finalName)
                ServerMonitor.log(LogLevel.OK, "Server Online on $host:${settings.port} ($finalName)")
                showToast("Server Online :${settings.port}")
            } catch (e: Throwable) {
                apiServer?.stopServer()
                apiServer = null
                try { SamuEngineRouter.unload() } catch (_: Throwable) {}
                SaMuServerService.stop(appCtx)
                val exactErr = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
                _state.update {
                    it.copy(
                        serverState = ServerRunState.STOPPED,
                        serverStatusText = "",
                        serverErrorText = "Engine failed: $exactErr"
                    )
                }
                ServerMonitor.log(LogLevel.ERR, "Start failed: $exactErr")
            }
        }
    }

    fun stopServer() {
        serverStartJob?.cancel()
        serverStartJob = null
        scope.launch {
            try { SamuEngineRouter.stop() } catch (_: Throwable) {}
            try { apiServer?.stopServer() } catch (_: Throwable) {}
            apiServer = null
            try { SamuEngineRouter.unload() } catch (_: Throwable) {}
            ServerMonitor.resetCounters()
            SaMuServerService.stop(appCtx)
            _state.update {
                it.copy(
                    serverState = ServerRunState.STOPPED,
                    serverStatusText = "Server stopped",
                    serverErrorText = null
                )
            }
            ServerMonitor.log(LogLevel.INFO, "Server stopped")
        }
    }

    private fun detectLanIp(): String {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return "127.0.0.1"
            var fallback: String? = null
            for (nif in interfaces) {
                if (!nif.isUp || nif.isLoopback) continue
                val name = nif.name.lowercase()
                val addrs = nif.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val ip = addr.hostAddress ?: continue
                        if (name.startsWith("wlan") || name.startsWith("ap") || name.startsWith("swlan")) {
                            return ip
                        }
                        if (fallback == null) fallback = ip
                    }
                }
            }
            fallback ?: "127.0.0.1"
        } catch (_: Exception) {
            "127.0.0.1"
        }
    }
}
