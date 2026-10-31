package com.phonekey.vpn.data.parser

import com.phonekey.vpn.domain.model.Protocol
import com.phonekey.vpn.domain.model.VpnProfile
import java.util.Base64

data class WireGuardFields(
    val privateKey: String = "",
    val address: String = "",
    val dns: String = "",
    val mtu: String = "",
    val publicKey: String = "",
    val presharedKey: String = "",
    val endpoint: String = "",
    val allowedIps: String = "0.0.0.0/0, ::/0",
    val keepalive: String = "25",
)

object ConfigTexts {
    const val MAX_BYTES = 1_000_000

    fun tunnelName(profile: VpnProfile): String {
        val slug = profile.name.lowercase().filter { it.isLetterOrDigit() }.take(8)
        val tail = profile.id.filter { it.isLetterOrDigit() }.take(6).lowercase()
        val raw = (if (slug.isEmpty()) "pk" else slug) + tail
        return raw.take(15).ifEmpty { "phonekey" }
    }

    fun isPlausibleKey(value: String): Boolean {
        val trimmed = value.trim()
        if (trimmed.length !in 42..44) return false
        return try {
            Base64.getDecoder().decode(trimmed).size == 32
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    fun generateWireGuard(name: String, fields: WireGuardFields): Result<VpnProfile> {
        val privateKey = fields.privateKey.trim()
        val address = fields.address.trim()
        val publicKey = fields.publicKey.trim()
        val allowed = fields.allowedIps.trim()
        val endpoint = fields.endpoint.trim()
        when {
            name.isBlank() -> return Result.failure(IllegalArgumentException("Name the profile."))
            !isPlausibleKey(privateKey) ->
                return Result.failure(IllegalArgumentException("Private key must be a 32-byte WireGuard key."))
            address.isBlank() -> return Result.failure(IllegalArgumentException("Address is required, for example 10.0.0.2/32."))
            !isPlausibleKey(publicKey) ->
                return Result.failure(IllegalArgumentException("Peer public key must be a 32-byte WireGuard key."))
            allowed.isBlank() -> return Result.failure(IllegalArgumentException("Allowed IPs are required."))
            endpoint.isNotBlank() && !endpointLooksValid(endpoint) ->
                return Result.failure(IllegalArgumentException("Endpoint should look like host:51820."))
            endpoint.isNotBlank() && com.phonekey.vpn.domain.model.ProtocolPolicy.isDocumentationEndpoint(endpoint) ->
                return Result.failure(IllegalArgumentException(com.phonekey.vpn.domain.model.ProtocolPolicy.DOCUMENTATION_ENDPOINT))
            fields.presharedKey.isNotBlank() && !isPlausibleKey(fields.presharedKey) ->
                return Result.failure(IllegalArgumentException("Preshared key must be a 32-byte key, or left empty."))
        }
        val lines = buildList {
            add("[Interface]")
            add("PrivateKey = $privateKey")
            add("Address = $address")
            if (fields.dns.isNotBlank()) add("DNS = ${fields.dns.trim()}")
            if (fields.mtu.isNotBlank()) add("MTU = ${fields.mtu.trim()}")
            add("")
            add("[Peer]")
            add("PublicKey = $publicKey")
            if (fields.presharedKey.isNotBlank()) add("PresharedKey = ${fields.presharedKey.trim()}")
            add("AllowedIPs = $allowed")
            if (endpoint.isNotBlank()) add("Endpoint = $endpoint")
            if (fields.keepalive.isNotBlank()) add("PersistentKeepalive = ${fields.keepalive.trim()}")
        }
        val raw = lines.joinToString("\n") + "\n"
        return Result.success(
            VpnProfile(
                name = name.trim(),
                protocol = Protocol.WIREGUARD,
                rawConfig = raw,
                endpoint = endpoint.ifBlank { null },
                address = address,
            ),
        )
    }

    fun import(text: String, suggestedName: String? = null): Result<VpnProfile> {
        val raw = text.trim().removePrefix("\uFEFF")
        if (raw.isEmpty()) return Result.failure(IllegalArgumentException("That file was empty."))
        if (raw.toByteArray().size > MAX_BYTES) {
            return Result.failure(IllegalArgumentException("Config is larger than 1 MB."))
        }
        if (raw.startsWith("{")) {
            return Result.failure(
                IllegalArgumentException("Xray and V2Ray are not implemented. PhoneKey only imports WireGuard configs."),
            )
        }
        if (raw.contains("[Interface]", ignoreCase = true)) return importWireGuard(raw, suggestedName)
        if (isSstp(raw)) {
            return Result.failure(
                IllegalArgumentException("SSTP is not implemented. PhoneKey only imports WireGuard configs."),
            )
        }
        return Result.failure(
            IllegalArgumentException("Unrecognized config. Paste a WireGuard .conf."),
        )
    }

    fun endpointHost(configText: String): String? {
        val sections = parseSections(configText)
        val peer = sections.firstOrNull { it.name.equals("Peer", ignoreCase = true) } ?: return null
        return peer.value("Endpoint")?.trim()?.ifBlank { null }
    }

    /**
     * Writes AllowedApplications (include-only) or ExcludedApplications (bypass)
     * and removes the opposite key so the two lists cannot both apply.
     */
    fun withApplicationFilter(configText: String, packages: List<String>, includeOnly: Boolean): String {
        val cleaned = packages.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val keep = if (includeOnly) "AllowedApplications" else "ExcludedApplications"
        val drop = if (includeOnly) "ExcludedApplications" else "AllowedApplications"
        var text = Regex("(?im)^$drop\\s*=.*(?:\\R)?").replace(configText, "")
        if (cleaned.isEmpty()) {
            return Regex("(?im)^$keep\\s*=.*(?:\\R)?").replace(text, "")
        }
        val line = "$keep = ${cleaned.joinToString(", ")}"
        val pattern = Regex("(?im)^$keep\\s*=.*$")
        if (pattern.containsMatchIn(text)) return pattern.replace(text, line)
        val interfaceHeader = Regex("(?im)^\\[Interface]\\s*$")
        if (!interfaceHeader.containsMatchIn(text)) return text
        return interfaceHeader.replace(text) { "${it.value}\n$line" }
    }

    private fun importWireGuard(raw: String, suggestedName: String?): Result<VpnProfile> {
        val sections = parseSections(raw)
        val iface = sections.firstOrNull { it.name.equals("Interface", ignoreCase = true) }
            ?: return Result.failure(IllegalArgumentException("WireGuard config is missing [Interface]."))
        val peer = sections.firstOrNull { it.name.equals("Peer", ignoreCase = true) }
            ?: return Result.failure(IllegalArgumentException("WireGuard config is missing [Peer]."))
        val privateKey = iface.value("PrivateKey").orEmpty()
        val address = iface.value("Address").orEmpty()
        val publicKey = peer.value("PublicKey").orEmpty()
        val allowed = peer.value("AllowedIPs").orEmpty()
        val endpoint = peer.value("Endpoint")
        when {
            !isPlausibleKey(privateKey) ->
                return Result.failure(IllegalArgumentException("Interface private key is missing or not a WireGuard key."))
            address.isBlank() ->
                return Result.failure(IllegalArgumentException("Interface address is missing."))
            !isPlausibleKey(publicKey) ->
                return Result.failure(IllegalArgumentException("Peer public key is missing or not a WireGuard key."))
            allowed.isBlank() ->
                return Result.failure(IllegalArgumentException("Peer AllowedIPs is missing."))
            endpoint != null && com.phonekey.vpn.domain.model.ProtocolPolicy.isDocumentationEndpoint(endpoint) ->
                return Result.failure(IllegalArgumentException(com.phonekey.vpn.domain.model.ProtocolPolicy.DOCUMENTATION_ENDPOINT))
        }
        val host = endpoint?.substringBefore(':')?.ifBlank { null }
        val name = suggestedName?.trim().takeUnless { it.isNullOrEmpty() }
            ?: host
            ?: "WireGuard"
        return Result.success(
            VpnProfile(
                name = name,
                protocol = Protocol.WIREGUARD,
                rawConfig = raw.trim() + "\n",
                endpoint = endpoint,
                address = address,
            ),
        )
    }

    private fun isSstp(raw: String): Boolean {
        val lower = raw.lowercase()
        return "sstp://" in lower || "<sstp" in lower || "protocol = sstp" in lower || "protocol=sstp" in lower
    }

    private fun endpointLooksValid(endpoint: String): Boolean {
        val trimmed = endpoint.trim()
        val host: String
        val port: String
        if (trimmed.startsWith("[")) {
            val end = trimmed.indexOf(']')
            if (end < 1 || !trimmed.substring(end).startsWith("]:")) return false
            host = trimmed.substring(1, end)
            port = trimmed.substring(end + 2)
        } else {
            host = trimmed.substringBeforeLast(':', "")
            port = trimmed.substringAfterLast(':', "")
        }
        val portNumber = port.toIntOrNull() ?: return false
        return host.isNotBlank() && portNumber in 1..65535
    }

    private data class Section(val name: String, val entries: List<Pair<String, String>>) {
        fun value(key: String): String? =
            entries.firstOrNull { it.first.equals(key, ignoreCase = true) }?.second
    }

    private fun parseSections(text: String): List<Section> {
        val sections = mutableListOf<Section>()
        var currentName: String? = null
        val entries = mutableListOf<Pair<String, String>>()
        fun flush() {
            val name = currentName ?: return
            sections += Section(name, entries.toList())
            entries.clear()
        }
        text.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith(";")) return@forEach
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                flush()
                currentName = trimmed.substring(1, trimmed.length - 1).trim()
                return@forEach
            }
            val eq = trimmed.indexOf('=')
            if (eq <= 0 || currentName == null) return@forEach
            entries += trimmed.substring(0, eq).trim() to trimmed.substring(eq + 1).trim()
        }
        flush()
        return sections
    }
}
