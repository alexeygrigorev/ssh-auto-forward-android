package com.sshautoforward.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.sshautoforward.R
import com.sshautoforward.data.repository.HostRepository
import com.sshautoforward.data.repository.SshKeyRepository
import com.sshautoforward.ssh.AutoForwarder
import com.sshautoforward.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ForwardingService : Service() {

    companion object {
        private const val TAG = "ForwardingService"
        private const val CHANNEL_ID = "ssh_forwarding"
        private const val NOTIFICATION_ID = 1

        const val ACTION_START = "com.sshautoforward.action.START"
        const val ACTION_STOP = "com.sshautoforward.action.STOP"
        const val EXTRA_HOST_ID = "host_id"

        fun start(context: Context, hostId: Long) {
            val intent = Intent(context, ForwardingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_HOST_ID, hostId)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
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

                CoroutineScope(Dispatchers.IO).launch {
                    val host = hostRepository.getById(hostId) ?: return@launch
                    val key = sshKeyRepository.getById(host.keyId) ?: return@launch
                    autoForwarder.start(host, key.privateKeyPath)
                }
            }
            ACTION_STOP -> {
                stopForwarding()
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopForwarding()
        super.onDestroy()
    }

    private fun stopForwarding() {
        autoForwarder.stop()
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun acquireWakeLock() {
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

    private fun buildNotification(hostName: String, tunnelCount: Int): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
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
