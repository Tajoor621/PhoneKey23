package com.phonekey.vpn.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.phonekey.vpn.PhoneKeyApp
import com.phonekey.vpn.data.parser.BundleCodec
import com.phonekey.vpn.data.parser.ConfigTexts
import com.phonekey.vpn.data.parser.WireGuardFields
import com.phonekey.vpn.domain.model.AppSettings
import com.phonekey.vpn.domain.model.ProtocolPolicy
import com.phonekey.vpn.domain.model.VpnProfile
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PhoneKeyViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as PhoneKeyApp).container
    private val bannerFlow = MutableStateFlow<String?>(null)
    private val savedFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val session = container.vpnRepository.session
    val profiles = container.profileRepository.profiles
    val settings = container.settingsRepository.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AppSettings(),
    )
    val banner = bannerFlow
    val saved = savedFlow.asSharedFlow()

    val selected = combine(profiles, settings) { list, prefs ->
        list.firstOrNull { it.id == prefs.selectedProfileId }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun dismissBanner() {
        bannerFlow.value = null
    }

    fun note(message: String) {
        bannerFlow.value = message
    }

    fun connectSelected() = container.vpnRepository.connectSelected()

    fun disconnect() = container.vpnRepository.disconnect()

    fun select(id: String) {
        viewModelScope.launch {
            val profile = container.profileRepository.find(id) ?: return@launch
            val blocked = ProtocolPolicy.connectBlockReason(profile)
            if (blocked != null) {
                bannerFlow.value = blocked
                return@launch
            }
            container.settingsRepository.update { it.copy(selectedProfileId = id) }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            container.profileRepository.delete(id)
            container.settingsRepository.update { prefs ->
                if (prefs.selectedProfileId == id) prefs.copy(selectedProfileId = null) else prefs
            }
        }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { container.settingsRepository.update(transform) }
    }

    fun importText(text: String, suggestedName: String? = null) {
        viewModelScope.launch {
            val trimmed = text.trim().removePrefix("\uFEFF")
            if (trimmed.startsWith("{") && "\"phonekey\"" in trimmed) {
                importBundle(trimmed)
                return@launch
            }
            val result = ConfigTexts.import(trimmed, suggestedName)
            result.onSuccess { saveNew(it) }
            result.onFailure { bannerFlow.value = it.message }
        }
    }

    fun exportBundle(): String = BundleCodec.export(container.profileRepository.profiles.value)

    private suspend fun importBundle(text: String) {
        val result = BundleCodec.import(text)
        result.onFailure {
            bannerFlow.value = it.message
            return
        }
        val imported = result.getOrThrow()
        imported.forEach { container.profileRepository.upsert(it) }
        val selectable = imported.firstOrNull { ProtocolPolicy.connectBlockReason(it) == null }
        if (selectable != null) {
            container.settingsRepository.update { it.copy(selectedProfileId = selectable.id) }
        }
        bannerFlow.value = "Imported ${imported.size} profile${if (imported.size == 1) "" else "s"}."
    }

    fun importUri(uri: Uri) {
        viewModelScope.launch {
            val text = try {
                val resolver = getApplication<Application>().contentResolver
                resolver.openInputStream(uri)?.use { input ->
                    val bytes = input.readBytes()
                    if (bytes.size > ConfigTexts.MAX_BYTES) {
                        bannerFlow.value = "That file is larger than 1 MB."
                        return@launch
                    }
                    bytes.toString(Charsets.UTF_8)
                }
            } catch (error: Exception) {
                bannerFlow.value = error.message ?: "Could not read that file."
                null
            } ?: return@launch
            val display = uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.')
            importText(text, display)
        }
    }

    fun saveManual(name: String, fields: WireGuardFields) {
        viewModelScope.launch {
            val result = ConfigTexts.generateWireGuard(name, fields)
            result.onSuccess { saveNew(it) }
            result.onFailure { bannerFlow.value = it.message }
        }
    }

    fun saveRaw(id: String?, name: String, raw: String) {
        viewModelScope.launch {
            val imported = ConfigTexts.import(raw, name.ifBlank { null })
            imported.onFailure {
                bannerFlow.value = it.message
                return@launch
            }
            val parsed = imported.getOrThrow()
            if (id == null) {
                saveNew(parsed.copy(name = name.ifBlank { parsed.name }))
            } else {
                val existing = container.profileRepository.find(id) ?: parsed
                container.profileRepository.upsert(
                    parsed.copy(
                        id = id,
                        name = name.ifBlank { parsed.name },
                        createdAt = existing.createdAt,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
                container.settingsRepository.update { it.copy(selectedProfileId = id) }
                bannerFlow.value = "Profile saved."
                savedFlow.tryEmit(Unit)
            }
        }
    }

    private suspend fun saveNew(profile: VpnProfile) {
        container.profileRepository.upsert(profile)
        container.settingsRepository.update { it.copy(selectedProfileId = profile.id) }
        bannerFlow.value = "Saved ${profile.name}."
        savedFlow.tryEmit(Unit)
    }
}
