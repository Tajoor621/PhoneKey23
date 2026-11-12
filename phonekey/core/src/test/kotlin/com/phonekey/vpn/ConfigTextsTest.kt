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
import kotlin.test.assertNotEquals
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
        assertTrue(!ProtocolPolicy.isDocumentationEndpoint("localhost:51820"))
        assertTrue(!ProtocolPolicy.isDocumentationEndpoint("127.0.0.1:51820"))
    }

    @Test
    fun localhostIsARealEndpoint() {
        val local = valid.replace("wg.internal.lan:51820", "localhost:51820")
        val profile = ConfigTexts.import(local, "Local").getOrThrow()
        assertEquals("localhost:51820", profile.endpoint)
        assertNull(ProtocolPolicy.connectBlockReason(profile))
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
        assertTrue(included.contains("IncludedApplications = com.example.b"))
        assertTrue(!included.contains("ExcludedApplications"))
        assertTrue(!included.contains("AllowedApplications"))
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
        val report = BundleCodec.import(exported).getOrThrow()
        assertEquals(1, report.imported)
        assertEquals(1, report.profiles.size)
        assertNotEquals(usable.id, report.profiles[0].id)
        assertEquals("Office", report.profiles[0].name)
        val again = BundleCodec.import(
            exported,
            existingPublicKeys = setOf(ConfigTexts.peerPublicKey(usable.rawConfig)!!),
        ).getOrThrow()
        assertEquals(0, again.imported)
        assertEquals(1, again.skipped)
        assertTrue(again.profiles.isEmpty())
        assertFailsWith<IllegalArgumentException> {
            BundleCodec.import("{\"nope\":true}").getOrThrow()
        }
    }

    @Test
    fun unknownFieldsCorruptEntriesAndSizeLimit() {
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
        val root = com.google.gson.JsonParser.parseString(BundleCodec.export(listOf(usable))).asJsonObject
        val first = root.getAsJsonArray("profiles")[0].asJsonObject
        first.addProperty("notes", "ignore me")
        val withUnknown = BundleCodec.import(root.toString()).getOrThrow()
        assertEquals(1, withUnknown.imported)
        assertEquals("Office", withUnknown.profiles[0].name)

        val both = com.google.gson.JsonObject()
        both.addProperty("phonekey", 1)
        val rows = com.google.gson.JsonArray()
        rows.add(root.getAsJsonArray("profiles")[0])
        val corrupt = root.getAsJsonArray("profiles")[0].deepCopy()
        corrupt.asJsonObject.addProperty("rawConfig", "this is not wireguard")
        corrupt.asJsonObject.addProperty("name", "Broken row")
        rows.add(corrupt)
        both.add("profiles", rows)
        val mixed = BundleCodec.import(both.toString()).getOrThrow()
        assertEquals(1, mixed.imported)
        assertEquals(1, mixed.failed)

        val huge = "x".repeat(ConfigTexts.MAX_BYTES + 1)
        assertTrue(BundleCodec.import(huge).isFailure)
        val duplicateKey = BundleCodec.import(BundleCodec.export(listOf(usable, usable))).getOrThrow()
        assertEquals(1, duplicateKey.imported)
        assertEquals(1, duplicateKey.skipped)
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
