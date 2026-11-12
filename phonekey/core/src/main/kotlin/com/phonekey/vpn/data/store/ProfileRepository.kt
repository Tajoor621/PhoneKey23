package com.phonekey.vpn.data.store

import com.phonekey.vpn.domain.ProfileSource
import com.phonekey.vpn.domain.model.VpnProfile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Profile list sealed at rest. Load runs on [scope], not on the caller of the
 * constructor, so Application.onCreate does not decrypt on the main thread.
 *
 * A file that cannot be parsed is never overwritten. An entry that cannot be
 * decrypted is kept byte-for-byte, and the original file is copied to
 * profiles.json.bak-<timestamp> before the first later write.
 */
class ProfileRepository(
    private val file: File,
    private val scope: CoroutineScope,
    private val sealer: ProfileSealer,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ProfileSource {
    private val gate = Mutex()
    private val _profiles = MutableStateFlow<List<VpnProfile>>(emptyList())
    private val _broken = MutableStateFlow<List<BrokenProfile>>(emptyList())
    private val _loading = MutableStateFlow(true)
    private val _error = MutableStateFlow<String?>(null)
    val profiles: StateFlow<List<VpnProfile>> = _profiles.asStateFlow()
    val broken: StateFlow<List<BrokenProfile>> = _broken.asStateFlow()
    val loading: StateFlow<Boolean> = _loading.asStateFlow()
    val error: StateFlow<String?> = _error.asStateFlow()

    private var preserved: List<com.google.gson.JsonObject> = emptyList()
    private var unreadableFile = false
    private var backedUp = false
    private val loaded = CompletableDeferred<Unit>()

    init {
        scope.launch { reload() }
    }

    suspend fun awaitLoaded() {
        loaded.await()
    }

    override suspend fun find(id: String): VpnProfile? {
        awaitLoaded()
        return _profiles.value.firstOrNull { it.id == id }
    }

    suspend fun upsert(profile: VpnProfile) = mutate { list ->
        val index = list.indexOfFirst { it.id == profile.id }
        if (index >= 0) list[index] = profile else list.add(0, profile)
    }

    suspend fun delete(id: String) = mutate { list ->
        list.removeAll { it.id == id }
    }

    private suspend fun reload() {
        val parsed = withContext(io) {
            if (!file.exists()) {
                ProfileDocuments.Parsed(
                    profiles = emptyList(),
                    broken = emptyList(),
                    preserved = emptyList(),
                    unreadableFile = false,
                    error = null,
                )
            } else {
                ProfileDocuments.parse(file.readText(), sealer)
            }
        }
        gate.withLock {
            preserved = parsed.preserved
            unreadableFile = parsed.unreadableFile
            _profiles.value = parsed.profiles
            _broken.value = parsed.broken
            _error.value = parsed.error
            _loading.value = false
            val needsSeal = !unreadableFile && preserved.isEmpty() &&
                file.exists() && fileHasPlaintextConfig(file.readText())
            if (needsSeal) writeUnlocked(parsed.profiles)
            if (!loaded.isCompleted) loaded.complete(Unit)
        }
    }

    private fun fileHasPlaintextConfig(json: String): Boolean {
        return Regex("\"rawConfig\"\\s*:\\s*\"(?!${Regex.escape(ProfileSealFormat.PREFIX)})").containsMatchIn(json)
    }

    private suspend fun mutate(block: (MutableList<VpnProfile>) -> Unit) {
        awaitLoaded()
        gate.withLock {
            if (unreadableFile) {
                _error.value = "profiles.json could not be read, so it was not overwritten."
                return@withLock
            }
            val next = _profiles.value.toMutableList()
            block(next)
            _profiles.value = next.toList()
            writeUnlocked(next)
        }
    }

    private suspend fun writeUnlocked(list: List<VpnProfile>) {
        if (unreadableFile) return
        withContext(io) {
            if (preserved.isNotEmpty() && !backedUp && file.exists()) {
                val stamp = System.currentTimeMillis()
                val bak = File(file.parentFile, "profiles.json.bak-$stamp")
                file.copyTo(bak, overwrite = false)
                backedUp = true
            }
            val text = ProfileDocuments.serialize(list, preserved, sealer)
            val tmp = File(file.parentFile, "profiles.json.tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(file)) {
                file.writeText(tmp.readText())
                tmp.delete()
            }
        }
    }
}
