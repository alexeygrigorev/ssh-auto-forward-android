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
import com.sshautoforward.ssh.PortForwardStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardState(
    val host: HostEntity? = null,
    val isConnected: Boolean = false,
    val isRunning: Boolean = false,
    val logMessages: List<String> = emptyList(),
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

    init {
        viewModelScope.launch {
            val host = hostRepository.getById(hostId) ?: return@launch
            _state.value = _state.value.copy(host = host)

            val key = sshKeyRepository.getById(host.keyId) ?: return@launch
            autoForwarder.start(host, key.privateKeyPath)
        }

        viewModelScope.launch {
            autoForwarder.isConnected.collect { connected ->
                _state.value = _state.value.copy(isConnected = connected)
            }
        }

        viewModelScope.launch {
            autoForwarder.isRunning.collect { running ->
                _state.value = _state.value.copy(isRunning = running)
            }
        }

        viewModelScope.launch {
            autoForwarder.events.collect { event ->
                event?.let {
                    addLog(it.toString())
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
        _logMessages.value = _logMessages.value + message
    }

    override fun onCleared() {
        autoForwarder.stop()
        super.onCleared()
    }
}
