package com.phonekey.vpn.data.vpn

import android.content.Context
import com.phonekey.vpn.data.parser.ConfigTexts
import com.phonekey.vpn.domain.engine.EngineOptions
import com.phonekey.vpn.domain.engine.TrafficSnapshot
import com.phonekey.vpn.domain.engine.VpnEngine
import com.phonekey.vpn.domain.model.Protocol
import com.phonekey.vpn.domain.model.VpnProfile
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import okhttp3.OkHttpClient
import java.io.ByteArrayInputStream
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/**
 * Hard block is not possible on top of GoBackend without forking it.
 * [com.wireguard.android.backend.GoBackend.VpnService.stopSelf] is final and
 * tears the interface down. PhoneKey reconnects instead, and the settings
 * screen points at Android's Always-on VPN lockdown.
 *
 * TODO: keep a userspace-free VpnService.Builder.establish() alive across an
 * unexpected drop so packets are blackholed until the user disconnects.
 */
object KillSwitch {
    const val MAX_RECONNECTS = 3
}

class EndpointProbe {
    private val client = OkHttpClient.Builder()
        .dns { hostname -> InetAddress.getAllByName(hostname).toList() }
        .connectTimeout(4, TimeUnit.SECONDS)
        .build()

    /** Returns the host when a public DNS lookup fails. Never contacts an ad or telemetry host. */
    fun unresolvedHost(configText: String): String? {
        val endpoint = ConfigTexts.endpointHost(configText) ?: return null
        val host = endpoint.substringBeforeLast(':').trim().removePrefix("[").removeSuffix("]")
        if (host.isEmpty() || host.any { it == ':' } || host.all { it.isDigit() || it == '.' }) return null
        return try {
            if (client.dns.lookup(host).isEmpty()) host else null
        } catch (_: Exception) {
            host
        }
    }
}

private class NamedTunnel(
    private val name: String,
    private val onState: (Tunnel.State) -> Unit,
) : Tunnel {
    override fun getName(): String = name

    override fun onStateChange(newState: Tunnel.State) {
        onState(newState)
    }
}

class WireGuardEngine(
    context: Context,
    private val onTunnelDown: (String) -> Unit,
) : VpnEngine {
    override val protocol = Protocol.WIREGUARD
    private val backend by lazy { GoBackend(context.applicationContext) }
    private var active: NamedTunnel? = null
    private var activeProfileId: String? = null

    override suspend fun connect(profile: VpnProfile, options: EngineOptions) {
        val text = ConfigTexts.withExcludedApplications(profile.rawConfig, options.excludedPackages)
        val config = try {
            Config.parse(ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)))
        } catch (error: Exception) {
            throw IllegalArgumentException(
                "WireGuard rejected this config: ${error.message ?: "invalid .conf"}",
                error,
            )
        }
        val tunnel = NamedTunnel(ConfigTexts.tunnelName(profile)) { state ->
            if (state == Tunnel.State.DOWN) {
                activeProfileId?.let(onTunnelDown)
            }
        }
        active = tunnel
        activeProfileId = profile.id
        val result = backend.setState(tunnel, Tunnel.State.UP, config)
        if (result != Tunnel.State.UP) {
            throw IllegalStateException("WireGuard stayed down.")
        }
    }

    override suspend fun disconnect() {
        val tunnel = active ?: return
        try {
            backend.setState(tunnel, Tunnel.State.DOWN, null)
        } finally {
            active = null
            activeProfileId = null
        }
    }

    override fun snapshot(): TrafficSnapshot? {
        val tunnel = active ?: return null
        return try {
            val stats = backend.getStatistics(tunnel)
            TrafficSnapshot(stats.totalRx(), stats.totalTx())
        } catch (_: Exception) {
            null
        }
    }
}

class XrayEngine : VpnEngine {
    override val protocol = Protocol.XRAY

    override suspend fun connect(profile: VpnProfile, options: EngineOptions) {
        throw UnsupportedOperationException(
            "Xray/V2Ray is not bundled. TODO: integrate a maintained open-source core " +
                "(libXray or xray-core), pass PhoneKeyVpnService's TUN fd into it, and " +
                "feed it the stored JSON. Nothing is sent anywhere.",
        )
    }

    override suspend fun disconnect() = Unit

    override fun snapshot(): TrafficSnapshot? = null
}

class SstpEngine : VpnEngine {
    override val protocol = Protocol.SSTP

    override suspend fun connect(profile: VpnProfile, options: EngineOptions) {
        throw UnsupportedOperationException(
            "SSTP is not bundled. TODO: integrate a maintained open-source SSTP client " +
                "and route it through PhoneKeyVpnService. The profile text is stored only.",
        )
    }

    override suspend fun disconnect() = Unit

    override fun snapshot(): TrafficSnapshot? = null
}

class EngineFactory(
    context: Context,
    onTunnelDown: (String) -> Unit,
) {
    private val wireGuard by lazy { WireGuardEngine(context, onTunnelDown) }
    private val xray = XrayEngine()
    private val sstp = SstpEngine()

    fun forProtocol(protocol: Protocol): VpnEngine = when (protocol) {
        Protocol.WIREGUARD -> wireGuard
        Protocol.XRAY -> xray
        Protocol.SSTP -> sstp
    }
}
