package com.phonekey.vpn.domain.engine

import com.phonekey.vpn.domain.model.Protocol
import com.phonekey.vpn.domain.model.VpnProfile
import kotlinx.coroutines.delay

data class EngineOptions(
    val packages: List<String> = emptyList(),
    val includeOnly: Boolean = false,
)

data class TrafficSnapshot(
    val rxBytes: Long,
    val txBytes: Long,
)

/**
 * One tunnel implementation. WireGuard is the only engine that connects.
 * Xray and SSTP refuse so a stale profile cannot open a tunnel.
 */
interface VpnEngine {
    val protocol: Protocol

    suspend fun connect(profile: VpnProfile, options: EngineOptions)

    /** Returns true only when the engine has verified a live peer handshake. */
    suspend fun awaitConnection(timeoutMillis: Long): Boolean = true

    fun latestHandshakeEpochMillis(): Long = 0L

    suspend fun awaitHandshakeNewerThan(previous: Long, timeoutMillis: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            val latest = latestHandshakeEpochMillis()
            if (latest > previous && latest > 0L) return true
            delay(500)
        }
        return false
    }

    /**
     * Ask the engine to bring the Android VPN service back after the next
     * [disconnect]. GoBackend's shutdown calls stopSelf(); a later
     * startForegroundService from the background is blocked on API 31+.
     * The service must restart itself while it is still a foreground service.
     */
    suspend fun prepareForRelaunch() {}

    suspend fun disconnect()

    fun snapshot(): TrafficSnapshot?
}

fun interface TunnelEventListener {
    fun onDown(profileId: String)
}

interface TunnelRuntime {
    fun engine(protocol: Protocol): VpnEngine
    suspend fun startServiceAndAwait(): Boolean
    suspend fun awaitServiceReady(): Boolean
    fun stopService()
    fun holdBlackhole(): Boolean
    fun releaseBlackhole()
    fun now(): Long
    fun acquireConnectWakeLock(): WakeHandle
}

fun interface WakeHandle {
    fun release()
}

fun interface EndpointProber {
    suspend fun unresolvedHost(configText: String): String?
}
