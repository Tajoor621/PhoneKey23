package com.phonekey.vpn.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.phonekey.vpn.PhoneKeyApp

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val app = context.applicationContext as PhoneKeyApp
        when (intent?.action) {
            PhoneKeyVpnService.ACTION_DISCONNECT -> app.container.vpnRepository.disconnect()
            PhoneKeyVpnService.ACTION_CONNECT -> app.container.vpnRepository.connectSelected()
        }
    }
}
