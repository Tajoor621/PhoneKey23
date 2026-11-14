package com.phonekey.vpn.data.vpn

import android.content.Context
import com.phonekey.vpn.data.parser.ConfigTexts
import com.phonekey.vpn.domain.TunnelLimits
import com.phonekey.vpn.domain.engine.EndpointProber
import com.phonekey.vpn.domain.engine.EngineOptions
import com.phonekey.vpn.domain.engine.TrafficSnapshot
import com.phonekey.vpn.domain.engine.VpnEngine
import com.phonekey.vpn.domain.model.Protocol
import com.phonekey.vpn.domain.model.VpnProfile
import com.phonekey.vpn.service.PhoneKeyVpnService
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.net.InetAddress

/**
 * A userspace blackhole can be held by [com.phonekey.vpn.service.PhoneKeyVpnService]
 * after a drop, but only while that process is alive. Android's Always-on VPN
 * plus "Block connections without VPN" is the block that survives process death.
 */
object KillSwitch {
    const val MAX_RECONNECTS = TunnelLimits.MAX_RECONNECTS
}

class EndpointProbe : EndpointProber {
    /** Returns the host when a public DNS lookup fails. Never contacts an ad or telemetry host. */
    override suspend fun unresolvedHost(configText: String): String? = withContext(Dispatchers.IO) {
        val endpoint = ConfigTexts.endpointHost(configText) ?: return@withContext null
        val host = endpoint.substringBeforeLast(':').trim().removePrefix("[").removeSuffix("]")
        if (host.isEmpty() || host.any { it == ':' } || host.all { it.isDigit() || it == '.' }) return@withContext null
        try {
            if (InetAddress.getAllByName(host).isEmpty()) host else null
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
    private val appContext = context.applicationContext
    private val backend by lazy { GoBackend(appContext) }

    @Volatile
    private var active: NamedTunnel? = null

    @Volatile
    private var activeProfileId: String? = null

    @Volatile
    private var suppressDown = false

    @Volatile
    private var relaunch = false

    override suspend fun prepareForRelaunch() {
        relaunch = true
    }

    override suspend fun connect(profile: VpnProfile, options: EngineOptions) {
        withContext(Dispatchers.IO) {
            // A leftover tunnel must be turned off on the same Tunnel instance GoBackend
            // stored. setState(DOWN) calls stopSelf(), which clears the static service
            // future. Bring PhoneKeyVpnService back before UP. Otherwise GoBackend starts
            // GoBackend$VpnService, and that class is not in the manifest.
            val previous = active
            if (previous != null) {
                suppressDown = true
                val generation = PhoneKeyVpnService.currentGeneration()
                PhoneKeyVpnService.restartOnNextDestroy()
                try {
                    backend.setState(previous, Tunnel.State.DOWN, null)
                } catch (_: Exception) {
                    // The next UP still replaces whatever handle is left.
                } finally {
                    suppressDown = false
                    if (active === previous) {
                        active = null
                        activeProfileId = null
                    }
                }
                val restarted = PhoneKeyVpnService.awaitRelaunch(generation)
                if (!restarted && !PhoneKeyVpnService.ensureRunning(appContext)) {
                    throw IllegalStateException("VPN service did not start. Open PhoneKey and tap Connect.")
                }
            }
            val text = ConfigTexts.withApplicationFilter(
                profile.rawConfig,
                options.packages,
                options.includeOnly,
            )
            val config = try {
                Config.parse(ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)))
            } catch (error: Exception) {
                throw IllegalArgumentException(
                    "WireGuard rejected this config: ${error.message ?: "invalid .conf"}",
                    error,
                )
            }
            val tunnel = NamedTunnel(ConfigTexts.tunnelName(profile)) { state ->
                if (state == Tunnel.State.DOWN && !suppressDown) {
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
    }

    override suspend fun awaitConnection(timeoutMillis: Long): Boolean = withContext(Dispatchers.IO) {
        val tunnel = active ?: return@withContext false
        val deadline = SystemClock.elapsedRealtime() + timeoutMillis.coerceAtLeast(0L)
        while (SystemClock.elapsedRealtime() < deadline) {
            val stats = runCatching { backend.getStatistics(tunnel) }.getOrNull()
            val hasHandshake = stats?.peers()?.any { peerKey ->
                (stats.peer(peerKey)?.latestHandshakeEpochMillis() ?: 0L) > 0L
            } == true
            if (hasHandshake) return@withContext true
            delay(500)
        }
        false
    }

    override fun latestHandshakeEpochMillis(): Long {
        val tunnel = active ?: return 0L
        return try {
            val stats = backend.getStatistics(tunnel)
            stats.peers().maxOfOrNull { peerKey ->
                stats.peer(peerKey)?.latestHandshakeEpochMillis() ?: 0L
            } ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    override suspend fun disconnect() {
        val tunnel = active ?: return
        val again = relaunch
        relaunch = false
        val generation = PhoneKeyVpnService.currentGeneration()
        if (again) PhoneKeyVpnService.restartOnNextDestroy()
        suppressDown = true
        try {
            withContext(Dispatchers.IO) {
                backend.setState(tunnel, Tunnel.State.DOWN, null)
            }
        } finally {
            if (active === tunnel) {
                active = null
                activeProfileId = null
            }
            suppressDown = false
        }
        if (again) {
            val restarted = PhoneKeyVpnService.awaitRelaunch(generation)
            if (!restarted && !PhoneKeyVpnService.ensureRunning(appContext)) {
                throw IllegalStateException("VPN service did not start. Open PhoneKey and tap Connect.")
            }
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
        throw IllegalStateException(
            "Xray is not implemented. PhoneKey connects with WireGuard only.",
        )
    }

    override suspend fun disconnect() = Unit

    override fun snapshot(): TrafficSnapshot? = null
}

class SstpEngine : VpnEngine {
    override val protocol = Protocol.SSTP

    override suspend fun connect(profile: VpnProfile, options: EngineOptions) {
        throw IllegalStateException(
            "SSTP is not implemented. PhoneKey connects with WireGuard only.",
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
