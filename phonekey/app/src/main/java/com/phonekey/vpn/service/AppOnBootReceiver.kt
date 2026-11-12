package com.phonekey.vpn.service

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.app.NotificationCompat
import com.phonekey.vpn.MainActivity
import com.phonekey.vpn.PhoneKeyApp
import com.phonekey.vpn.R
import kotlinx.coroutines.launch

/**
 * Decides whether auto-connect is allowed, then starts the foreground service
 * and returns. The handshake state machine runs in the service process, not
 * inside this receiver's short goAsync window.
 *
 * If the system refuses a background foreground-service start (API 31+ limits,
 * and stricter background starts on Android 15+), a notification asks the user
 * to tap Connect. That path opens MainActivity, which still runs VpnService.prepare.
 */
class AppOnBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        val app = context.applicationContext as PhoneKeyApp
        app.container.appScope.launch {
            try {
                val settings = app.container.settingsRepository.snapshot()
                if (!settings.autoConnectOnBoot) return@launch
                if (VpnService.prepare(app) != null) return@launch
                try {
                    val token = PhoneKeyVpnService.armStart()
                    val start = Intent(app, PhoneKeyVpnService::class.java)
                        .putExtra(PhoneKeyVpnService.EXTRA_GENERATION, token)
                        .putExtra(PhoneKeyVpnService.EXTRA_BOOT, true)
                    app.startForegroundService(start)
                } catch (_: Exception) {
                    notifyNeedsTap(app)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun notifyNeedsTap(context: Context) {
        val open = Intent(context, MainActivity::class.java)
            .setAction(PhoneKeyVpnService.ACTION_CONNECT)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val tap = PendingIntent.getActivity(
            context,
            9,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, PhoneKeyVpnService.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_key)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.boot_needs_tap))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        manager.notify(BOOT_NOTIFICATION_ID, notification)
    }

    private companion object {
        const val BOOT_NOTIFICATION_ID = 43
    }
}
