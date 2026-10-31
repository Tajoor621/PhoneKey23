package com.phonekey.vpn

import com.phonekey.vpn.data.parser.BundleCodec
import com.phonekey.vpn.data.parser.ConfigTexts
import com.phonekey.vpn.data.parser.WireGuardFields
import com.phonekey.vpn.domain.model.EngineStatus
import com.phonekey.vpn.domain.model.Protocol
import com.phonekey.vpn.domain.model.ProtocolPolicy
import com.phonekey.vpn.domain.model.SettingsCodec
import com.phonekey.vpn.domain.model.VpnProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConfigTextsTest {
    private val valid = """
        [Interface]
        PrivateKey = yAnz5TF+lXXJte14tji3zlMNq+hd2rYUIgJBgB3fBmk=
        Address = 10.0.0.2/32
        DNS = 1.1.1.1

        [Peer]
        PublicKey = HIgo9xNzJMWLKASShiTqIybxZ0U3wGLiUeJ1PKf8ykw=
        AllowedIPs = 0.0.0.0/0
        Endpoint = wg.internal.lan:51820
    """.trimIndent()

    @Test
    fun parsesWireGuardConf() {
        val profile = ConfigTexts.import(valid, "Home").getOrThrow()
        assertEquals(Protocol.WIREGUARD, profile.protocol)
        assertEquals("Home", profile.name)
        assertEquals("wg.internal.lan:51820", profile.endpoint)
        assertEquals("10.0.0.2/32", profile.address)
        assertNull(ProtocolPolicy.connectBlockReason(profile))
    }

    @Test
    fun refusesDocumentationEndpoint() {
        val sample = """
            [Interface]
            PrivateKey = yAnz5TF+lXXJte14tji3zlMNq+hd2rYUIgJBgB3fBmk=
            Address = 10.0.0.2/32
            [Peer]
            PublicKey = HIgo9xNzJMWLKASShiTqIybxZ0U3wGLiUeJ1PKf8ykw=
            AllowedIPs = 0.0.0.0/0
            Endpoint = 203.0.113.1:51820
        """.trimIndent()
        val error = ConfigTexts.import(sample).exceptionOrNull()
        assertTrue(error?.message?.contains("documentation") == true)
        assertTrue(ProtocolPolicy.isDocumentationEndpoint("203.0.113.1:51820"))
        assertTrue(ProtocolPolicy.isDocumentationEndpoint("192.0.2.10:1"))
        assertTrue(ProtocolPolicy.isDocumentationEndpoint("vpn.example.net:51820"))
    }

    @Test
    fun refusesXrayJson() {
        val json = """{"outbounds":[{"protocol":"vless"}]}"""
        val error = ConfigTexts.import(json).exceptionOrNull()
        assertTrue(error?.message?.contains("not implemented") == true)
    }

    @Test
    fun refusesSstpText() {
        val error = ConfigTexts.import("sstp://vpn.example.net").exceptionOrNull()
        assertTrue(error?.message?.contains("not implemented") == true)
    }

    @Test
    fun formRejectsBadKeyAndDocsHost() {
        val bad = ConfigTexts.generateWireGuard(
            "n",
            WireGuardFields(privateKey = "nope", address = "10.0.0.2/32", publicKey = "nope"),
        )
        assertTrue(bad.isFailure)
        val docs = ConfigTexts.generateWireGuard(
            "n",
            WireGuardFields(
                privateKey = "yAnz5TF+lXXJte14tji3zlMNq+hd2rYUIgJBgB3fBmk=",
                address = "10.0.0.2/32",
                publicKey = "HIgo9xNzJMWLKASShiTqIybxZ0U3wGLiUeJ1PKf8ykw=",
                endpoint = "203.0.113.1:51820",
            ),
        )
        assertTrue(docs.isFailure)
    }

    @Test
    fun applicationFilterReplacesTheOtherList() {
        val excluded = ConfigTexts.withApplicationFilter(valid, listOf("com.example.a"), includeOnly = false)
        assertTrue(excluded.contains("ExcludedApplications = com.example.a"))
        val included = ConfigTexts.withApplicationFilter(excluded, listOf("com.example.b"), includeOnly = true)
        assertTrue(included.contains("AllowedApplications = com.example.b"))
        assertTrue(!included.contains("ExcludedApplications"))
    }
}

class EngineFactoryTest {
    @Test
    fun onlyWireGuardIsReady() {
        assertEquals(EngineStatus.READY, ProtocolPolicy.engineStatus(Protocol.WIREGUARD))
        assertEquals(EngineStatus.NOT_IMPLEMENTED, ProtocolPolicy.engineStatus(Protocol.XRAY))
        assertEquals(EngineStatus.NOT_IMPLEMENTED, ProtocolPolicy.engineStatus(Protocol.SSTP))
        val stale = VpnProfile(name = "old", protocol = Protocol.XRAY, rawConfig = "{}")
        assertEquals(ProtocolPolicy.UNIMPLEMENTED, ProtocolPolicy.connectBlockReason(stale))
    }
}

class BundleCodecTest {
    @Test
    fun roundTripsProfilesAndSkipsUnknown() {
        val usable = ConfigTexts.import(
            """
            [Interface]
            PrivateKey = yAnz5TF+lXXJte14tji3zlMNq+hd2rYUIgJBgB3fBmk=
            Address = 10.7.0.2/32
            [Peer]
            PublicKey = HIgo9xNzJMWLKASShiTqIybxZ0U3wGLiUeJ1PKf8ykw=
            AllowedIPs = 0.0.0.0/0
            Endpoint = wg.internal.lan:51820
            """.trimIndent(),
            "Office",
        ).getOrThrow()
        val exported = BundleCodec.export(listOf(usable))
        val imported = BundleCodec.import(exported).getOrThrow()
        assertEquals(1, imported.size)
        assertEquals(usable.id, imported[0].id)
        assertEquals("Office", imported[0].name)
        assertFailsWith<IllegalArgumentException> {
            BundleCodec.import("{\"nope\":true}").getOrThrow()
        }
    }
}

class SettingsCodecTest {
    @Test
    fun roundTripsSplitListAndSelection() {
        val original = com.phonekey.vpn.domain.model.AppSettings(
            autoConnectOnBoot = true,
            autoConnectOnLaunch = false,
            killSwitch = true,
            splitTunnelEnabled = true,
            splitIncludeOnly = true,
            splitTunnelPackages = listOf("com.example.b", " com.example.a ", ""),
            selectedProfileId = "abc",
        )
        val decoded = SettingsCodec.decode(
            boot = original.autoConnectOnBoot,
            launch = original.autoConnectOnLaunch,
            kill = original.killSwitch,
            split = original.splitTunnelEnabled,
            includeOnly = original.splitIncludeOnly,
            packages = SettingsCodec.packageSet(original),
            selected = original.selectedProfileId,
        )
        assertEquals(listOf("com.example.a", "com.example.b"), decoded.splitTunnelPackages)
        assertEquals("abc", decoded.selectedProfileId)
        assertEquals(true, decoded.splitIncludeOnly)
        val cleared = SettingsCodec.decode(null, null, null, null, null, null, "  ")
        assertEquals(null, cleared.selectedProfileId)
        assertEquals(false, cleared.killSwitch)
    }
}
