package com.sshautoforward.ssh

import android.util.Log
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Logger
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

class SshConnection(private val config: SshConfig) {

    companion object {
        private const val TAG = "SshConnection"
        private const val DEFAULT_TIMEOUT = 30000

        init {
            JSch.setLogger(object : Logger {
                override fun isEnabled(level: Int): Boolean = true
                override fun log(level: Int, message: String) {
                    when (level) {
                        Logger.DEBUG -> Log.d("JSch", message)
                        Logger.INFO -> Log.i("JSch", message)
                        Logger.WARN -> Log.w("JSch", message)
                        Logger.ERROR -> Log.e("JSch", message)
                        Logger.FATAL -> Log.e("JSch", message)
                    }
                }
            })
        }
    }

    private var session: Session? = null

    val isConnected: Boolean get() = session?.isConnected == true

    suspend fun connect(): Session = withContext(Dispatchers.IO) {
        if (session?.isConnected == true) {
            return@withContext session!!
        }

        val jsch = JSch()
        try {
            val keyFile = java.io.File(config.privateKeyPath)
            Log.i(TAG, "Key file path: ${config.privateKeyPath}")
            Log.i(TAG, "Key file exists: ${keyFile.exists()}, size: ${if (keyFile.exists()) keyFile.length() else 0}")
            if (!keyFile.exists()) {
                throw SshException("Private key file not found: ${config.privateKeyPath}")
            }
            if (config.passphrase != null) {
                Log.i(TAG, "Loading identity with passphrase...")
                jsch.addIdentity(config.privateKeyPath, config.passphrase)
            } else {
                Log.i(TAG, "Loading identity without passphrase...")
                jsch.addIdentity(config.privateKeyPath)
            }
            Log.i(TAG, "Identity loaded successfully, name: ${jsch.identityNames}")
        } catch (e: SshException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load private key: ${e.message}", e)
            throw SshException("Failed to load private key: ${e.message}", e)
        }

        val s = jsch.getSession(config.username, config.hostname, config.port)
        s.setConfig(Properties().apply {
            put("StrictHostKeyChecking", "no")
            put("PreferredAuthentications", "publickey")
        })
        s.timeout = DEFAULT_TIMEOUT
        s.setServerAliveInterval(config.keepAliveInterval * 1000)
        s.setServerAliveCountMax(3)

        try {
            Log.i(TAG, "Creating session: ${config.username}@${config.hostname}:${config.port}")
            Log.i(TAG, "Config: StrictHostKeyChecking=no, PreferredAuthentications=publickey, timeout=${DEFAULT_TIMEOUT}ms")
            s.connect()
            this@SshConnection.session = s
            Log.i(TAG, "Connected to ${config.hostname}:${config.port}, server version: ${s.serverVersion}")
            s
        } catch (e: Exception) {
            Log.e(TAG, "Connection failed: ${e.message}", e)
            throw SshException("Connection failed: ${e.message}", e)
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
