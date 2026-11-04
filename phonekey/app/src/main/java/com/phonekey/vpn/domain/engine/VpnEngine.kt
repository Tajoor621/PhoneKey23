package com.phonekey.vpn.domain.engine

import com.phonekey.vpn.domain.model.Protocol
import com.phonekey.vpn.domain.model.VpnProfile

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

    /** Returns true only when the engine has verified a live peer connection. */
    suspend fun awaitConnection(timeoutMillis: Long): Boolean = true

    suspend fun disconnect()

    fun snapshot(): TrafficSnapshot?
}

fun interface TunnelEventListener {
    fun onDown(profileId: String)
}
