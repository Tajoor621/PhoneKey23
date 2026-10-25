package com.phonekey.vpn.domain.model

import java.util.UUID

enum class Protocol {
    WIREGUARD,
    XRAY,
    SSTP,
    ;

    val label: String
        get() = when (this) {
            WIREGUARD -> "WireGuard"
            XRAY -> "Xray"
            SSTP -> "SSTP"
        }
}

enum class TunnelStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR,
}

data class VpnProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val protocol: Protocol,
    val rawConfig: String,
    val endpoint: String? = null,
    val address: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
)

data class AppSettings(
    val autoConnectOnBoot: Boolean = false,
    val autoConnectOnLaunch: Boolean = false,
    val killSwitch: Boolean = false,
    val splitTunnelEnabled: Boolean = false,
    val splitTunnelPackages: List<String> = emptyList(),
    val selectedProfileId: String? = null,
)

data class Session(
    val status: TunnelStatus = TunnelStatus.DISCONNECTED,
    val profileId: String? = null,
    val profileName: String? = null,
    val protocol: Protocol? = null,
    val sinceEpochMs: Long? = null,
    val rxBytes: Long = 0,
    val txBytes: Long = 0,
    val rxSpeedBps: Double = 0.0,
    val txSpeedBps: Double = 0.0,
    val message: String? = null,
)
