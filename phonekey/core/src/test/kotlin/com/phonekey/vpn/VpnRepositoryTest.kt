package com.phonekey.vpn

import com.phonekey.vpn.data.vpn.VpnRepository
import com.phonekey.vpn.domain.ProfileSource
import com.phonekey.vpn.domain.SettingsSource
import com.phonekey.vpn.domain.TunnelLimits
import com.phonekey.vpn.domain.TunnelMachine
import com.phonekey.vpn.domain.engine.EndpointProber
import com.phonekey.vpn.domain.engine.EngineOptions
import com.phonekey.vpn.domain.engine.TrafficSnapshot
import com.phonekey.vpn.domain.engine.TunnelRuntime
import com.phonekey.vpn.domain.engine.VpnEngine
import com.phonekey.vpn.domain.engine.WakeHandle
import com.phonekey.vpn.domain.model.AppSettings
import com.phonekey.vpn.domain.model.Protocol
import com.phonekey.vpn.domain.model.TunnelStatus
import com.phonekey.vpn.domain.model.VpnProfile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VpnRepositoryTest {
    @Test
    fun connectSucceedsOnlyAfterHandshakeAndHoldsWakeLock() = runBlocking {
        val engine = FakeEngine(handshake = true)
        engine.onAwait = { assertTrue(engine.runtime.wakeHeld, "wake lock dropped before handshake") }
        val repo = harness(engine)
        repo.connectSelectedAndAwait()
        assertEquals(TunnelStatus.CONNECTED, repo.session.value.status)
        assertEquals(TunnelMachine.HANDSHAKE_WAIT, engine.sawHandshakeMessage)
        assertTrue(engine.runtime.wakeReleased)
        assertEquals(1, engine.connectCount)
        assertTrue(TunnelLimits.CONNECT_WAKE_MS >= 60_000L)
    }

    @Test
    fun handshakeTimeoutIsAnError() = runBlocking {
        val engine = FakeEngine(handshake = false)
        val repo = harness(engine)
        repo.connectSelectedAndAwait()
        assertEquals(TunnelStatus.ERROR, repo.session.value.status)
        assertTrue(repo.session.value.message?.contains("handshake") == true)
        assertTrue(engine.runtime.stopped > 0)
    }

    @Test
    fun connectFailureDoesNotReportConnected() = runBlocking {
        val engine = FakeEngine(handshake = true, connectError = IllegalStateException("port closed"))
        val repo = harness(engine)
        repo.connectSelectedAndAwait()
        assertEquals(TunnelStatus.ERROR, repo.session.value.status)
        assertEquals("port closed", repo.session.value.message)
    }

    @Test
    fun disconnectClearsTheSession() = runBlocking {
        val engine = FakeEngine(handshake = true)
        val repo = harness(engine)
        repo.connectSelectedAndAwait()
        repo.disconnect()
        withTimeout(2_000) {
            while (repo.session.value.status != TunnelStatus.DISCONNECTED) delay(10)
        }
        assertEquals(1, engine.disconnectCount)
    }

    @Test
    fun secondConnectWhileConnectingIsIgnored() = runBlocking {
        val engine = FakeEngine(handshake = true)
        val gate = CompletableDeferred<Unit>()
        engine.blockConnect = gate
        val repo = harness(engine)
        val first = launch { repo.connectSelectedAndAwait() }
        withTimeout(2_000) {
            while (engine.connectStarted == 0) delay(10)
        }
        repo.connectSelectedAndAwait()
        assertEquals(1, engine.connectStarted)
        gate.complete(Unit)
        first.join()
        assertEquals(1, engine.connectCount)
        assertEquals(TunnelStatus.CONNECTED, repo.session.value.status)
    }

    @Test
    fun disconnectCancelsADelayedReconnect() = runBlocking {
        val engine = FakeEngine(handshake = true)
        val delays = mutableListOf<Long>()
        val release = CompletableDeferred<Unit>()
        val repo = harness(engine, killSwitch = true, sleeper = { ms ->
            delays += ms
            if (ms >= 1_000L) release.await()
        })
        repo.connectSelectedAndAwait()
        repo.onTunnelDown(engine.profileId)
        withTimeout(2_000) {
            while (delays.none { it == 1_000L }) delay(10)
        }
        repo.disconnect()
        release.complete(Unit)
        delay(80)
        assertEquals(TunnelStatus.DISCONNECTED, repo.session.value.status)
        assertEquals(1, engine.connectCount)
    }

    @Test
    fun tunnelDropWithoutKillSwitchDoesNotHold() = runBlocking {
        val engine = FakeEngine(handshake = true)
        val repo = harness(engine, killSwitch = false, sleeper = { })
        repo.connectSelectedAndAwait()
        repo.onTunnelDown(engine.profileId)
        withTimeout(2_000) {
            while (repo.session.value.status != TunnelStatus.ERROR) delay(10)
        }
        assertEquals(false, repo.session.value.killSwitchHold)
        assertEquals("Tunnel dropped.", repo.session.value.message)
    }

    @Test
    fun killSwitchGivesUpAfterThreeFailedReconnects() = runBlocking {
        val engine = FakeEngine(handshake = true)
        val repo = harness(engine, killSwitch = true, sleeper = { })
        repo.connectSelectedAndAwait()
        engine.handshake = false
        repo.onTunnelDown(engine.profileId)
        withTimeout(2_000) {
            while (!repo.session.value.killSwitchHold) delay(10)
        }
        assertTrue(repo.session.value.message?.contains("blocking") == true)
        assertEquals(1 + TunnelLimits.MAX_RECONNECTS, engine.connectCount)
    }

    @Test
    fun revokeDoesNotReconnect() = runBlocking {
        val engine = FakeEngine(handshake = true)
        val repo = harness(engine, killSwitch = true, sleeper = { delay(it) })
        repo.connectSelectedAndAwait()
        val connects = engine.connectCount
        repo.onVpnRevoked()
        delay(100)
        assertEquals(TunnelStatus.ERROR, repo.session.value.status)
        assertTrue(repo.session.value.message?.contains("revoked") == true)
        assertEquals(connects, engine.connectCount)
    }

    @Test
    fun slowDnsDoesNotBlockConnect() = runBlocking {
        val engine = FakeEngine(handshake = true)
        val repo = harness(
            engine,
            probe = EndpointProber {
                delay(10_000)
                "slow.example"
            },
        )
        val started = System.currentTimeMillis()
        repo.connectSelectedAndAwait()
        val elapsed = System.currentTimeMillis() - started
        assertTrue(elapsed < 3_000, "connect waited ${elapsed}ms on DNS")
        assertEquals(TunnelStatus.CONNECTED, repo.session.value.status)
    }

    @Test
    fun alwaysOnUsesTheSelectedProfile() = runBlocking {
        val engine = FakeEngine(handshake = true)
        val repo = harness(engine)
        repo.onAlwaysOnTriggered()
        withTimeout(2_000) {
            while (repo.session.value.status != TunnelStatus.CONNECTED) delay(10)
        }
        assertEquals(true, engine.runtime.serviceAlreadyUpSeen)
    }

    private fun harness(
        engine: FakeEngine,
        killSwitch: Boolean = false,
        sleeper: suspend (Long) -> Unit = { },
        probe: EndpointProber = EndpointProber { null },
    ): VpnRepository {
        val runtime = FakeRuntime(engine)
        engine.runtime = runtime
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val profile = profile()
        engine.profileId = profile.id
        return VpnRepository(
            scope = scope,
            profiles = object : ProfileSource {
                override suspend fun find(id: String) = profile.takeIf { it.id == id }
            },
            settings = object : SettingsSource {
                override suspend fun snapshot() = AppSettings(
                    killSwitch = killSwitch,
                    selectedProfileId = profile.id,
                )
            },
            runtime = runtime,
            probe = probe,
            sleeper = sleeper,
        )
    }

    private fun profile() = VpnProfile(
        id = "p1",
        name = "Home",
        protocol = Protocol.WIREGUARD,
        rawConfig = "raw",
        endpoint = "10.0.0.1:51820",
    )
}

private class FakeRuntime(private val engine: FakeEngine) : TunnelRuntime {
    var wakeHeld = false
    var wakeReleased = false
    var stopped = 0
    var serviceAlreadyUpSeen = false
    override fun engine(protocol: Protocol): VpnEngine = engine
    override suspend fun startServiceAndAwait(): Boolean = true
    override suspend fun awaitServiceReady(): Boolean {
        serviceAlreadyUpSeen = true
        return true
    }
    override fun stopService() { stopped += 1 }
    override fun holdBlackhole(): Boolean = true
    override fun releaseBlackhole() = Unit
    override fun now(): Long = System.currentTimeMillis()
    override fun acquireConnectWakeLock(): WakeHandle {
        wakeHeld = true
        wakeReleased = false
        return WakeHandle {
            wakeHeld = false
            wakeReleased = true
        }
    }
}

private class FakeEngine(
    var handshake: Boolean,
    private val connectError: Exception? = null,
) : VpnEngine {
    override val protocol = Protocol.WIREGUARD
    var connectCount = 0
    var connectStarted = 0
    var disconnectCount = 0
    var profileId: String = ""
    var blockConnect: CompletableDeferred<Unit>? = null
    var onAwait: () -> Unit = {}
    lateinit var runtime: FakeRuntime
    var sawHandshakeMessage: String? = null

    override suspend fun connect(profile: VpnProfile, options: EngineOptions) {
        connectStarted += 1
        blockConnect?.await()
        connectCount += 1
        connectError?.let { throw it }
    }

    override suspend fun awaitConnection(timeoutMillis: Long): Boolean {
        onAwait()
        sawHandshakeMessage = TunnelMachine.HANDSHAKE_WAIT
        return handshake
    }

    override suspend fun disconnect() {
        disconnectCount += 1
    }

    override fun snapshot(): TrafficSnapshot? = null
}
