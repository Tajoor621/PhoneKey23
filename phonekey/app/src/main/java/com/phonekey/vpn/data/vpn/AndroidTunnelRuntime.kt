package com.phonekey.vpn.data.vpn

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.PowerManager
import com.phonekey.vpn.domain.TunnelLimits
import com.phonekey.vpn.domain.engine.TunnelRuntime
import com.phonekey.vpn.domain.engine.WakeHandle
import com.phonekey.vpn.domain.model.Protocol
import com.phonekey.vpn.service.PhoneKeyVpnService

/**
 * Wake lock choice: hold a partial wake lock for the whole connect attempt,
 * capped at [TunnelLimits.CONNECT_WAKE_MS] (60 seconds), and release it in the
 * repository's finally. That is longer than the 25 second handshake wait, so a
 * handshake that takes 24 seconds is not cut off. The foreground service alone
 * does not cover the gap before the service is running.
 */
class AndroidTunnelRuntime(
    private val context: Context,
    private val engines: EngineFactory,
) : TunnelRuntime {
    override fun engine(protocol: Protocol) = engines.forProtocol(protocol)

    override suspend fun startServiceAndAwait(): Boolean {
        return try {
            val token = PhoneKeyVpnService.armStart()
            val intent = Intent(context, PhoneKeyVpnService::class.java)
                .putExtra(PhoneKeyVpnService.EXTRA_GENERATION, token)
            PhoneKeyVpnService.start(context, intent)
            PhoneKeyVpnService.awaitReady()
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun awaitServiceReady(): Boolean = PhoneKeyVpnService.awaitReady()

    override fun stopService() {
        PhoneKeyVpnService.requestStop()
    }

    override fun holdBlackhole(): Boolean = PhoneKeyVpnService.holdBlackhole()

    override fun releaseBlackhole() {
        PhoneKeyVpnService.releaseBlackhole()
    }

    override fun now(): Long = System.currentTimeMillis()

    override fun acquireConnectWakeLock(): WakeHandle {
        val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val lock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "phonekey:connect")
        lock.setReferenceCounted(false)
        lock.acquire(TunnelLimits.CONNECT_WAKE_MS)
        return WakeHandle {
            if (lock.isHeld) lock.release()
        }
    }
}

class UnderlyingNetworkWatcher(
    context: Context,
    private val vpn: VpnRepository,
) {
    private val cm = context.getSystemService(ConnectivityManager::class.java)
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val caps = cm.getNetworkCapabilities(network) ?: return
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return
            vpn.onUnderlyingNetworkChanged()
        }

        override fun onLost(network: Network) {
            vpn.onUnderlyingNetworkChanged()
        }
    }

    fun start() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(request, callback)
    }
}
