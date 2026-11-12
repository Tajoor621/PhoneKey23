package com.phonekey.vpn.data.parser

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.phonekey.vpn.domain.model.VpnProfile
import java.util.UUID

data class BundleImportReport(
    val profiles: List<VpnProfile>,
    val imported: Int,
    val skipped: Int,
    val failed: Int,
)

object BundleCodec {
    private val gson = Gson()

    fun export(profiles: List<VpnProfile>): String {
        val root = com.google.gson.JsonObject()
        root.addProperty("phonekey", 1)
        root.add("profiles", gson.toJsonTree(profiles))
        return gson.toJson(root)
    }

    /**
     * Assigns a new id to every accepted profile. A profile whose WireGuard
     * public key is already in [existingPublicKeys], or already seen in this
     * file, is skipped instead of replacing the saved one.
     */
    fun import(text: String, existingPublicKeys: Set<String> = emptySet()): Result<BundleImportReport> {
        if (text.toByteArray().size > ConfigTexts.MAX_BYTES) {
            return Result.failure(IllegalArgumentException("Config is larger than 1 MB."))
        }
        val root = try {
            JsonParser.parseString(text).asJsonObject
        } catch (_: Exception) {
            return Result.failure(IllegalArgumentException("That file is not a PhoneKey profile export."))
        }
        if (root.get("phonekey")?.asInt != 1 || !root.has("profiles")) {
            return Result.failure(IllegalArgumentException("That file is not a PhoneKey profile export."))
        }
        val array = try {
            root.getAsJsonArray("profiles")
        } catch (_: Exception) {
            return Result.failure(IllegalArgumentException("The export has no profiles."))
        } ?: return Result.failure(IllegalArgumentException("The export has no profiles."))
        val imported = mutableListOf<VpnProfile>()
        val seen = existingPublicKeys.map { it.trim() }.filter { it.isNotEmpty() }.toMutableSet()
        var skipped = 0
        var failed = 0
        array.forEach { element ->
            if (!element.isJsonObject) {
                failed += 1
                return@forEach
            }
            val obj = element.asJsonObject
            val raw = obj.get("rawConfig")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
            val suggested = obj.get("name")?.takeIf { !it.isJsonNull }?.asString
            val parsed = ConfigTexts.import(raw, suggested)
            parsed.onSuccess { profile ->
                val key = ConfigTexts.peerPublicKey(profile.rawConfig)
                if (key != null && key in seen) {
                    skipped += 1
                } else {
                    if (key != null) seen += key
                    imported += profile.copy(
                        id = UUID.randomUUID().toString(),
                        name = suggested?.ifBlank { profile.name } ?: profile.name,
                    )
                }
            }
            parsed.onFailure { failed += 1 }
        }
        if (imported.isEmpty() && skipped == 0) {
            return Result.failure(IllegalArgumentException("Nothing in that file could be imported."))
        }
        return Result.success(
            BundleImportReport(
                profiles = imported,
                imported = imported.size,
                skipped = skipped,
                failed = failed,
            ),
        )
    }
}
