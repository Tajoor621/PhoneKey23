package com.phonekey.vpn

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.phonekey.vpn.service.PhoneKeyVpnService
import com.wireguard.android.backend.GoBackend

class PhoneKeyApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        ensureChannel()
        GoBackend.setAlwaysOnCallback {
            container.vpnRepository.onAlwaysOnTriggered()
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            PhoneKeyVpnService.CHANNEL_ID,
            getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.channel_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }
}
