package com.phonekey.vpn

import com.phonekey.vpn.data.store.ProfileDocuments
import com.phonekey.vpn.data.store.ProfileRepository
import com.phonekey.vpn.data.store.ProfileSealFormat
import com.phonekey.vpn.data.store.ProfileSealer
import com.phonekey.vpn.domain.model.Protocol
import com.phonekey.vpn.domain.model.VpnProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProfileRepositoryTest {
    @Test
    fun corruptEntryIsReportedAndPreserved() = runBlocking {
        val dir = java.nio.file.Files.createTempDirectory("phonekey-profiles").toFile()
        val file = File(dir, "profiles.json")
        val sealer = FakeSealer()
        val good = VpnProfile(
            id = "ok",
            name = "Good",
            protocol = Protocol.WIREGUARD,
            rawConfig = SAMPLE,
            endpoint = "wg.internal.lan:51820",
            address = "10.0.0.2/32",
        )
        val sealed = ProfileDocuments.serialize(listOf(good), emptyList(), sealer).trim()
        val corrupt = """{"id":"bad","name":"Broken","protocol":"WIREGUARD","rawConfig":"pk1:CORRUPT","createdAt":1,"updatedAt":1}"""
        val body = sealed.removeSuffix("]") + "," + corrupt + "]"
        file.writeText(body)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val repo = ProfileRepository(file, scope, sealer)
        repo.awaitLoaded()
        assertEquals(1, repo.profiles.value.size)
        assertEquals("Good", repo.profiles.value[0].name)
        assertTrue(repo.profiles.value[0].rawConfig.contains("PrivateKey"))
        assertEquals(1, repo.broken.value.size)
        assertEquals("bad", repo.broken.value[0].id)
        assertEquals("Broken", repo.broken.value[0].name)
        assertTrue(repo.error.value?.contains("decrypted") == true)
        repo.upsert(repo.profiles.value[0].copy(name = "Renamed"))
        val saved = file.readText()
        assertTrue(saved.contains("pk1:CORRUPT"))
        assertTrue(saved.contains("Renamed"))
        val backups = dir.listFiles { candidate -> candidate.name.startsWith("profiles.json.bak-") }.orEmpty()
        assertEquals(1, backups.size)
        assertTrue(backups[0].readText().contains("pk1:CORRUPT"))
    }

    @Test
    fun unreadableFileIsNotOverwritten() = runBlocking {
        val dir = java.nio.file.Files.createTempDirectory("phonekey-profiles-bad").toFile()
        val file = File(dir, "profiles.json")
        file.writeText("not json at all")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val repo = ProfileRepository(file, scope, FakeSealer())
        repo.awaitLoaded()
        assertTrue(repo.profiles.value.isEmpty())
        assertTrue(repo.error.value?.contains("not overwritten") == true)
        repo.upsert(
            VpnProfile(name = "New", protocol = Protocol.WIREGUARD, rawConfig = SAMPLE),
        )
        assertEquals("not json at all", file.readText())
        assertTrue(repo.error.value?.contains("not overwritten") == true)
    }
}

private class FakeSealer : ProfileSealer {
    override fun seal(plain: String): String {
        if (plain.startsWith(ProfileSealFormat.PREFIX)) return plain
        return ProfileSealFormat.PREFIX + Base64.getEncoder().encodeToString(plain.toByteArray())
    }

    override fun open(stored: String): String {
        if (!stored.startsWith(ProfileSealFormat.PREFIX)) return stored
        val body = stored.removePrefix(ProfileSealFormat.PREFIX)
        if (body == "CORRUPT") throw IllegalArgumentException("bad")
        return String(Base64.getDecoder().decode(body))
    }
}

private const val SAMPLE = """
[Interface]
PrivateKey = yAnz5TF+lXXJte14tji3zlMNq+hd2rYUIgJBgB3fBmk=
Address = 10.0.0.2/32

[Peer]
PublicKey = HIgo9xNzJMWLKASShiTqIybxZ0U3wGLiUeJ1PKf8ykw=
AllowedIPs = 0.0.0.0/0
Endpoint = wg.internal.lan:51820
"""

