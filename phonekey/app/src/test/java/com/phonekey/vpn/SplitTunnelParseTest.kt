package com.phonekey.vpn

import com.phonekey.vpn.data.parser.ConfigTexts
import com.wireguard.config.Config
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

/**
 * Round-trips through com.wireguard.config.Config from tunnel 1.0.20260102.
 * That parser accepts IncludedApplications and ExcludedApplications, and rejects
 * AllowedApplications as an unknown interface attribute.
 */
class SplitTunnelParseTest {
    private val valid = """
        [Interface]
        PrivateKey = yAnz5TF+lXXJte14tji3zlMNq+hd2rYUIgJBgB3fBmk=
        Address = 10.0.0.2/32

        [Peer]
        PublicKey = HIgo9xNzJMWLKASShiTqIybxZ0U3wGLiUeJ1PKf8ykw=
        AllowedIPs = 0.0.0.0/0
        Endpoint = 10.1.0.1:51820
    """.trimIndent()

    @Test
    fun includeOnlyRoundTrips() {
        val text = ConfigTexts.withApplicationFilter(valid, listOf("com.example.a", "com.example.b"), true)
        val config = Config.parse(ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)))
        assertEquals(setOf("com.example.a", "com.example.b"), config.`interface`.includedApplications)
        assertTrue(config.`interface`.excludedApplications.isEmpty())
    }

    @Test
    fun bypassRoundTrips() {
        val text = ConfigTexts.withApplicationFilter(valid, listOf("com.example.a"), false)
        val config = Config.parse(ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)))
        assertEquals(setOf("com.example.a"), config.`interface`.excludedApplications)
        assertTrue(config.`interface`.includedApplications.isEmpty())
    }
}
