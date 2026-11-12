package com.phonekey.vpn.data.vpn

import com.phonekey.vpn.domain.ProfileSource
import com.phonekey.vpn.domain.SettingsSource
import com.phonekey.vpn.domain.TunnelLimits
import com.phonekey.vpn.domain.TunnelMachine
import com.phonekey.vpn.domain.engine.EndpointProber
import com.phonekey.vpn.domain.engine.EngineOptions
import com.phonekey.vpn.domain.engine.TunnelRuntime
import com.phonekey.vpn.domain.engine.VpnEngine
import com.phonekey.vpn.domain.model.Protocol
import com.phonekey.vpn.domain.model.ProtocolPolicy
import com.phonekey.vpn.domain.model.Session
import com.phonekey.vpn.domain.model.TunnelStatus
import com.phonekey.vpn.domain.model.VpnProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * See [TunnelMachine] for states and transitions.
 *
 * Wake lock: [TunnelRuntime.acquireConnectWakeLock] is held for the whole
 * connect attempt and released in a finally. The Android side acquires it for
 * [TunnelLimits.CONNECT_WAKE_MS] (60s), which is longer than the 25s handshake
 * wait, so a 24s handshake still holds the lock. A foreground service is not a
 * substitute for the first seconds before the service is up.
 *
 * DNS: [EndpointProber] runs outside [gate], with a 3s timeout. A slow lookup
 * never delays [connectAwait]. A failure is advisory only.
 */
class VpnRepository(
    private val scope: CoroutineScope,
    private val profiles: ProfileSource,
    private val settings: SettingsSource,
    private val runtime: TunnelRuntime,
    private val probe: EndpointProber = EndpointProber { null },
    private val sleeper: suspend (Long) -> Unit = { delay(it) },
) {
    private val gate = Mutex()
    private val machine = TunnelMachine()
    private val _session = MutableStateFlow(Session())
    val session: StateFlow<Session> = _session.asStateFlow()

    private var activeEngine: VpnEngine? = null
    private var pollJob: Job? = null
    private var networkJob: Job? = null
    private var pendingAdvisory: String? = null
    private var lastRx = 0L
    private var lastTx = 0L
    private var lastSampleAt = 0L

    fun connectSelected(serviceAlreadyUp: Boolean = false) {
        scope.launch { connectAwait(profileId = null, serviceAlreadyUp = serviceAlreadyUp, expectedGeneration = null) }
    }

    suspend fun connectSelectedAndAwait(serviceAlreadyUp: Boolean = false) {
        connectAwait(profileId = null, serviceAlreadyUp = serviceAlreadyUp, expectedGeneration = null)
    }

    fun disconnect() {
        scope.launch { disconnectAwait() }
    }

    fun toggle() {
        scope.launch {
            val status = machine.session.status
            if (status == TunnelStatus.CONNECTED || status == TunnelStatus.CONNECTING) {
                disconnectAwait()
            } else {
                connectAwait(null, serviceAlreadyUp = false, expectedGeneration = null)
            }
        }
    }

    fun onAlwaysOnTriggered() {
        scope.launch {
            val selected = settings.snapshot().selectedProfileId ?: return@launch
            connectAwait(selected, serviceAlreadyUp = true, expectedGeneration = null)
        }
    }

    fun onVpnRevoked() {
        scope.launch {
            val engine = gate.withLock {
                machine.onRevoked()
                stopPolling()
                val engine = activeEngine
                activeEngine = null
                publish()
                engine
            }
            runCatching { engine?.disconnect() }
            runtime.stopService()
        }
    }

    fun onTunnelDown(profileId: String) {
        scope.launch { handleDown(profileId) }
    }

    /**
     * Underlying Wi-Fi or cellular changed. VPN networks must be filtered by the caller.
     * WireGuard is given [TunnelLimits.NETWORK_HANDSHAKE_TIMEOUT_MS] to re-handshake.
     * If it does not, reconnect with backoff. A fresh connect is not treated as a change.
     */
    fun onUnderlyingNetworkChanged() {
        networkJob?.cancel()
        networkJob = scope.launch {
            sleeper(TunnelLimits.NETWORK_DEBOUNCE_MS)
            val watched = gate.withLock {
                val current = machine.session
                if (current.status != TunnelStatus.CONNECTED) return@withLock null
                val since = current.sinceEpochMs
                if (since != null && runtime.now() - since < 8_000L) return@withLock null
                val engine = activeEngine ?: return@withLock null
                val id = current.profileId ?: return@withLock null
                Watch(engine, engine.latestHandshakeEpochMillis(), id, machine.generation)
            } ?: return@launch
            val fresh = watched.engine.awaitHandshakeNewerThan(
                watched.handshake,
                TunnelLimits.NETWORK_HANDSHAKE_TIMEOUT_MS,
            )
            if (fresh) return@launch
            val stillUp = gate.withLock {
                machine.isCurrent(watched.generation) && machine.session.status == TunnelStatus.CONNECTED
            }
            if (stillUp) handleDown(watched.profileId)
        }
    }

    private data class Watch(
        val engine: VpnEngine,
        val handshake: Long,
        val profileId: String,
        val generation: Int,
    )

    private suspend fun connectAwait(
        profileId: String?,
        serviceAlreadyUp: Boolean,
        expectedGeneration: Int?,
    ) {
        val id = profileId ?: settings.snapshot().selectedProfileId
        if (id == null) {
            gate.withLock {
                machine.failBeforeStart("Choose a profile first.")
                publish()
            }
            return
        }
        val profile = profiles.find(id)
        if (profile == null) {
            gate.withLock {
                machine.failBeforeStart("The selected profile is gone.")
                publish()
            }
            return
        }
        val blocked = ProtocolPolicy.connectBlockReason(profile)
        if (blocked != null) {
            gate.withLock {
                machine.failBeforeStart(blocked, profile)
                publish()
            }
            return
        }
        if (profile.protocol == Protocol.WIREGUARD) launchProbe(profile)
        val generation = gate.withLock {
            if (expectedGeneration != null && !machine.isCurrent(expectedGeneration)) return@withLock null
            when (val begin = machine.beginConnect(profile, resetAttempts = expectedGeneration == null)) {
                is TunnelMachine.Begin.AlreadyActive -> null
                is TunnelMachine.Begin.Started -> begin.generation
            }.also { started ->
                if (started != null) {
                    pendingAdvisory = null
                    publish()
                }
            }
        } ?: return
        val wake = runtime.acquireConnectWakeLock()
        var engine: VpnEngine? = null
        try {
            if (!machine.isCurrent(generation)) return
            val ready = if (serviceAlreadyUp) runtime.awaitServiceReady() else runtime.startServiceAndAwait()
            if (!ready) {
                throw IllegalStateException(
                    if (serviceAlreadyUp) "Always-on VPN service was not ready." else "VPN service did not start.",
                )
            }
            if (!machine.isCurrent(generation)) return
            runtime.releaseBlackhole()
            val prefs = settings.snapshot()
            engine = runtime.engine(profile.protocol)
            engine.connect(
                profile,
                EngineOptions(
                    packages = if (prefs.splitTunnelEnabled) prefs.splitTunnelPackages else emptyList(),
                    includeOnly = prefs.splitIncludeOnly,
                ),
            )
            activeEngine = engine
            gate.withLock {
                if (machine.isCurrent(generation)) {
                    machine.noteHandshakeWait()
                    publish()
                }
            }
            val ok = engine.awaitConnection(TunnelLimits.HANDSHAKE_TIMEOUT_MS)
            if (!machine.isCurrent(generation)) {
                runCatching { engine.disconnect() }
                if (activeEngine === engine) activeEngine = null
                return
            }
            if (!ok) {
                throw IllegalStateException(
                    "WireGuard started, but no peer handshake was confirmed within 25 seconds. " +
                        "Check the server endpoint, peer keys, UDP port, and server status.",
                )
            }
            gate.withLock {
                if (!machine.isCurrent(generation)) return@withLock
                val host = pendingAdvisory
                machine.markConnected(
                    runtime.now(),
                    host?.let { "Could not resolve $it yet. The tunnel is still up." },
                )
                publish()
            }
            if (machine.session.status == TunnelStatus.CONNECTED && activeEngine === engine) {
                startPolling(engine)
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Exception) {
            val plan = gate.withLock {
                if (!machine.isCurrent(generation)) return@withLock null
                if (expectedGeneration != null) {
                    val prefs = settings.snapshot()
                    val next = machine.onReconnectAttemptFailed(prefs.killSwitch, false)
                    if (next == null) machine.fail(error.message ?: "Could not connect.")
                    publish()
                    Recovery(next, prefs.killSwitch)
                } else {
                    machine.fail(error.message ?: "Could not connect.")
                    publish()
                    Recovery(null, false)
                }
            }
            if (activeEngine === engine) activeEngine = null
            val followup = plan?.next
            // GoBackend.setState(DOWN) calls stopSelf(). Ask for a relaunch first when
            // the tunnel should come back, so the service restarts itself before it dies.
            if (plan?.killSwitch == true || followup?.reconnect == true) {
                runCatching { engine?.prepareForRelaunch() }
            }
            runCatching { engine?.disconnect() }
            if (plan?.killSwitch == true && followup != null && machine.isCurrent(generation)) {
                val held = runtime.startServiceAndAwait() && runtime.holdBlackhole()
                if (held) {
                    gate.withLock {
                        if (machine.isCurrent(generation)) {
                            machine.noteBlackhole(true, terminal = followup?.reconnect != true)
                            publish()
                        }
                    }
                }
            } else if (followup == null || !followup.reconnect) {
                runtime.stopService()
            }
            if (followup?.reconnect == true) {
                sleeper(followup.delayMs)
                connectAwait(followup.profileId, serviceAlreadyUp = false, expectedGeneration = followup.generation)
            }
        } finally {
            wake.release()
        }
    }

    private fun launchProbe(profile: VpnProfile) {
        val id = profile.id
        val config = profile.rawConfig
        scope.launch {
            val host = try {
                withTimeoutOrNull(TunnelLimits.PROBE_TIMEOUT_MS) { probe.unresolvedHost(config) }
            } catch (_: Exception) {
                null
            }
            if (host.isNullOrBlank()) return@launch
            gate.withLock {
                if (machine.session.profileId != id) return@withLock
                pendingAdvisory = host
                machine.noteAdvisory(id, host)
                publish()
            }
        }
    }

    private data class Recovery(
        val next: TunnelMachine.Down?,
        val killSwitch: Boolean,
    )

    private data class Drop(
        val decision: TunnelMachine.Down,
        val engine: VpnEngine?,
        val killSwitch: Boolean,
    )

    private suspend fun handleDown(profileId: String) {
        val plan = gate.withLock {
            val prefs = settings.snapshot()
            // Blackhole is applied after the WireGuard device is down. Establishing it
            // first would be undone by GoBackend's stopSelf() inside setState(DOWN).
            val decision = machine.onTunnelDown(profileId, prefs.killSwitch, false) ?: return@withLock null
            stopPolling()
            val engine = activeEngine
            activeEngine = null
            publish()
            Drop(decision, engine, prefs.killSwitch)
        } ?: return
        if (plan.killSwitch || plan.decision.reconnect) {
            runCatching { plan.engine?.prepareForRelaunch() }
        }
        runCatching { plan.engine?.disconnect() }
        if (plan.killSwitch) {
            val held = runtime.startServiceAndAwait() && runtime.holdBlackhole()
            if (held) {
                gate.withLock {
                    if (machine.isCurrent(plan.decision.generation)) {
                        machine.noteBlackhole(true, terminal = !plan.decision.reconnect)
                        publish()
                    }
                }
            } else if (!plan.decision.reconnect) {
                runtime.stopService()
            }
        } else if (!plan.decision.reconnect) {
            runtime.stopService()
        }
        if (!plan.decision.reconnect) return
        sleeper(plan.decision.delayMs)
        connectAwait(plan.decision.profileId, serviceAlreadyUp = false, expectedGeneration = plan.decision.generation)
    }

    private suspend fun disconnectAwait() {
        val engine = gate.withLock {
            machine.disconnect()
            stopPolling()
            val engine = activeEngine
            activeEngine = null
            publish()
            runtime.releaseBlackhole()
            engine
        }
        runCatching { engine?.disconnect() }
        runtime.stopService()
    }

    private fun publish() {
        _session.value = machine.session
    }

    private fun startPolling(engine: VpnEngine) {
        stopPolling()
        lastRx = 0L
        lastTx = 0L
        lastSampleAt = 0L
        pollJob = scope.launch {
            while (isActive) {
                delay(1_000)
                if (!isActive) return@launch
                val snap = engine.snapshot() ?: continue
                val now = runtime.now()
                if (lastSampleAt == 0L) {
                    lastRx = snap.rxBytes
                    lastTx = snap.txBytes
                    lastSampleAt = now
                    _session.update { it.copy(rxBytes = snap.rxBytes, txBytes = snap.txBytes) }
                    continue
                }
                val seconds = ((now - lastSampleAt).coerceAtLeast(1L)) / 1000.0
                val rxBps = (snap.rxBytes - lastRx) / seconds
                val txBps = (snap.txBytes - lastTx) / seconds
                lastRx = snap.rxBytes
                lastTx = snap.txBytes
                lastSampleAt = now
                _session.update {
                    it.copy(
                        rxBytes = snap.rxBytes,
                        txBytes = snap.txBytes,
                        rxSpeedBps = rxBps.coerceAtLeast(0.0),
                        txSpeedBps = txBps.coerceAtLeast(0.0),
                    )
                }
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }
}
