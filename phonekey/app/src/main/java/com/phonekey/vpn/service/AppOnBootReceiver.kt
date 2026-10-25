package com.phonekey.vpn.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.phonekey.vpn.PhoneKeyApp
import kotlinx.coroutines.launch

class AppOnBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        val app = context.applicationContext as PhoneKeyApp
        app.container.appScope.launch {
            try {
                val settings = app.container.settingsRepository.snapshot()
                if (settings.autoConnectOnBoot && VpnService.prepare(app) == null) {
                    app.container.vpnRepository.connectSelected()
                }
            } finally {
                pending.finish()
            }
        }
    }
}
