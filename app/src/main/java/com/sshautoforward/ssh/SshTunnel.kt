package com.sshautoforward.ssh

import android.util.Log
import com.jcraft.jsch.Session

data class TunnelInfo(
    val remotePort: Int,
    val localPort: Int,
    val processName: String = "",
    val bytesForwarded: Long = 0,
    val bytesReceived: Long = 0,
    val isActive: Boolean = true,
)

class SshTunnel(
    private val session: Session,
    val remotePort: Int,
    val localPort: Int,
) {
    companion object {
        private const val TAG = "SshTunnel"
    }

    @Volatile
    var isActive = false
        private set

    private var _bytesForwarded = 0L
    private var _bytesReceived = 0L

    val bytesForwarded: Long get() = _bytesForwarded
    val bytesReceived: Long get() = _bytesReceived

    fun start() {
        try {
            session.setPortForwardingL(
                "127.0.0.1",
                localPort,
                "127.0.0.1",
                remotePort,
            )
            isActive = true
            Log.i(TAG, "Tunnel started: 127.0.0.1:$localPort -> 127.0.0.1:$remotePort")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start tunnel $localPort->$remotePort: ${e.message}")
            throw SshException("Failed to start tunnel: ${e.message}", e)
        }
    }

    fun stop() {
        try {
            session.delPortForwardingL(localPort)
            Log.i(TAG, "Tunnel stopped: $localPort->$remotePort")
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping tunnel $localPort: ${e.message}")
        }
        isActive = false
    }

    fun toInfo(processName: String): TunnelInfo = TunnelInfo(
        remotePort = remotePort,
        localPort = localPort,
        processName = processName,
        bytesForwarded = bytesForwarded,
        bytesReceived = bytesReceived,
        isActive = isActive,
    )
}
