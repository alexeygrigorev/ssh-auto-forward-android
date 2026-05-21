package com.sshautoforward.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.sshautoforward.data.db.entity.HostEntity
import com.sshautoforward.data.db.entity.PortUsageEntity
import com.sshautoforward.data.repository.HostRepository
import com.sshautoforward.data.repository.PortUsageRepository
import com.sshautoforward.service.ForwardingService
import com.sshautoforward.ssh.AutoForwarder
import com.sshautoforward.ssh.AutoForwarderEvent
import com.sshautoforward.ssh.PortForwardStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class DashboardState(
    val host: HostEntity? = null,
    val isConnected: Boolean = false,
    val isRunning: Boolean = false,
    val lastError: String? = null,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    application: Application,
    savedStateHandle: SavedStateHandle,
    private val hostRepository: HostRepository,
    private val portUsageRepository: PortUsageRepository,
    private val autoForwarder: AutoForwarder,
) : AndroidViewModel(application) {

    private val hostId: Long = savedStateHandle["hostId"] ?: error("hostId required")

    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    val ports: StateFlow<List<PortForwardStatus>> = autoForwarder.ports

    val usage: StateFlow<Map<Int, PortUsageEntity>> =
        portUsageRepository.getByHostId(hostId)
            .map { list -> list.associateBy { it.remotePort } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _logMessages = MutableStateFlow<List<String>>(emptyList())
    val logMessages: StateFlow<List<String>> = _logMessages.asStateFlow()

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    init {
        viewModelScope.launch {
            val host = hostRepository.getById(hostId) ?: return@launch
            _state.value = _state.value.copy(host = host)
            addLog("Loading host: ${host.name} (${host.hostname}:${host.port})")

            if (autoForwarder.isRunning.value && autoForwarder.currentHostId == hostId) {
                addLog("Forwarding already active for ${host.name}")
            } else {
                addLog("Starting forwarding service for ${host.hostname}:${host.port}...")
                ForwardingService.start(getApplication(), hostId)
            }
        }

        viewModelScope.launch {
            autoForwarder.isConnected.collect { connected ->
                _state.value = _state.value.copy(
                    isConnected = connected,
                    lastError = if (connected) null else _state.value.lastError,
                )
            }
        }

        viewModelScope.launch {
            autoForwarder.isRunning.collect { running ->
                _state.value = _state.value.copy(isRunning = running)
            }
        }

        viewModelScope.launch {
            autoForwarder.events.collect { event ->
                when (event) {
                    is AutoForwarderEvent.Connected -> {
                        _state.value = _state.value.copy(lastError = null)
                        addLog("Connected to ${event.host}")
                    }
                    is AutoForwarderEvent.Disconnected -> {
                        addLog("Disconnected: ${event.reason}")
                    }
                    is AutoForwarderEvent.PortForwarded -> {
                        addLog("Forwarding port ${event.remotePort} -> localhost:${event.localPort}")
                    }
                    is AutoForwarderEvent.PortRemoved -> {
                        addLog("Stopped port ${event.remotePort}")
                    }
                    is AutoForwarderEvent.Error -> {
                        _state.value = _state.value.copy(lastError = event.message)
                        addLog("ERROR: ${event.message}")
                    }
                    is AutoForwarderEvent.Log -> {
                        addLog(event.message)
                    }
                    is AutoForwarderEvent.PortDiscovered -> {
                        addLog("Discovered port ${event.port} (${event.process})")
                    }
                    null -> {}
                }
            }
        }
    }

    fun togglePort(remotePort: Int) {
        autoForwarder.togglePort(remotePort)
    }

    fun recordOpenUrl(remotePort: Int) {
        viewModelScope.launch {
            portUsageRepository.recordClick(hostId, remotePort)
        }
    }

    fun disconnect() {
        ForwardingService.stop(getApplication())
    }

    private fun addLog(message: String) {
        val timestamp = timeFormat.format(Date())
        _logMessages.value = _logMessages.value + "[$timestamp] $message"
    }
}
