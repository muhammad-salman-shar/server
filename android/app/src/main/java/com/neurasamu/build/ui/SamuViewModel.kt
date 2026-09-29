package com.neurasamu.build.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.neurasamu.build.model.ModelStore
import com.neurasamu.build.model.SamuModel
import com.neurasamu.build.server.SamuEngine
import com.neurasamu.build.server.SamuRuntime
import com.neurasamu.build.server.SamuServerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class UiState(
    val models: List<SamuModel> = emptyList(),
    val active: SamuModel? = null,
    val serverRunning: Boolean = false,
    val url: String = "",
    val apiKey: String = "samu-" + UUID.randomUUID().toString().take(8),
    val modelName: String = "",
    val busy: Boolean = false,
    val busyLabel: String = "",
    val message: String? = null,
    val errorDetail: String? = null,
    val selfTestResult: String? = null
)

class SamuViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        val list = ModelStore.list(getApplication())
        _state.value = _state.value.copy(models = list)
    }

    fun importModel(uri: Uri) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, busyLabel = "Importing…", message = null, errorDetail = null)
            try {
                val m = ModelStore.import(getApplication(), uri)
                _state.value = _state.value.copy(busy = false, busyLabel = "", message = "Added ${m.displayName}")
                refresh()
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    busy = false, busyLabel = "",
                    message = "Import failed",
                    errorDetail = "${e.javaClass.simpleName}: ${e.message}"
                )
            }
        }
    }

    fun runSelfTest() {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, busyLabel = "GPU detect + JNI check…")
            val out = withContext(Dispatchers.IO) {
                buildString {
                    appendLine("=== SamuEngine JNI self-test ===")
                    appendLine("Lib loaded: ${SamuEngine.isLoaded}")
                    appendLine("GPU: ${runCatching { SamuEngine.detectGpu() }.getOrElse { "err: ${it.message}" }}")
                    appendLine("Context: ${runCatching { SamuEngine.contextSize() }.getOrElse { -1 }}")
                }
            }
            _state.value = _state.value.copy(busy = false, busyLabel = "", selfTestResult = out)
        }
    }

    fun loadModel(m: SamuModel) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                busy = true,
                busyLabel = "Loading ${m.displayName}… (2-5 min)",
                message = null, errorDetail = null
            )
            try {
                withContext(Dispatchers.IO) {
                    SamuRuntime.loadModel(getApplication(), m)
                }
                _state.value = _state.value.copy(
                    busy = false, busyLabel = "",
                    active = m, modelName = m.displayName,
                    message = "Model active — press Start"
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    busy = false, busyLabel = "",
                    message = "Load failed",
                    errorDetail = "${e.javaClass.simpleName}: ${e.message}"
                )
            }
        }
    }

    fun startServer() {
        val m = _state.value.active ?: return
        viewModelScope.launch {
            try {
                val base = SamuRuntime.startServer(getApplication(), m)
                SamuRuntime.http(getApplication()).apiKey = _state.value.apiKey
                SamuRuntime.http(getApplication()).displayNameOverride = _state.value.modelName

                val i = android.content.Intent(getApplication(), SamuServerService::class.java)
                i.putExtra(SamuServerService.EXTRA_PORT, SamuRuntime.port)
                i.putExtra(SamuServerService.EXTRA_MODEL, m.displayName)
                androidx.core.content.ContextCompat.startForegroundService(getApplication(), i)

                _state.value = _state.value.copy(
                    serverRunning = true, url = base, message = "Server started"
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    message = "Server failed",
                    errorDetail = "${e.javaClass.simpleName}: ${e.message}"
                )
            }
        }
    }

    fun stopServer() {
        val i = android.content.Intent(getApplication(), SamuServerService::class.java)
            .setAction(SamuServerService.ACTION_STOP)
        getApplication<Application>().startService(i)
        SamuRuntime.stopAll()
        _state.value = _state.value.copy(serverRunning = false, active = null, message = "Server stopped")
    }

    fun delete(m: SamuModel) {
        ModelStore.delete(getApplication(), m)
        if (_state.value.active?.id == m.id) {
            SamuRuntime.stopAll()
            _state.value = _state.value.copy(active = null, serverRunning = false)
        }
        refresh()
    }

    fun updateModelName(name: String) {
        _state.value = _state.value.copy(modelName = name)
        SamuRuntime.httpOrNull()?.displayNameOverride = name
    }

    fun updateApiKey(k: String) {
        _state.value = _state.value.copy(apiKey = k)
        SamuRuntime.httpOrNull()?.apiKey = k
    }

    fun clearError() {
        _state.value = _state.value.copy(message = null, errorDetail = null)
    }
}
