package com.phonekey.vpn.domain.model

enum class EngineStatus {
    READY,
    NOT_IMPLEMENTED,
}

/**
 * What PhoneKey will actually connect. Xray and SSTP stay in the model so an
 * old profile can be recognized and refused, but they are not selectable.
 */
object ProtocolPolicy {
    const val UNIMPLEMENTED =
        "Xray and SSTP are not implemented. Only a WireGuard profile can be selected."

    const val DOCUMENTATION_ENDPOINT =
        "This endpoint is a documentation address, not a server. PhoneKey will not save or connect it. Use your own WireGuard endpoint."

    fun implemented(protocol: Protocol): Boolean = protocol == Protocol.WIREGUARD

    fun engineStatus(protocol: Protocol): EngineStatus =
        if (implemented(protocol)) EngineStatus.READY else EngineStatus.NOT_IMPLEMENTED

    fun connectBlockReason(profile: VpnProfile): String? {
        if (!implemented(profile.protocol)) return UNIMPLEMENTED
        if (isDocumentationEndpoint(profile.endpoint)) return DOCUMENTATION_ENDPOINT
        return null
    }

    fun isDocumentationEndpoint(endpoint: String?): Boolean {
        if (endpoint.isNullOrBlank()) return false
        val host = hostOf(endpoint).lowercase()
        // localhost is a real test server, not an RFC 2606 documentation name.
        if (host == "example" ||
            host.endsWith(".example") || host.endsWith(".invalid") || host.endsWith(".test") ||
            host == "example.com" || host.endsWith(".example.com") ||
            host == "example.net" || host.endsWith(".example.net") ||
            host == "example.org" || host.endsWith(".example.org")
        ) {
            return true
        }
        val parts = host.split('.')
        if (parts.size != 4 || parts.any { it.toIntOrNull() !in 0..255 }) return false
        val a = parts.map { it.toInt() }
        return (a[0] == 192 && a[1] == 0 && a[2] == 2) ||
            (a[0] == 198 && a[1] == 51 && a[2] == 100) ||
            (a[0] == 203 && a[1] == 0 && a[2] == 113)
    }

    /**
     * Android 17 blocks the app's own sockets to these hosts unless
     * ACCESS_LOCAL_NETWORK was granted. A public address does not need it.
     */
    fun needsLocalNetwork(endpoint: String?): Boolean {
        if (endpoint.isNullOrBlank()) return false
        val host = hostOf(endpoint).lowercase().trim().removeSuffix(".").substringBefore("%")
        if (host.isEmpty()) return false
        if (host == "localhost" || host == "localhost.localdomain" || host == "::1") return true
        if (host.startsWith("::ffff:")) return ipv4Local(host.substringAfter("::ffff:"))
        if (host.contains(":")) return ipv6Local(host)
        if (host.endsWith(".local") || host.endsWith(".lan") || host.endsWith(".home") ||
            host.endsWith(".internal") || host.endsWith(".localdomain") || !host.contains('.')
        ) {
            return true
        }
        return ipv4Local(host)
    }

    private fun ipv4Local(host: String): Boolean {
        val parts = host.split('.')
        if (parts.size != 4 || parts.any { it.toIntOrNull() !in 0..255 }) return false
        val a = parts.map { it.toInt() }
        return a[0] == 10 ||
            a[0] == 127 ||
            (a[0] == 169 && a[1] == 254) ||
            (a[0] == 192 && a[1] == 168) ||
            (a[0] == 172 && a[1] in 16..31)
    }

    /** Unique-local (fc00::/7) and link-local (fe80::/10). */
    private fun ipv6Local(host: String): Boolean {
        val bare = host.removePrefix("[").removeSuffix("]")
        if (bare == "::1") return true
        if (bare.startsWith("::")) return false
        val first = bare.substringBefore(":").substringBefore("%").toIntOrNull(16) ?: return false
        val ula = first and 0xfe00 == 0xfc00
        val linkLocal = first and 0xffc0 == 0xfe80
        return ula || linkLocal
    }

    private fun hostOf(endpoint: String): String {
        val trimmed = endpoint.trim()
        if (trimmed.startsWith("[")) {
            val end = trimmed.indexOf(']')
            if (end > 1) return trimmed.substring(1, end)
        }
        val colon = trimmed.lastIndexOf(':')
        return if (colon > 0) trimmed.substring(0, colon) else trimmed
    }
}
