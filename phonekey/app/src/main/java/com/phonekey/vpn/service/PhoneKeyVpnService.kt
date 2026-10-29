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
import java.util.concurrent.atomic.AtomicInteger

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
    private var boundGeneration = 0

    override fun onCreate() {
        super.onCreate()
        instance = this
        startForegroundCompat(buildNotification(latest))
        val app = application as PhoneKeyApp
        scope.launch {
            app.container.vpnRepository.session.collect { session ->
                latest = session
                if (boundGeneration != liveGeneration) return@collect
                val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
                manager.notify(NOTIFICATION_ID, buildNotification(session))
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val token = intent?.getIntExtra(EXTRA_GENERATION, NO_TOKEN) ?: NO_TOKEN
        val startedByUs = token != NO_TOKEN && token == liveGeneration
        if (token != NO_TOKEN) {
            boundGeneration = token
        } else if (boundGeneration == 0) {
            boundGeneration = liveGeneration
        }
        if (token != NO_TOKEN && boundGeneration != liveGeneration) {
            startForegroundCompat(buildNotification(latest))
            stopQuietly()
            return START_NOT_STICKY
        }
        // Parent completes GoBackend's service future. Its Always-on check looks for a
        // component whose package is not ours. The system starts Always-on with our
        // component set, so that check does not fire. A start without our generation
        // token is Always-on or a restart after the process died.
        val result = super.onStartCommand(intent, flags, startId)
        startForegroundCompat(buildNotification(latest))
        if (!startedByUs) {
            (application as PhoneKeyApp).container.vpnRepository.onAlwaysOnTriggered()
        }
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
        val connect = PendingIntent.getActivity(
            this,
            2,
            Intent(this, MainActivity::class.java)
                .setAction(ACTION_CONNECT)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
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
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_key)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(session.status == TunnelStatus.CONNECTED || session.status == TunnelStatus.CONNECTING)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (session.status == TunnelStatus.CONNECTED || session.status == TunnelStatus.CONNECTING) {
            builder.addAction(0, getString(R.string.action_disconnect), disconnect)
        } else {
            builder.addAction(0, getString(R.string.action_connect), connect)
        }
        return builder.build()
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
        const val EXTRA_GENERATION = "generation"
        private const val NO_TOKEN = -1
        private const val NOTIFICATION_ID = 42
        private const val READY_TIMEOUT_MS = 5_000L

        private val generation = AtomicInteger(0)

        @Volatile
        private var liveGeneration = 0

        @Volatile
        private var instance: PhoneKeyVpnService? = null

        /** Call immediately before [android.content.Context.startForegroundService]. */
        fun armStart(): Int {
            val token = generation.incrementAndGet()
            liveGeneration = token
            return token
        }

        fun requestStop() {
            liveGeneration = generation.incrementAndGet()
            instance?.stopIfStale()
        }

        private fun PhoneKeyVpnService.stopIfStale() {
            if (boundGeneration != liveGeneration) stopQuietly()
        }

        suspend fun awaitReady(): Boolean {
            val start = SystemClock.elapsedRealtime()
            while (SystemClock.elapsedRealtime() - start < READY_TIMEOUT_MS) {
                val running = instance
                if (running != null && running.boundGeneration == liveGeneration) return true
                delay(20)
            }
            val running = instance
            return running != null && running.boundGeneration == liveGeneration
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
