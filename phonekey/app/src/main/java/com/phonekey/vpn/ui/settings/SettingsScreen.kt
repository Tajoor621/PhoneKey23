package com.phonekey.vpn.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.phonekey.vpn.domain.model.AppSettings

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
) {
    val context = LocalContext.current
    var packageDraft by remember { mutableStateOf(settings.splitTunnelPackages.joinToString("\n")) }
    var packagesFocused by remember { mutableStateOf(false) }
    LaunchedEffect(settings.splitTunnelPackages, packagesFocused) {
        if (!packagesFocused) packageDraft = settings.splitTunnelPackages.joinToString("\n")
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        Toggle(
            title = "Auto-connect on boot",
            body = "After the phone starts, bring up the selected profile if VPN permission is already granted.",
            checked = settings.autoConnectOnBoot,
            onChecked = { value -> onChange { it.copy(autoConnectOnBoot = value) } },
        )
        Toggle(
            title = "Auto-connect on launch",
            body = "Connect when you open PhoneKey, without asking again.",
            checked = settings.autoConnectOnLaunch,
            onChecked = { value -> onChange { it.copy(autoConnectOnLaunch = value) } },
        )
        Toggle(
            title = "Kill switch",
            body = "If the tunnel drops, PhoneKey tries to reconnect. This is not a hard block. Android can still leak during the gap.",
            checked = settings.killSwitch,
            onChecked = { value -> onChange { it.copy(killSwitch = value) } },
        )
        Button(
            onClick = { context.startActivity(Intent(Settings.ACTION_VPN_SETTINGS)) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Open system VPN settings") }
        Text(
            "For a real block, set PhoneKey as Always-on VPN and enable “Block connections without VPN”.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
        )
        Toggle(
            title = "Split tunneling",
            body = "Packages below are written as ExcludedApplications on the WireGuard interface. Type them yourself. The app does not scan installed apps.",
            checked = settings.splitTunnelEnabled,
            onChecked = { value -> onChange { it.copy(splitTunnelEnabled = value) } },
        )
        OutlinedTextField(
            value = packageDraft,
            onValueChange = { text ->
                packageDraft = text
                val packages = text.split('\n', ',', ' ').map { it.trim() }.filter { it.isNotEmpty() }
                onChange { it.copy(splitTunnelPackages = packages) }
            },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { packagesFocused = it.isFocused },
            label = { Text("Excluded packages") },
            placeholder = { Text("com.example.browser") },
            minLines = 3,
        )
        Spacer(Modifier.height(24.dp))
        Text("About", style = MaterialTheme.typography.titleMedium)
        Text(
            "PhoneKey is free. No ads. No subscriptions. No accounts. No analytics. Keys stay in private app storage and are excluded from backup.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Toggle(
    title: String,
    body: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Column(Modifier.padding(bottom = 16.dp)) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Switch(checked = checked, onCheckedChange = onChecked)
        }
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
