package com.phonekey.vpn.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.phonekey.vpn.data.store.LaunchableApp
import com.phonekey.vpn.domain.model.AppSettings

@Composable
fun SettingsScreen(
    settings: AppSettings,
    apps: List<LaunchableApp>,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    val context = LocalContext.current
    var packageDraft by remember { mutableStateOf(settings.splitTunnelPackages.joinToString("\n")) }
    var packagesFocused by remember { mutableStateOf(false) }
    var pickerOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
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
            body = "If the tunnel drops, PhoneKey reconnects a few times. While the app process is alive it can also hold a tunnel that forwards nothing. That block ends if the process dies.",
            checked = settings.killSwitch,
            onChecked = { value -> onChange { it.copy(killSwitch = value) } },
        )
        Button(
            onClick = { context.startActivity(Intent(Settings.ACTION_VPN_SETTINGS)) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Open system VPN settings") }
        Text(
            "For a block that survives a reboot or a force-stop, set PhoneKey as Always-on VPN and enable “Block connections without VPN”.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
        )
        Toggle(
            title = "Split tunneling",
            body = "Choose apps below, or type package names. Off the switch, nothing is filtered.",
            checked = settings.splitTunnelEnabled,
            onChecked = { value -> onChange { it.copy(splitTunnelEnabled = value) } },
        )
        Toggle(
            title = "Only selected apps use the VPN",
            body = "Off: selected apps bypass the tunnel. On: only selected apps use it.",
            checked = settings.splitIncludeOnly,
            onChecked = { value -> onChange { it.copy(splitIncludeOnly = value) } },
        )
        OutlinedButton(onClick = { pickerOpen = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Choose apps")
        }
        Spacer(Modifier.height(8.dp))
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
            label = { Text("Packages") },
            placeholder = { Text("com.example.browser") },
            minLines = 3,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onExport, modifier = Modifier.fillMaxWidth()) {
            Text("Export all profiles")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
            Text("Import profile list")
        }
        Text(
            "The export is JSON and includes private keys. A single profile can still be shared as a QR code.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(Modifier.height(24.dp))
        Text("Permissions", style = MaterialTheme.typography.titleMedium)
        Text(
            "Internet, network state, and the VPN service run the tunnel. Notifications show speeds and a disconnect action. Boot and a short wake lock are only used for auto-connect. The camera is asked for only when you scan a QR code.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(Modifier.height(24.dp))
        Text("About", style = MaterialTheme.typography.titleMedium)
        Text(
            "PhoneKey is free. No ads. No subscriptions. No accounts. No analytics. WireGuard keys are encrypted with Android Keystore and excluded from backup. Xray and SSTP are not implemented.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (pickerOpen) {
        val shown = apps.filter {
            query.isBlank() ||
                it.label.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
        }
        AlertDialog(
            onDismissRequest = { pickerOpen = false },
            confirmButton = {
                TextButton(onClick = { pickerOpen = false }) { Text("Done") }
            },
            title = { Text("Apps") },
            text = {
                Column {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Search") },
                        singleLine = true,
                    )
                    if (apps.isEmpty()) {
                        Text(
                            "No launchable apps were visible. Type package names instead.",
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(shown, key = { it.packageName }) { app ->
                            val checked = settings.splitTunnelPackages.contains(app.packageName)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { on ->
                                        val next = if (on) {
                                            settings.splitTunnelPackages + app.packageName
                                        } else {
                                            settings.splitTunnelPackages - app.packageName
                                        }
                                        onChange { it.copy(splitTunnelPackages = next.distinct()) }
                                    },
                                )
                                Column {
                                    Text(app.label)
                                    Text(
                                        app.packageName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            },
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Switch(checked = checked, onCheckedChange = onChecked)
        }
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
