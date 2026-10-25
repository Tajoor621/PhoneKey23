package com.phonekey.vpn.domain.engine

import com.phonekey.vpn.domain.model.Protocol
import com.phonekey.vpn.domain.model.VpnProfile

data class EngineOptions(
    val excludedPackages: List<String> = emptyList(),
)

data class TrafficSnapshot(
    val rxBytes: Long,
    val txBytes: Long,
)

/**
 * One tunnel implementation. [WireGuardEngine] is real. Xray and SSTP are
 * placeholders that refuse to connect until a maintained FOSS core is wired in.
 */
interface VpnEngine {
    val protocol: Protocol

    suspend fun connect(profile: VpnProfile, options: EngineOptions)

    suspend fun disconnect()

    fun snapshot(): TrafficSnapshot?
}

fun interface TunnelEventListener {
    fun onDown(profileId: String)
}
