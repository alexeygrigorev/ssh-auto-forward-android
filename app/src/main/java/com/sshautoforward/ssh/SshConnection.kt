package com.sshautoforward.ssh

import android.util.Log
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Properties

data class SshConfig(
    val hostname: String,
    val port: Int = 22,
    val username: String,
    val privateKeyPath: String,
    val passphrase: String? = null,
    val keepAliveInterval: Int = 15,
)

class SshConnection(
    private val config: SshConfig,
    private val logCallback: ((String) -> Unit)? = null,
) {

    companion object {
        private const val TAG = "SshConnection"
        private const val DEFAULT_TIMEOUT = 30000
    }

    private var session: Session? = null

    val isConnected: Boolean get() = session?.isConnected == true

    private fun log(message: String) {
        Log.d(TAG, message)
        logCallback?.invoke(message)
    }

    suspend fun connect(): Session = withContext(Dispatchers.IO) {
        if (session?.isConnected == true) {
            return@withContext session!!
        }

        val jsch = JSch()
        try {
            val keyFile = java.io.File(config.privateKeyPath)
            log("Key file: ${config.privateKeyPath}")
            if (!keyFile.exists()) {
                throw SshException("Private key file not found: ${config.privateKeyPath}")
            }
            val keyContent = keyFile.readText(Charsets.UTF_8)
            val firstLine = keyContent.lineSequence().firstOrNull() ?: ""
            log("Key exists (${keyFile.length()} bytes), format: $firstLine")
            if (!firstLine.contains("PRIVATE KEY")) {
                throw SshException("File does not look like a private key: $firstLine")
            }
            if (config.passphrase != null) {
                log("Loading identity with passphrase...")
                jsch.addIdentity(config.privateKeyPath, config.passphrase)
            } else {
                log("Loading identity without passphrase...")
                jsch.addIdentity(config.privateKeyPath)
            }
            log("Identity loaded: ${jsch.identityNames}")
        } catch (e: SshException) {
            throw e
        } catch (e: Exception) {
            log("Failed to load key: ${e.javaClass.simpleName}: ${e.message}")
            throw SshException("Failed to load private key: ${e.javaClass.simpleName}: ${e.message}", e)
        }

        val s = jsch.getSession(config.username, config.hostname, config.port)
        s.setConfig(Properties().apply {
            put("StrictHostKeyChecking", "no")
            put("PreferredAuthentications", "publickey")
            put("UserKnownHostsFile", "/dev/null")
        })
        s.timeout = DEFAULT_TIMEOUT
        s.setServerAliveInterval(config.keepAliveInterval * 1000)
        s.setServerAliveCountMax(3)

        try {
            val addrs = java.net.InetAddress.getAllByName(config.hostname)
            log("DNS resolved ${config.hostname} -> ${addrs.map { it.hostAddress }}")
        } catch (e: Exception) {
            log("DNS resolution failed for ${config.hostname}: ${e.message}")
            throw SshException("DNS resolution failed for ${config.hostname}: ${e.message}", e)
        }

        try {
            log("Connecting to ${config.username}@${config.hostname}:${config.port} (timeout=${DEFAULT_TIMEOUT}ms)...")
            s.connect(DEFAULT_TIMEOUT)
            this@SshConnection.session = s
            log("Connected to ${config.hostname}:${config.port}, server version: ${s.serverVersion}")
            s
        } catch (e: Exception) {
            val cause = e.cause
            val detail = if (cause != null) "${e.javaClass.simpleName}: ${e.message} (caused by ${cause.javaClass.simpleName}: ${cause.message})" else "${e.javaClass.simpleName}: ${e.message}"
            log("Connection failed: $detail")
            throw SshException("Connection failed: $detail", e)
        }
    }

    fun disconnect() {
        session?.disconnect()
        session = null
        Log.i(TAG, "Disconnected from ${config.hostname}")
    }

    fun getSession(): Session? = session

    suspend fun executeCommand(command: String): String = withContext(Dispatchers.IO) {
        val s = session ?: throw SshException("Not connected")
        val channel = s.openChannel("exec") as ChannelExec
        channel.setCommand(command)
        val outputStream = ByteArrayOutputStream()
        val errorStream = ByteArrayOutputStream()
        channel.outputStream = outputStream
        channel.setExtOutputStream(errorStream)
        channel.connect()

        while (!channel.isClosed) {
            Thread.sleep(50)
        }

        channel.disconnect()

        val exitStatus = channel.exitStatus
        if (exitStatus != 0 && exitStatus != -1) {
            val error = errorStream.toString("UTF-8")
            throw SshException("Command failed (exit=$exitStatus): $error")
        }

        outputStream.toString("UTF-8")
    }
}

class SshException(message: String, cause: Throwable? = null) : Exception(message, cause)
