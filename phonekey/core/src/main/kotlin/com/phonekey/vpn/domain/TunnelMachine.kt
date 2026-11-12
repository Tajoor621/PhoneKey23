package com.phonekey.vpn.domain

import com.phonekey.vpn.domain.model.Session
import com.phonekey.vpn.domain.model.TunnelStatus
import com.phonekey.vpn.domain.model.VpnProfile

/**
 * Connection state machine.
 *
 * States: DISCONNECTED, CONNECTING, CONNECTED, ERROR, and KILL_SWITCH_HOLD.
 * KILL_SWITCH_HOLD is ERROR with [Session.killSwitchHold]. It lasts only while
 * this process is alive. Android Always-on VPN plus "Block connections without VPN"
 * is the block that survives process death.
 *
 * Transitions:
 * - DISCONNECTED or ERROR -- connect --> CONNECTING
 * - CONNECTING -- handshake confirmed --> CONNECTED
 * - CONNECTING -- failure --> ERROR
 * - CONNECTING or CONNECTED or ERROR -- disconnect --> DISCONNECTED
 * - CONNECTED -- tunnel down, retries left --> ERROR, reconnect scheduled
 * - CONNECTED -- tunnel down, kill switch, retries exhausted, blackhole held --> KILL_SWITCH_HOLD
 * - CONNECTED -- tunnel down, otherwise --> ERROR
 * - any active state -- VPN revoked --> ERROR, generation bumped, no reconnect
 * - a delayed reconnect whose generation is stale is ignored
 * - CONNECTING or CONNECTED -- another connect --> rejected (no second tunnel)
 */
class TunnelMachine {
    var session: Session = Session()
        private set

    var reconnects: Int = 0
        private set

    var generation: Int = 0
        private set

    private var ignoreDown: Boolean = true

    fun isCurrent(expected: Int): Boolean = generation == expected

    sealed interface Begin {
        data object AlreadyActive : Begin
        data class Started(val generation: Int) : Begin
    }

    fun beginConnect(profile: VpnProfile, resetAttempts: Boolean = true): Begin {
        if (session.status == TunnelStatus.CONNECTED || session.status == TunnelStatus.CONNECTING) {
            return Begin.AlreadyActive
        }
        generation += 1
        ignoreDown = true
        if (resetAttempts) reconnects = 0
        session = Session(
            status = TunnelStatus.CONNECTING,
            profileId = profile.id,
            profileName = profile.name,
            protocol = profile.protocol,
        )
        return Begin.Started(generation)
    }

    fun noteHandshakeWait() {
        if (session.status != TunnelStatus.CONNECTING) return
        session = session.copy(message = HANDSHAKE_WAIT)
    }

    fun markConnected(sinceEpochMs: Long, advisory: String?) {
        if (session.status != TunnelStatus.CONNECTING) return
        ignoreDown = false
        reconnects = 0
        session = session.copy(
            status = TunnelStatus.CONNECTED,
            sinceEpochMs = sinceEpochMs,
            message = advisory,
            killSwitchHold = false,
        )
    }

    fun fail(message: String) {
        ignoreDown = true
        session = Session(
            status = TunnelStatus.ERROR,
            profileId = session.profileId,
            profileName = session.profileName,
            protocol = session.protocol,
            message = message,
        )
    }

    fun failBeforeStart(message: String, profile: VpnProfile? = null) {
        ignoreDown = true
        generation += 1
        session = Session(
            status = TunnelStatus.ERROR,
            profileId = profile?.id,
            profileName = profile?.name,
            protocol = profile?.protocol,
            message = message,
        )
    }

    fun disconnect() {
        generation += 1
        ignoreDown = true
        reconnects = 0
        session = Session(status = TunnelStatus.DISCONNECTED)
    }

    /** Revocation must not schedule a reconnect. A later user connect is a new generation. */
    fun onRevoked() {
        generation += 1
        ignoreDown = true
        reconnects = 0
        session = Session(
            status = TunnelStatus.ERROR,
            message = "Android revoked the VPN. PhoneKey will not reconnect until you connect again.",
        )
    }

    data class Down(
        val reconnect: Boolean,
        val delayMs: Long,
        val generation: Int,
        val profileId: String,
    )

    fun onTunnelDown(profileId: String, killSwitch: Boolean, blackholeHeld: Boolean): Down? {
        if (ignoreDown) return null
        if (session.profileId != profileId) return null
        if (session.status != TunnelStatus.CONNECTED) return null
        ignoreDown = true
        val name = session.profileName
        val protocol = session.protocol
        if (killSwitch && reconnects < TunnelLimits.MAX_RECONNECTS) {
            reconnects += 1
            session = Session(
                status = TunnelStatus.ERROR,
                profileId = profileId,
                profileName = name,
                protocol = protocol,
                killSwitchHold = blackholeHeld,
                message = if (blackholeHeld) {
                    "Tunnel dropped. Traffic is blocked while PhoneKey reconnects. " +
                        "This block ends if the app is killed. Always-on VPN is the durable switch."
                } else {
                    "Tunnel dropped. Trying to bring it back. The in-app block did not engage. " +
                        "Turn on Always-on VPN and “Block connections without VPN”."
                },
            )
            return Down(true, TunnelLimits.reconnectDelayMs(reconnects), generation, profileId)
        }
        if (killSwitch && blackholeHeld) {
            session = Session(
                status = TunnelStatus.ERROR,
                profileId = profileId,
                profileName = name,
                protocol = protocol,
                killSwitchHold = true,
                message = "Tunnel dropped. PhoneKey is blocking other traffic until you disconnect. " +
                    "This lasts only while the app process is alive. For a block that survives that, " +
                    "set Always-on VPN and “Block connections without VPN”.",
            )
            return Down(false, 0, generation, profileId)
        }
        session = Session(
            status = TunnelStatus.ERROR,
            profileId = profileId,
            profileName = name,
            protocol = protocol,
            message = if (killSwitch) {
                "Tunnel dropped and reconnect gave up. PhoneKey is not blocking traffic. Use system VPN lockdown."
            } else {
                "Tunnel dropped."
            },
        )
        return Down(false, 0, generation, profileId)
    }

    /**
     * A scheduled reconnect failed. Counts toward [TunnelLimits.MAX_RECONNECTS].
     * Does nothing for a user-started connect (reconnects == 0).
     */
    fun onReconnectAttemptFailed(killSwitch: Boolean, blackholeHeld: Boolean): Down? {
        if (reconnects <= 0) return null
        val profileId = session.profileId ?: return null
        if (killSwitch && reconnects < TunnelLimits.MAX_RECONNECTS) {
            reconnects += 1
            session = session.copy(
                status = TunnelStatus.ERROR,
                killSwitchHold = blackholeHeld,
                message = "Tunnel dropped. Trying to bring it back.",
            )
            return Down(true, TunnelLimits.reconnectDelayMs(reconnects), generation, profileId)
        }
        if (killSwitch && blackholeHeld) {
            session = session.copy(
                status = TunnelStatus.ERROR,
                killSwitchHold = true,
                message = "Tunnel dropped. PhoneKey is blocking other traffic until you disconnect. " +
                    "This lasts only while the app process is alive. For a block that survives that, " +
                    "set Always-on VPN and “Block connections without VPN”.",
            )
            return Down(false, 0, generation, profileId)
        }
        session = session.copy(
            status = TunnelStatus.ERROR,
            killSwitchHold = false,
            message = if (killSwitch) {
                "Tunnel dropped and reconnect gave up. PhoneKey is not blocking traffic. Use system VPN lockdown."
            } else {
                "Tunnel dropped."
            },
        )
        return Down(false, 0, generation, profileId)
    }

    fun noteBlackhole(held: Boolean, terminal: Boolean) {
        if (!held || session.status != TunnelStatus.ERROR) return
        if (!terminal) {
            session = session.copy(
                killSwitchHold = false,
                message = "Tunnel dropped. PhoneKey is blocking other traffic while it reconnects. " +
                    "This block ends if the app is killed. Always-on VPN is the durable switch.",
            )
            return
        }
        session = session.copy(
            killSwitchHold = true,
            message = "Tunnel dropped. PhoneKey is blocking other traffic until you disconnect. " +
                "This lasts only while the app process is alive. For a block that survives that, " +
                "set Always-on VPN and “Block connections without VPN”.",
        )
    }

    fun noteAdvisory(profileId: String, host: String) {
        if (session.profileId != profileId) return
        if (session.status != TunnelStatus.CONNECTED && session.status != TunnelStatus.CONNECTING) return
        val extra = "Could not resolve $host yet."
        val current = session.message
        if (current != null && host in current) return
        session = session.copy(message = if (current.isNullOrBlank()) extra else "$current $extra")
    }

    companion object {
        const val HANDSHAKE_WAIT = "Waiting for a WireGuard peer handshake"
    }
}

object TunnelLimits {
    const val MAX_RECONNECTS = 3
    const val HANDSHAKE_TIMEOUT_MS = 25_000L

    /**
     * Safety cap for the partial wake lock held across the whole connect attempt.
     * The handshake wait is 25 seconds; 60 seconds covers that plus service startup.
     * The lock is released in a finally as soon as the attempt finishes.
     */
    const val CONNECT_WAKE_MS = 60_000L
    const val PROBE_TIMEOUT_MS = 3_000L
    const val NETWORK_HANDSHAKE_TIMEOUT_MS = 15_000L
    const val NETWORK_DEBOUNCE_MS = 1_000L

    fun reconnectDelayMs(attempt: Int): Long = when (attempt) {
        1 -> 1_000L
        2 -> 2_000L
        else -> 4_000L
    }
}
