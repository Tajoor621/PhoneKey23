package com.phonekey.vpn.ui.profiles

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.phonekey.vpn.R
import com.phonekey.vpn.data.parser.ConfigTexts
import com.phonekey.vpn.data.parser.WireGuardFields
import com.phonekey.vpn.domain.model.VpnProfile
import com.phonekey.vpn.ui.PhoneKeyViewModel
import com.phonekey.vpn.ui.encodeQr

@Composable
fun ProfilesScreen(
    profiles: List<VpnProfile>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    onQr: (VpnProfile) -> Unit,
) {
    if (profiles.isEmpty()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("No profiles yet", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "Add a WireGuard config from a file, a QR code, or by typing the keys. Xray and SSTP can be stored, but those engines are not bundled.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(profiles, key = { it.id }) { profile ->
            ProfileRow(
                profile = profile,
                selected = profile.id == selectedId,
                onSelect = { onSelect(profile.id) },
                onEdit = { onEdit(profile.id) },
                onDelete = { onDelete(profile.id) },
                onQr = { onQr(profile) },
            )
        }
    }
}

@Composable
private fun ProfileRow(
    profile: VpnProfile,
    selected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onQr: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
            .clickable(onClick = onSelect)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(profile.protocol.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(profile.name, style = MaterialTheme.typography.titleMedium)
                profile.endpoint?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (selected) {
                Text("In use", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
        Row {
            IconButton(onClick = onEdit) {
                Icon(painterResource(R.drawable.ic_edit), contentDescription = "Edit")
            }
            IconButton(onClick = onQr) {
                Icon(painterResource(R.drawable.ic_qr), contentDescription = "Show QR")
            }
            IconButton(onClick = onDelete) {
                Icon(painterResource(R.drawable.ic_trash), contentDescription = "Delete")
            }
        }
    }
}

@Composable
fun AddConfigScreen(
    vm: PhoneKeyViewModel,
    onBack: () -> Unit,
    onFile: () -> Unit,
    onQr: () -> Unit,
) {
    var mode by remember { mutableStateOf(AddMode.Choose) }
    var name by remember { mutableStateOf("") }
    var fields by remember { mutableStateOf(WireGuardFields()) }
    var raw by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        vm.saved.collect { onBack() }
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                if (mode == AddMode.Choose) onBack() else mode = AddMode.Choose
            }) {
                Icon(painterResource(R.drawable.ic_back), contentDescription = "Back")
            }
            Text("Add profile", style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(12.dp))
        when (mode) {
            AddMode.Choose -> {
                Choice("Import a file") { onFile() }
                Choice("Scan a QR code") { onQr() }
                Choice("WireGuard form") { mode = AddMode.Manual }
                Choice("Paste config") { mode = AddMode.Paste }
                OutlinedButton(
                    onClick = { vm.saveRaw(null, "Example", ConfigTexts.EXAMPLE_WIREGUARD.trim()) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Load the example config") }
                Text(
                    "The example uses documentation IP 203.0.113.1. It will not open a real tunnel.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            AddMode.Manual -> {
                Field("Name", name) { name = it }
                Field("Private key", fields.privateKey) { fields = fields.copy(privateKey = it) }
                Field("Address", fields.address) { fields = fields.copy(address = it) }
                Field("DNS", fields.dns) { fields = fields.copy(dns = it) }
                Field("MTU", fields.mtu, KeyboardType.Number) { fields = fields.copy(mtu = it) }
                Field("Peer public key", fields.publicKey) { fields = fields.copy(publicKey = it) }
                Field("Preshared key", fields.presharedKey) { fields = fields.copy(presharedKey = it) }
                Field("Endpoint", fields.endpoint) { fields = fields.copy(endpoint = it) }
                Field("Allowed IPs", fields.allowedIps) { fields = fields.copy(allowedIps = it) }
                Field("Keepalive", fields.keepalive, KeyboardType.Number) { fields = fields.copy(keepalive = it) }
                Button(onClick = { vm.saveManual(name, fields) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Save profile")
                }
            }
            AddMode.Paste -> {
                Field("Name (optional)", name) { name = it }
                OutlinedTextField(
                    value = raw,
                    onValueChange = { raw = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    label = { Text("WireGuard, Xray JSON, or SSTP") },
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = { vm.importText(raw, name.ifBlank { null }) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Import")
                }
            }
        }
    }
}

@Composable
fun EditConfigScreen(vm: PhoneKeyViewModel, profile: VpnProfile?, onBack: () -> Unit) {
    if (profile == null) {
        Column(Modifier.padding(24.dp)) { Text("That profile no longer exists.") }
        return
    }
    var name by remember(profile.id) { mutableStateOf(profile.name) }
    var raw by remember(profile.id) { mutableStateOf(profile.rawConfig.trim()) }
    LaunchedEffect(Unit) { vm.saved.collect { onBack() } }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_back), contentDescription = "Back")
            }
            Text("Edit", style = MaterialTheme.typography.titleLarge)
        }
        Text(profile.protocol.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Field("Name", name) { name = it }
        OutlinedTextField(
            value = raw,
            onValueChange = { raw = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp),
            label = { Text("Config") },
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = { vm.saveRaw(profile.id, name, raw) }, modifier = Modifier.fillMaxWidth()) {
            Text("Save")
        }
    }
}

@Composable
fun QrDialog(profile: VpnProfile?, onDismiss: () -> Unit) {
    if (profile == null) return
    val bitmap = remember(profile.rawConfig) { encodeQr(profile.rawConfig.trim()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(profile.name) },
        text = {
            Column {
                Text(
                    "This code contains the private key. Anyone who scans it can use the tunnel.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                if (bitmap == null) {
                    Text("This config is too large for a QR code.", color = MaterialTheme.colorScheme.error)
                } else {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "QR code for ${profile.name}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp)),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
fun DeleteDialog(profile: VpnProfile?, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    if (profile == null) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete ${profile.name}?") },
        text = { Text("The keys are removed from this phone. This cannot be undone.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Choice(label: String, onClick: () -> Unit) {
    Text(
        label,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(16.dp),
        style = MaterialTheme.typography.titleMedium,
    )
}

@Composable
private fun Field(
    label: String,
    value: String,
    keyboard: KeyboardType = KeyboardType.Text,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
    )
}

private enum class AddMode { Choose, Manual, Paste }
