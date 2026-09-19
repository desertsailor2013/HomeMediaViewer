package com.hmv.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.hmv.server.HttpRangeServer

class MediaServerService : Service() {

    private var server: HttpRangeServer? = null
    private var items: List<com.hmv.server.MediaItem> = emptyList()
    private val binder = LocalBinder()
    private var nsdHelper: NsdHelper? = null

    inner class LocalBinder : Binder() {
        fun getService(): MediaServerService = this@MediaServerService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    @Suppress("UNCHECKED_CAST")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                items = if (Build.VERSION.SDK_INT >= 33) {
                    (intent.getSerializableExtra(EXTRA_ITEMS, ArrayList::class.java) as? ArrayList<com.hmv.server.MediaItem>)?.toList() ?: emptyList()
                } else {
                    @Suppress("DEPRECATION")
                    (intent.getSerializableExtra(EXTRA_ITEMS) as? ArrayList<com.hmv.server.MediaItem>)?.toList() ?: emptyList()
                }
                startServer()
            }
            ACTION_STOP -> {
                stopServer()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            else -> {}
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopServer()
        super.onDestroy()
    }

    val port: Int get() = server?.port ?: 0

    private fun startServer() {
        val repo = ContentMediaRepository(this, items)
        val s = HttpRangeServer(repo)
        s.start()
        server = s
        startForeground(NOTIFICATION_ID, buildNotification(s.port))

        nsdHelper = NsdHelper(this)
        nsdHelper?.registerService(s.port)
    }

    private fun stopServer() {
        nsdHelper?.unregisterService()
        nsdHelper = null
        server?.close()
        server = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(port: Int): Notification {
        val stopIntent = Intent(this, MediaServerService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openIntent = Intent(this, MainActivity::class.java)
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val ips = localIps().joinToString(", ") { "$it:$port" }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text, ips))
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(openPendingIntent)
            .addAction(android.R.drawable.ic_media_pause, getString(R.string.stop), stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun localIps(): List<String> {
        val result = mutableListOf<String>()
        try {
            java.net.NetworkInterface.getNetworkInterfaces().toList().forEach { ni ->
                if (ni.isUp && !ni.isLoopback) {
                    ni.inetAddresses.toList()
                        .filter { it is java.net.Inet4Address && !it.isLoopbackAddress }
                        .forEach { it.hostAddress?.let { addr -> result += addr } }
                }
            }
        } catch (_: Exception) {}
        return result
    }

    companion object {
        const val ACTION_START = "com.hmv.app.START_SERVER"
        const val ACTION_STOP = "com.hmv.app.STOP_SERVER"
        const val EXTRA_ITEMS = "extra_items"
        const val CHANNEL_ID = "media_server_channel"
        const val NOTIFICATION_ID = 1
    }
}
