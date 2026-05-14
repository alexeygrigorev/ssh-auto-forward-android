package com.sshautoforward.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.sshautoforward.R
import com.sshautoforward.data.repository.HostRepository
import com.sshautoforward.data.repository.SshKeyRepository
import com.sshautoforward.ssh.AutoForwarder
import com.sshautoforward.ssh.AutoForwarderEvent
import com.sshautoforward.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ForwardingService : Service() {

    companion object {
        private const val TAG = "ForwardingService"
        private const val CHANNEL_ID = "ssh_forwarding"
        private const val NOTIFICATION_ID = 1
        private const val PREFS_NAME = "forwarding_service"
        private const val KEY_ACTIVE_HOST_ID = "active_host_id"

        const val ACTION_START = "com.sshautoforward.action.START"
        const val ACTION_STOP = "com.sshautoforward.action.STOP"
        const val EXTRA_HOST_ID = "host_id"

        fun start(context: Context, hostId: Long) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putLong(KEY_ACTIVE_HOST_ID, hostId).apply()
            val intent = Intent(context, ForwardingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_HOST_ID, hostId)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().remove(KEY_ACTIVE_HOST_ID).apply()
            val intent = Intent(context, ForwardingService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    @Inject lateinit var autoForwarder: AutoForwarder
    @Inject lateinit var hostRepository: HostRepository
    @Inject lateinit var sshKeyRepository: SshKeyRepository

    private var wakeLock: PowerManager.WakeLock? = null
    private var eventJob: Job? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var hostName: String = ""

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val hostId = intent.getLongExtra(EXTRA_HOST_ID, -1)
                if (hostId == -1L) return START_NOT_STICKY

                startForeground(NOTIFICATION_ID, buildNotification("Connecting...", 0))
                acquireWakeLock()
                observeEvents()
                registerNetworkCallback()

                if (autoForwarder.isRunning.value && autoForwarder.currentHostId == hostId) {
                    updateNotification("Connected", autoForwarder.ports.value.size)
                } else {
                    startForwarding(hostId)
                }
            }
            ACTION_STOP -> {
                stopForwarding()
            }
            else -> {
                val savedHostId = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .getLong(KEY_ACTIVE_HOST_ID, -1)
                if (savedHostId != -1L) {
                    startForeground(NOTIFICATION_ID, buildNotification("Reconnecting...", 0))
                    acquireWakeLock()
                    observeEvents()
                    registerNetworkCallback()
                    startForwarding(savedHostId)
                } else {
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        eventJob?.cancel()
        eventJob = null
        unregisterNetworkCallback()
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopForwarding()
        super.onTaskRemoved(rootIntent)
    }

    private fun startForwarding(hostId: Long) {
        CoroutineScope(Dispatchers.IO).launch {
            val host = hostRepository.getById(hostId) ?: run {
                stopForwarding()
                return@launch
            }
            val key = sshKeyRepository.getById(host.keyId) ?: run {
                stopForwarding()
                return@launch
            }
            hostName = host.name
            autoForwarder.start(host, key.privateKeyPath)
        }
    }

    private fun observeEvents() {
        if (eventJob?.isActive == true) return
        eventJob = CoroutineScope(Dispatchers.Main).launch {
            autoForwarder.events.collect { event ->
                when (event) {
                    is AutoForwarderEvent.Connected -> {
                        val tunnelCount = autoForwarder.ports.value.size
                        updateNotification(event.host, tunnelCount)
                    }
                    is AutoForwarderEvent.PortForwarded,
                    is AutoForwarderEvent.PortRemoved -> {
                        val tunnelCount = autoForwarder.ports.value.size
                        val name = hostName.ifEmpty { "SSH" }
                        updateNotification(name, tunnelCount)
                    }
                    is AutoForwarderEvent.Disconnected -> {
                        updateNotification("Disconnected — reconnecting...", 0)
                    }
                    is AutoForwarderEvent.Error -> {
                        if (!autoForwarder.isConnected.value) {
                            updateNotification("Error: ${event.message.take(40)}", 0)
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    private fun stopForwarding() {
        autoForwarder.stop()
        eventJob?.cancel()
        eventJob = null
        unregisterNetworkCallback()
        releaseWakeLock()
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove(KEY_ACTIVE_HOST_ID).apply()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun registerNetworkCallback() {
        if (networkCallback != null) return
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "Network available: $network")
                if (autoForwarder.isRunning.value) {
                    autoForwarder.reconnectNow()
                }
            }

            override fun onLost(network: Network) {
                Log.d(TAG, "Network lost: $network")
            }
        }
        cm.registerDefaultNetworkCallback(networkCallback!!)
    }

    private fun unregisterNetworkCallback() {
        networkCallback?.let {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.unregisterNetworkCallback(it)
        }
        networkCallback = null
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "ssh-auto-forward::forwarding"
        ).apply {
            acquire(12 * 60 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }

    private fun updateNotification(label: String, tunnelCount: Int) {
        val notification = buildNotification(label, tunnelCount)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(hostName: String, tunnelCount: Int): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, ForwardingService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SSH Auto Forward")
            .setContentText("$hostName • $tunnelCount tunnels active")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "SSH Port Forwarding",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows active SSH port forwarding tunnels"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
}
