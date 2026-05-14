package com.sshautoforward.ssh

import android.util.Log
import com.sshautoforward.data.db.entity.HostEntity
import com.sshautoforward.data.db.entity.PortRemappingEntity
import com.sshautoforward.data.repository.PortRemappingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class PortForwardStatus(
    val remotePort: Int,
    val localPort: Int,
    val processName: String,
    val state: ForwardState,
    val bytesForwarded: Long = 0,
    val bytesReceived: Long = 0,
)

enum class ForwardState {
    FORWARDING,
    FORWARDING_MANUAL,
    AVAILABLE,
    STOPPED,
}

sealed class AutoForwarderEvent {
    data class PortDiscovered(val port: Int, val process: String) : AutoForwarderEvent()
    data class PortForwarded(val remotePort: Int, val localPort: Int) : AutoForwarderEvent()
    data class PortRemoved(val remotePort: Int) : AutoForwarderEvent()
    data class Error(val message: String) : AutoForwarderEvent()
    data class Connected(val host: String) : AutoForwarderEvent()
    data class Disconnected(val reason: String) : AutoForwarderEvent()
    data class Log(val message: String) : AutoForwarderEvent()
}

@Singleton
class AutoForwarder @Inject constructor(
    private val portRemappingRepository: PortRemappingRepository,
) {
    companion object {
        private const val TAG = "AutoForwarder"
        private const val MAX_RECONNECT_DELAY = 60_000L
        private const val INITIAL_RECONNECT_DELAY = 5_000L
    }

    private val _ports = MutableStateFlow<List<PortForwardStatus>>(emptyList())
    val ports: StateFlow<List<PortForwardStatus>> = _ports.asStateFlow()

    private val _events = MutableStateFlow<AutoForwarderEvent?>(null)
    val events: StateFlow<AutoForwarderEvent?> = _events.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val tunnels = mutableMapOf<Int, SshTunnel>()
    private val manualPorts = mutableSetOf<Int>()
    private val processNames = mutableMapOf<Int, String>()
    private val localPortMap = mutableMapOf<Int, Int>()
    private val portRemappings = mutableMapOf<Int, Int>()
    private val failedPorts = mutableSetOf<Int>()

    private var connection: SshConnection? = null
    private var scanJob: Job? = null
    private var scope: CoroutineScope? = null
    private var host: HostEntity? = null
    private var nextLocalPort = 3000
    private var reconnectWaitJob: Job? = null

    val currentHostId: Long? get() = host?.id

    fun start(host: HostEntity, privateKeyPath: String, passphrase: String? = null) {
        if (_isRunning.value) stop()

        this.host = host
        this.scope = CoroutineScope(Dispatchers.IO)
        this._isRunning.value = true

        scope!!.launch {
            loadRemappings(host.id)
            connectAndLoop(host, privateKeyPath, passphrase)
        }
    }

    private suspend fun loadRemappings(hostId: Long) {
        portRemappings.clear()
        val remappings = portRemappingRepository.getByHostId(hostId).first()
        remappings.forEach { portRemappings[it.remotePort] = it.localPort }
    }

    private fun emitLog(message: String) {
        Log.d(TAG, message)
        _events.value = AutoForwarderEvent.Log(message)
    }

    private suspend fun connectAndLoop(
        host: HostEntity,
        privateKeyPath: String,
        passphrase: String?,
    ) {
        var reconnectDelay = INITIAL_RECONNECT_DELAY

        while (scope?.isActive == true) {
            try {
                emitLog("Connecting to ${host.hostname}:${host.port} as ${host.username}")

                val config = SshConfig(
                    hostname = host.hostname,
                    port = host.port,
                    username = host.username,
                    privateKeyPath = privateKeyPath,
                    passphrase = passphrase,
                )
                connection = SshConnection(config) { msg -> emitLog(msg) }
                val session = connection!!.connect()
                _isConnected.value = true
                emitLog("Connected to ${host.name} (server: ${session.serverVersion})")
                _events.value = AutoForwarderEvent.Connected(host.name)
                reconnectDelay = INITIAL_RECONNECT_DELAY

                scanLoop(host)

                _isConnected.value = false
                _events.value = AutoForwarderEvent.Disconnected("Connection lost")
            } catch (e: Exception) {
                _isConnected.value = false
                val msg = e.message ?: "Unknown error"
                emitLog("Connection error: $msg")
                if (e.cause != null) {
                    emitLog("Caused by: ${e.cause!!.javaClass.simpleName}: ${e.cause!!.message}")
                }
                _events.value = AutoForwarderEvent.Error(msg)
            }

            if (scope?.isActive == true) {
                emitLog("Reconnecting in ${reconnectDelay / 1000}s...")
                val waitJob = scope!!.launch { delay(reconnectDelay) }
                reconnectWaitJob = waitJob
                waitJob.join()
                reconnectWaitJob = null
                reconnectDelay = (reconnectDelay * 2).coerceAtMost(MAX_RECONNECT_DELAY)
            }
        }
    }

    private suspend fun scanLoop(host: HostEntity) {
        while (scope?.isActive == true && connection?.isConnected == true) {
            try {
                scanAndForward(host)
            } catch (e: Exception) {
                Log.e(TAG, "Scan error: ${e.message}")
                if (!connection?.isConnected!!) break
            }
            delay(host.scanIntervalSec * 1000L)
        }
    }

    private suspend fun scanAndForward(host: HostEntity) {
        val remotePorts = PortScanner.scanRemotePorts(connection!!)
        val remotePortSet = remotePorts.map { it.port }.toSet()

        remotePorts.forEach { rp ->
            processNames[rp.port] = rp.processName
            if (rp.port !in tunnels && rp.port !in failedPorts) {
                if (rp.port <= host.maxAutoPort && rp.port >= host.skipPortsBelow) {
                    forwardPort(rp.port, host)
                }
            }
        }

        tunnels.keys.toList().forEach { remotePort ->
            if (remotePort !in remotePortSet && remotePort !in manualPorts) {
                stopTunnel(remotePort)
            }
        }

        failedPorts.removeAll { it !in remotePortSet }

        updatePortsState()
    }

    fun togglePort(remotePort: Int) {
        scope?.launch {
            if (remotePort in tunnels) {
                stopTunnel(remotePort)
                manualPorts.remove(remotePort)
            } else {
                manualPorts.add(remotePort)
                forwardPort(remotePort, host ?: return@launch)
            }
            updatePortsState()
        }
    }

    suspend fun remapPort(remotePort: Int, localPort: Int) {
        portRemappings[remotePort] = localPort
        host?.id?.let { hostId ->
            portRemappingRepository.insert(
                PortRemappingEntity(hostId = hostId, remotePort = remotePort, localPort = localPort)
            )
        }
        if (remotePort in tunnels) {
            stopTunnel(remotePort)
            forwardPort(remotePort, host ?: return)
        }
        updatePortsState()
    }

    private fun forwardPort(remotePort: Int, host: HostEntity) {
        try {
            val session = connection?.getSession() ?: return
            val localPort = resolveLocalPort(remotePort, host)

            val tunnel = SshTunnel(session, remotePort, localPort)
            tunnel.start()
            tunnels[remotePort] = tunnel
            localPortMap[remotePort] = localPort

            Log.i(TAG, "Forwarding port $remotePort -> local $localPort")
            _events.value = AutoForwarderEvent.PortForwarded(remotePort, localPort)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to forward port $remotePort: ${e.message}")
            failedPorts.add(remotePort)
        }
    }

    private fun stopTunnel(remotePort: Int) {
        tunnels.remove(remotePort)?.stop()
        localPortMap.remove(remotePort)
        Log.i(TAG, "Stopped forwarding port $remotePort")
        _events.value = AutoForwarderEvent.PortRemoved(remotePort)
    }

    private fun resolveLocalPort(remotePort: Int, host: HostEntity): Int {
        portRemappings[remotePort]?.let { return it }
        if (remotePort in host.skipPortsBelow..host.maxAutoPort) {
            return remotePort
        }
        return allocateLocalPort()
    }

    private fun allocateLocalPort(): Int {
        val usedPorts = localPortMap.values.toSet()
        while (nextLocalPort in usedPorts) {
            nextLocalPort++
        }
        return nextLocalPort++
    }

    private fun updatePortsState() {
        val statusList = mutableListOf<PortForwardStatus>()
        val allPorts = (tunnels.keys + (processNames.keys)).distinct().sorted()

        for (port in allPorts) {
            val tunnel = tunnels[port]
            val state = when {
                tunnel != null && port in manualPorts -> ForwardState.FORWARDING_MANUAL
                tunnel != null -> ForwardState.FORWARDING
                port in failedPorts -> ForwardState.STOPPED
                else -> ForwardState.AVAILABLE
            }
            statusList.add(
                PortForwardStatus(
                    remotePort = port,
                    localPort = tunnel?.localPort ?: portRemappings[port] ?: port,
                    processName = processNames[port] ?: "",
                    state = state,
                    bytesForwarded = tunnel?.bytesForwarded ?: 0,
                    bytesReceived = tunnel?.bytesReceived ?: 0,
                )
            )
        }
        _ports.value = statusList
    }

    fun reconnectNow() {
        emitLog("Network change — reconnecting immediately")
        reconnectWaitJob?.cancel()
        reconnectWaitJob = null
        connection?.disconnect()
    }

    fun stop() {
        _isRunning.value = false
        _isConnected.value = false
        scanJob?.cancel()
        tunnels.values.toList().forEach { it.stop() }
        tunnels.clear()
        localPortMap.clear()
        manualPorts.clear()
        processNames.clear()
        failedPorts.clear()
        connection?.disconnect()
        connection = null
        _ports.value = emptyList()
        _events.value = AutoForwarderEvent.Disconnected("Stopped")
        Log.i(TAG, "AutoForwarder stopped")
    }
}
