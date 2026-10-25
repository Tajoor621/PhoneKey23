package com.phonekey.vpn.data.store

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.phonekey.vpn.domain.model.VpnProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class ProfileRepository(context: Context) {
    private val gson = Gson()
    private val file = File(context.applicationContext.filesDir, "profiles.json")
    private val gate = Mutex()
    private val _profiles = MutableStateFlow<List<VpnProfile>>(emptyList())
    val profiles: StateFlow<List<VpnProfile>> = _profiles.asStateFlow()

    init {
        _profiles.value = readQuietly()
    }

    suspend fun upsert(profile: VpnProfile) = mutate { list ->
        val index = list.indexOfFirst { it.id == profile.id }
        if (index >= 0) list[index] = profile else list.add(0, profile)
    }

    suspend fun delete(id: String) = mutate { list ->
        list.removeAll { it.id == id }
    }

    suspend fun find(id: String): VpnProfile? = gate.withLock {
        _profiles.value.firstOrNull { it.id == id }
    }

    private suspend fun mutate(block: (MutableList<VpnProfile>) -> Unit) {
        gate.withLock {
            val next = _profiles.value.toMutableList()
            block(next)
            _profiles.value = next.toList()
            write(next)
        }
    }

    private fun readQuietly(): List<VpnProfile> = try {
        if (!file.exists()) emptyList() else {
            val type = object : TypeToken<List<VpnProfile>>() {}.type
            gson.fromJson<List<VpnProfile>>(file.readText(), type) ?: emptyList()
        }
    } catch (_: Exception) {
        emptyList()
    }

    private suspend fun write(list: List<VpnProfile>) = withContext(Dispatchers.IO) {
        val tmp = File(file.parentFile, "profiles.json.tmp")
        tmp.writeText(gson.toJson(list))
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
    }
}
