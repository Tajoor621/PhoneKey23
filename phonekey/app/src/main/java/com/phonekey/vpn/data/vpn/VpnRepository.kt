package com.phonekey.vpn.data.vpn

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.os.SystemClock
import com.phonekey.vpn.data.store.ProfileRepository
import com.phonekey.vpn.data.store.SettingsRepository
import com.phonekey.vpn.domain.engine.EngineOptions
import com.phonekey.vpn.domain.engine.VpnEngine
import com.phonekey.vpn.domain.model.Session
import com.phonekey.vpn.domain.model.TunnelStatus
import com.phonekey.vpn.domain.model.VpnProfile
import com.phonekey.vpn.service.PhoneKeyVpnService
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

class VpnRepository(
    private val context: Context,
    private val appScope: CoroutineScope,
    private val profiles: ProfileRepository,
    private val settings: SettingsRepository,
) {
    private val gate = Mutex()
    private val probe = EndpointProbe()
    private val engines = EngineFactory(context) { profileId -> onEngineReportedDown(profileId) }
    private val _session = MutableStateFlow(Session())
    val session: StateFlow<Session> = _session.asStateFlow()

    private var activeEngine: VpnEngine? = null
    private var ignoreDown = true
    private var pollJob: Job? = null
    private var reconnects = 0
    private var lastRx = 0L
    private var lastTx = 0L
    private var lastSampleAt = 0L

    fun connectSelected() {
        appScope.launch { connectSelectedAwait() }
    }

    fun disconnect() {
        appScope.launch { disconnectAwait() }
    }

    fun toggle() {
        appScope.launch {
            if (_session.value.status == TunnelStatus.CONNECTED ||
                _session.value.status == TunnelStatus.CONNECTING
            ) {
                disconnectAwait()
            } else {
                connectSelectedAwait()
            }
        }
    }

    fun onAlwaysOnTriggered() {
        appScope.launch {
            val selected = settings.snapshot().selectedProfileId ?: return@launch
            connectAwait(selected, serviceAlreadyUp = true)
        }
    }

    private suspend fun connectSelectedAwait() {
        val id = settings.snapshot().selectedProfileId
        if (id == null) {
            _session.update {
                it.copy(status = TunnelStatus.ERROR, message = "Choose a profile first.")
            }
            return
        }
        connectAwait(id, serviceAlreadyUp = false)
    }

    private suspend fun connectAwait(profileId: String, serviceAlreadyUp: Boolean) {
        gate.withLock {
            val current = _session.value.status
            if (current == TunnelStatus.CONNECTED || current == TunnelStatus.CONNECTING) return
            val profile = profiles.find(profileId)
            if (profile == null) {
                _session.value = Session(
                    status = TunnelStatus.ERROR,
                    message = "The selected profile is gone.",
                )
                return
            }
            bringUp(profile, serviceAlreadyUp)
        }
    }

    private suspend fun bringUp(profile: VpnProfile, serviceAlreadyUp: Boolean) {
        ignoreDown = true
        stopPolling()
        _session.value = Session(
            status = TunnelStatus.CONNECTING,
            profileId = profile.id,
            profileName = profile.name,
            protocol = profile.protocol,
            message = null,
        )
        val prefs = settings.snapshot()
        val engine = engines.forProtocol(profile.protocol)
        val wake = wakeLock()
        try {
            if (!serviceAlreadyUp) {
                startTunnelService()
                if (!PhoneKeyVpnService.awaitReady()) {
                    throw IllegalStateException("VPN service did not start.")
                }
            } else if (!PhoneKeyVpnService.awaitReady()) {
                throw IllegalStateException("Always-on VPN service was not ready.")
            }
            val unresolved = if (profile.protocol == com.phonekey.vpn.domain.model.Protocol.WIREGUARD) {
                probe.unresolvedHost(profile.rawConfig)
            } else {
                null
            }
            engine.connect(
                profile,
                EngineOptions(
                    excludedPackages = if (prefs.splitTunnelEnabled) prefs.splitTunnelPackages else emptyList(),
                ),
            )
            activeEngine = engine
            ignoreDown = false
            reconnects = 0
            _session.value = Session(
                status = TunnelStatus.CONNECTED,
                profileId = profile.id,
                profileName = profile.name,
                protocol = profile.protocol,
                sinceEpochMs = System.currentTimeMillis(),
                message = unresolved?.let { "Could not resolve $it yet. The tunnel is still up." },
            )
            startPolling(engine)
        } catch (error: Exception) {
            ignoreDown = true
            activeEngine = null
            runCatching { engine.disconnect() }
            PhoneKeyVpnService.requestStop()
            _session.value = Session(
                status = TunnelStatus.ERROR,
                profileId = profile.id,
                profileName = profile.name,
                protocol = profile.protocol,
                message = error.message ?: "Could not connect.",
            )
        } finally {
            if (wake.isHeld) wake.release()
        }
    }

    private suspend fun disconnectAwait() {
        gate.withLock {
            ignoreDown = true
            reconnects = 0
            stopPolling()
            val engine = activeEngine
            activeEngine = null
            runCatching { engine?.disconnect() }
            PhoneKeyVpnService.requestStop()
            _session.value = Session(status = TunnelStatus.DISCONNECTED)
        }
    }

    private fun onEngineReportedDown(profileId: String) {
        appScope.launch {
            gate.withLock {
                if (ignoreDown) return@withLock
                if (_session.value.profileId != profileId) return@withLock
                if (_session.value.status != TunnelStatus.CONNECTED) return@withLock
                ignoreDown = true
                stopPolling()
                activeEngine = null
                val prefs = settings.snapshot()
                if (prefs.killSwitch && reconnects < KillSwitch.MAX_RECONNECTS) {
                    reconnects += 1
                    _session.update {
                        it.copy(
                            status = TunnelStatus.ERROR,
                            message = "Tunnel dropped. Trying to bring it back. " +
                                "For a hard block, turn on Always-on VPN and “Block connections without VPN”.",
                        )
                    }
                    appScope.launch {
                        delay(700)
                        connectAwait(profileId, serviceAlreadyUp = false)
                    }
                } else {
                    PhoneKeyVpnService.requestStop()
                    _session.value = Session(
                        status = TunnelStatus.ERROR,
                        profileId = profileId,
                        profileName = _session.value.profileName,
                        protocol = _session.value.protocol,
                        message = if (prefs.killSwitch) {
                            "Tunnel dropped and reconnect gave up. Traffic is not being blocked by PhoneKey. Use system VPN lockdown."
                        } else {
                            "Tunnel dropped."
                        },
                    )
                }
            }
        }
    }

    private fun startTunnelService() {
        val intent = Intent(context, PhoneKeyVpnService::class.java)
        context.startForegroundService(intent)
    }

    private fun startPolling(engine: VpnEngine) {
        stopPolling()
        lastRx = 0L
        lastTx = 0L
        lastSampleAt = 0L
        pollJob = appScope.launch {
            while (isActive) {
                delay(1_000)
                val snap = engine.snapshot() ?: continue
                val now = SystemClock.elapsedRealtime()
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

    private fun wakeLock(): PowerManager.WakeLock {
        val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "phonekey:connect").apply {
            setReferenceCounted(false)
            acquire(20_000L)
        }
    }
}
