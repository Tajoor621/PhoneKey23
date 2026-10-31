package com.phonekey.vpn.data.parser

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.phonekey.vpn.domain.model.VpnProfile

object BundleCodec {
    private val gson = Gson()

    fun export(profiles: List<VpnProfile>): String {
        val root = com.google.gson.JsonObject()
        root.addProperty("phonekey", 1)
        root.add("profiles", gson.toJsonTree(profiles))
        return gson.toJson(root)
    }

    fun import(text: String): Result<List<VpnProfile>> {
        val root = try {
            JsonParser.parseString(text).asJsonObject
        } catch (_: Exception) {
            return Result.failure(IllegalArgumentException("That file is not a PhoneKey profile export."))
        }
        if (root.get("phonekey")?.asInt != 1 || !root.has("profiles")) {
            return Result.failure(IllegalArgumentException("That file is not a PhoneKey profile export."))
        }
        val array = root.getAsJsonArray("profiles")
            ?: return Result.failure(IllegalArgumentException("The export has no profiles."))
        val imported = mutableListOf<VpnProfile>()
        val problems = mutableListOf<String>()
        array.forEach { element ->
            val obj = element.asJsonObject
            val raw = obj.get("rawConfig")?.asString.orEmpty()
            val suggested = obj.get("name")?.asString
            val parsed = ConfigTexts.import(raw, suggested)
            parsed.onSuccess { profile ->
                val id = obj.get("id")?.asString?.takeIf { it.isNotBlank() } ?: profile.id
                imported += profile.copy(id = id, name = suggested?.ifBlank { profile.name } ?: profile.name)
            }
            parsed.onFailure { problems += (it.message ?: "Skipped one profile.") }
        }
        if (imported.isEmpty()) {
            val why = problems.firstOrNull() ?: "Nothing in that file could be imported."
            return Result.failure(IllegalArgumentException(why))
        }
        return Result.success(imported)
    }
}
