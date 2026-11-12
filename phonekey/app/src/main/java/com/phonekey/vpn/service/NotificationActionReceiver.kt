package com.phonekey.vpn.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.phonekey.vpn.PhoneKeyApp

/**
 * Disconnect is allowed from the notification without another consent prompt.
 * Connect is not handled here: the notification opens MainActivity, which runs
 * VpnService.prepare before calling connect.
 */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val app = context.applicationContext as PhoneKeyApp
        if (intent?.action == PhoneKeyVpnService.ACTION_DISCONNECT) {
            app.container.vpnRepository.disconnect()
        }
    }
}
