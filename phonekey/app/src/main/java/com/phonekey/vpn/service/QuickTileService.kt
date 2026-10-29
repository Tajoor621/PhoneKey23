package com.phonekey.vpn.service

import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.phonekey.vpn.MainActivity
import com.phonekey.vpn.PhoneKeyApp
import com.phonekey.vpn.domain.model.TunnelStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class QuickTileService : TileService() {
    private var scope: CoroutineScope? = null

    override fun onStartListening() {
        super.onStartListening()
        scope?.cancel()
        val app = application as PhoneKeyApp
        val next = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = next
        next.launch {
            combine(
                app.container.vpnRepository.session,
                app.container.settingsRepository.settings,
            ) { session, settings -> session to settings.selectedProfileId }
                .collect { (session, selectedId) ->
                    val tile = qsTile ?: return@collect
                    tile.state = when {
                        selectedId == null -> Tile.STATE_UNAVAILABLE
                        session.status == TunnelStatus.CONNECTED ||
                            session.status == TunnelStatus.CONNECTING -> Tile.STATE_ACTIVE
                        else -> Tile.STATE_INACTIVE
                    }
                    tile.label = "PhoneKey"
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        tile.subtitle = when {
                            selectedId == null -> "No profile"
                            session.status == TunnelStatus.CONNECTED -> session.profileName ?: "Connected"
                            session.status == TunnelStatus.CONNECTING -> "Connecting"
                            session.status == TunnelStatus.ERROR -> "Error"
                            else -> "Off"
                        }
                    }
                    tile.updateTile()
                }
        }
    }

    override fun onStopListening() {
        scope?.cancel()
        scope = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        val app = application as PhoneKeyApp
        val status = app.container.vpnRepository.session.value.status
        if (status == TunnelStatus.CONNECTED || status == TunnelStatus.CONNECTING) {
            app.container.vpnRepository.disconnect()
            return
        }
        // Listening scope is cancelled as soon as the panel closes. Keep this on the app scope.
        app.container.appScope.launch(Dispatchers.Main.immediate) {
            val selected = app.container.settingsRepository.snapshot().selectedProfileId
            if (selected == null) {
                openApp()
                return@launch
            }
            val prepare = VpnService.prepare(this@QuickTileService)
            if (prepare != null) {
                prepare.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                collapse(prepare)
                return@launch
            }
            app.container.vpnRepository.connectSelected()
        }
    }

    private fun collapse(intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pending = PendingIntent.getActivity(
                this,
                7,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun openApp() {
        collapse(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
