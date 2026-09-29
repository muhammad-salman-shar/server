package com.neurasamu.build.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.neurasamu.build.model.ModelStore
import com.neurasamu.build.model.SamuModel
import com.neurasamu.build.server.SamuRuntime
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
    val message: String? = null
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
            _state.value = _state.value.copy(busy = true, message = "Importing…")
            try {
                val m = ModelStore.import(getApplication(), uri)
                _state.value = _state.value.copy(busy = false, message = "Added ${m.displayName}")
                refresh()
            } catch (e: Exception) {
                _state.value = _state.value.copy(busy = false, message = "Import failed: ${e.message}")
            }
        }
    }

    fun loadModel(m: SamuModel) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, message = "Loading ${m.displayName}…")
            try {
                val engine = SamuRuntime.engine(getApplication())
                withContext(Dispatchers.IO) { engine.ensureLoaded(m) }
                _state.value = _state.value.copy(
                    busy = false,
                    active = m,
                    modelName = m.displayName,
                    message = "Model active"
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(busy = false, message = "Load failed: ${e.message}")
            }
        }
    }

    fun startServer() {
        val m = _state.value.active ?: return
        viewModelScope.launch {
            try {
                val base = SamuRuntime.startServer(getApplication(), m)
                SamuRuntime.http(getApplication()).apiKey = _state.value.apiKey
                val i = android.content.Intent(getApplication(), com.neurasamu.build.server.SamuServerService::class.java)
                i.putExtra(com.neurasamu.build.server.SamuServerService.EXTRA_PORT, SamuRuntime.port)
                i.putExtra(com.neurasamu.build.server.SamuServerService.EXTRA_MODEL, m.displayName)
                androidx.core.content.ContextCompat.startForegroundService(getApplication(), i)
                _state.value = _state.value.copy(
                    serverRunning = true,
                    url = base,
                    message = "Server started"
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(message = "Server failed: ${e.message}")
            }
        }
    }

    fun stopServer() {
        val i = android.content.Intent(getApplication(), com.neurasamu.build.server.SamuServerService::class.java).setAction(com.neurasamu.build.server.SamuServerService.ACTION_STOP)
        getApplication<Application>().startService(i)
        SamuRuntime.stopAll()
        _state.value = _state.value.copy(serverRunning = false, message = "Server stopped")
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
    }

    fun updateApiKey(k: String) {
        _state.value = _state.value.copy(apiKey = k)
    }
}
