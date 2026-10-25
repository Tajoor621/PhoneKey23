package com.phonekey.vpn.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon
import com.phonekey.vpn.R
import com.phonekey.vpn.domain.model.Session
import com.phonekey.vpn.domain.model.TunnelStatus
import com.phonekey.vpn.domain.model.VpnProfile
import com.phonekey.vpn.ui.formatBytes
import com.phonekey.vpn.ui.formatDuration
import com.phonekey.vpn.ui.formatRate
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    session: Session,
    selected: VpnProfile?,
    onToggle: () -> Unit,
    onChooseProfile: () -> Unit,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(session.status, session.sinceEpochMs) {
        if (session.status != TunnelStatus.CONNECTED) return@LaunchedEffect
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val busy = session.status == TunnelStatus.CONNECTING
    val on = session.status == TunnelStatus.CONNECTED || busy
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("PhoneKey", style = MaterialTheme.typography.headlineMedium)
        Text(
            "No ads. No subscription.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))
        Text(
            when (session.status) {
                TunnelStatus.CONNECTED -> "Connected"
                TunnelStatus.CONNECTING -> "Connecting"
                TunnelStatus.ERROR -> "Not connected"
                TunnelStatus.DISCONNECTED -> "Off"
            },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            if (session.status == TunnelStatus.CONNECTED) formatDuration(session.sinceEpochMs, now) else "00:00",
            style = MaterialTheme.typography.displaySmall,
        )
        Spacer(Modifier.height(28.dp))
        Box(
            modifier = Modifier
                .size(176.dp)
                .clip(CircleShape)
                .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                .clickable(role = Role.Button, enabled = !busy, onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_power),
                contentDescription = if (on) "Disconnect" else "Connect",
                modifier = Modifier.size(64.dp),
                tint = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            if (on) "Tap to disconnect" else "Tap to connect",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        session.message?.let {
            Spacer(Modifier.height(16.dp))
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = if (session.status == TunnelStatus.ERROR) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        Spacer(Modifier.weight(1f))
        ProfileCard(selected, onChooseProfile)
        if (session.status == TunnelStatus.CONNECTED) {
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Stat("Down", formatRate(session.rxSpeedBps), formatBytes(session.rxBytes), Modifier.weight(1f))
                Stat("Up", formatRate(session.txSpeedBps), formatBytes(session.txBytes), Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ProfileCard(selected: VpnProfile?, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Text("Profile", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(selected?.name ?: "None selected", style = MaterialTheme.typography.titleMedium)
        Text(
            selected?.let { "${it.protocol.label}${it.endpoint?.let { endpoint -> " · $endpoint" } ?: ""}" }
                ?: "Add a WireGuard config to start",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Stat(label: String, rate: String, total: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(rate, style = MaterialTheme.typography.titleMedium)
        Text(total, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
