package com.sshautoforward.ui.dashboard

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sshautoforward.data.db.entity.HostEntity
import com.sshautoforward.data.repository.HostRepository
import com.sshautoforward.data.repository.SshKeyRepository
import com.sshautoforward.ssh.AutoForwarder
import com.sshautoforward.ssh.AutoForwarderEvent
import com.sshautoforward.ssh.PortForwardStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    savedStateHandle: SavedStateHandle,
    private val hostRepository: HostRepository,
    private val sshKeyRepository: SshKeyRepository,
    private val autoForwarder: AutoForwarder,
) : ViewModel() {

    private val hostId: Long = savedStateHandle["hostId"] ?: error("hostId required")

    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    val ports: StateFlow<List<PortForwardStatus>> = autoForwarder.ports

    private val _logMessages = MutableStateFlow<List<String>>(emptyList())
    val logMessages: StateFlow<List<String>> = _logMessages.asStateFlow()

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    init {
        viewModelScope.launch {
            val host = hostRepository.getById(hostId) ?: return@launch
            _state.value = _state.value.copy(host = host)
            addLog("Loading host: ${host.name} (${host.hostname}:${host.port})")

            val key = sshKeyRepository.getById(host.keyId)
            if (key == null) {
                _state.value = _state.value.copy(lastError = "SSH key not found")
                addLog("ERROR: SSH key not found (keyId=${host.keyId})")
                return@launch
            }
            addLog("Using key: ${key.name}")
            addLog("Connecting to ${host.hostname}:${host.port}...")
            autoForwarder.start(host, key.privateKeyPath)
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

    fun stop() {
        autoForwarder.stop()
    }

    private fun addLog(message: String) {
        val timestamp = timeFormat.format(Date())
        _logMessages.value = _logMessages.value + "[$timestamp] $message"
    }

    override fun onCleared() {
        autoForwarder.stop()
        super.onCleared()
    }
}
