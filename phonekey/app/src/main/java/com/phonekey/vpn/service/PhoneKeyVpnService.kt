package com.phonekey.vpn.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.phonekey.vpn.MainActivity
import com.phonekey.vpn.PhoneKeyApp
import com.phonekey.vpn.R
import com.phonekey.vpn.domain.model.Session
import com.phonekey.vpn.domain.model.TunnelStatus
import com.wireguard.android.backend.GoBackend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Foreground [android.net.VpnService] that GoBackend actually drives.
 *
 * GoBackend completes a static future with whichever VpnService instance reaches
 * onCreate first. This subclass is started before setState(), shows the ongoing
 * notification, and is the only VpnService declared in the manifest.
 */
class PhoneKeyVpnService : GoBackend.VpnService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var latest: Session = Session()

    override fun onCreate() {
        super.onCreate()
        instance = this
        startForegroundCompat(buildNotification(latest))
        val app = application as PhoneKeyApp
        scope.launch {
            app.container.vpnRepository.session.collect { session ->
                latest = session
                val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
                manager.notify(NOTIFICATION_ID, buildNotification(session))
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val result = super.onStartCommand(intent, flags, startId)
        startForegroundCompat(buildNotification(latest))
        return result
    }

    override fun onDestroy() {
        scope.cancel()
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun stopQuietly() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_VPN)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(session: Session): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val disconnect = actionPending(ACTION_DISCONNECT, 1)
        val connect = actionPending(ACTION_CONNECT, 2)
        val title = session.profileName ?: getString(R.string.app_name)
        val text = when (session.status) {
            TunnelStatus.CONNECTED -> buildString {
                append(getString(R.string.notif_connected))
                append(" · ")
                append(formatRate(session.rxSpeedBps))
                append(" down · ")
                append(formatRate(session.txSpeedBps))
                append(" up")
            }
            TunnelStatus.CONNECTING -> getString(R.string.notif_starting)
            TunnelStatus.ERROR -> session.message ?: getString(R.string.notif_disconnected)
            TunnelStatus.DISCONNECTED -> getString(R.string.notif_disconnected)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_key)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(session.status == TunnelStatus.CONNECTED || session.status == TunnelStatus.CONNECTING)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, getString(R.string.action_connect), connect)
            .addAction(0, getString(R.string.action_disconnect), disconnect)
            .build()
    }

    private fun actionPending(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, NotificationActionReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val CHANNEL_ID = "phonekey_vpn"
        const val ACTION_DISCONNECT = "com.phonekey.vpn.action.DISCONNECT"
        const val ACTION_CONNECT = "com.phonekey.vpn.action.CONNECT"
        private const val NOTIFICATION_ID = 42
        private const val READY_TIMEOUT_MS = 5_000L

        @Volatile
        private var instance: PhoneKeyVpnService? = null

        suspend fun awaitReady(): Boolean {
            val start = SystemClock.elapsedRealtime()
            while (SystemClock.elapsedRealtime() - start < READY_TIMEOUT_MS) {
                if (instance != null) return true
                delay(20)
            }
            return instance != null
        }

        fun requestStop() {
            instance?.stopQuietly()
        }

        private fun formatRate(bps: Double): String {
            val value = bps.coerceAtLeast(0.0)
            return when {
                value < 1024 -> String.format(Locale.US, "%.0f B/s", value)
                value < 1024 * 1024 -> String.format(Locale.US, "%.1f KB/s", value / 1024)
                else -> String.format(Locale.US, "%.1f MB/s", value / (1024 * 1024))
            }
        }
    }
}
