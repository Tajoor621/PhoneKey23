package com.phonekey.vpn.data.store

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.phonekey.vpn.domain.model.VpnProfile

data class BrokenProfile(
    val id: String,
    val name: String,
)

/**
 * Parses and writes profiles.json one entry at a time.
 * A sealed entry that cannot be opened is preserved verbatim and never logged.
 */
object ProfileDocuments {
    private val gson = Gson()

    data class Parsed(
        val profiles: List<VpnProfile>,
        val broken: List<BrokenProfile>,
        val preserved: List<JsonObject>,
        val unreadableFile: Boolean,
        val error: String?,
    )

    fun parse(json: String, sealer: ProfileSealer): Parsed {
        val root = try {
            JsonParser.parseString(json)
        } catch (_: Exception) {
            return unreadable()
        }
        if (!root.isJsonArray) return unreadable()
        val profiles = mutableListOf<VpnProfile>()
        val broken = mutableListOf<BrokenProfile>()
        val preserved = mutableListOf<JsonObject>()
        root.asJsonArray.forEach { element ->
            if (!element.isJsonObject) {
                broken += BrokenProfile(id = "unknown", name = "Unreadable profile")
                return@forEach
            }
            val obj = element.asJsonObject
            val id = stringField(obj, "id") ?: "unknown"
            val name = stringField(obj, "name") ?: "Unnamed profile"
            try {
                val stored = obj.get("rawConfig")?.asString
                    ?: throw IllegalArgumentException("missing config")
                val opened = sealer.open(stored)
                val profile = gson.fromJson(obj, VpnProfile::class.java).copy(rawConfig = opened)
                profiles += profile
            } catch (_: Exception) {
                broken += BrokenProfile(id = id, name = name)
                preserved += obj
            }
        }
        val error = if (broken.isNotEmpty()) {
            "Some profiles could not be decrypted. They were left in the file."
        } else {
            null
        }
        return Parsed(profiles, broken, preserved, unreadableFile = false, error = error)
    }

    fun serialize(
        profiles: List<VpnProfile>,
        preserved: List<JsonObject>,
        sealer: ProfileSealer,
    ): String {
        val array = JsonArray()
        profiles.forEach { profile ->
            val sealed = profile.copy(rawConfig = sealer.seal(profile.rawConfig))
            array.add(gson.toJsonTree(sealed))
        }
        preserved.forEach { array.add(it) }
        return gson.toJson(array)
    }

    private fun unreadable() = Parsed(
        profiles = emptyList(),
        broken = emptyList(),
        preserved = emptyList(),
        unreadableFile = true,
        error = "profiles.json could not be read. It was not overwritten.",
    )

    private fun stringField(obj: JsonObject, key: String): String? {
        val value = obj.get(key) ?: return null
        if (value.isJsonNull || !value.isJsonPrimitive) return null
        return value.asString.trim().ifBlank { null }
    }
}
