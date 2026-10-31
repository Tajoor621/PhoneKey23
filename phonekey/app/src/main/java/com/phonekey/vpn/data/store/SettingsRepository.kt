package com.phonekey.vpn.data.store

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.phonekey.vpn.domain.model.AppSettings
import com.phonekey.vpn.domain.model.SettingsCodec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore(name = "phonekey_settings")

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.settingsStore

    val settings: Flow<AppSettings> = store.data.map { it.toSettings() }

    suspend fun snapshot(): AppSettings = settings.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { prefs ->
            prefs.write(transform(prefs.toSettings()))
        }
    }

    private fun Preferences.toSettings(): AppSettings = SettingsCodec.decode(
        boot = this[Keys.BOOT],
        launch = this[Keys.LAUNCH],
        kill = this[Keys.KILL],
        split = this[Keys.SPLIT],
        includeOnly = this[Keys.INCLUDE],
        packages = this[Keys.PACKAGES],
        selected = this[Keys.SELECTED],
    )

    private fun MutablePreferences.write(settings: AppSettings) {
        this[Keys.BOOT] = settings.autoConnectOnBoot
        this[Keys.LAUNCH] = settings.autoConnectOnLaunch
        this[Keys.KILL] = settings.killSwitch
        this[Keys.SPLIT] = settings.splitTunnelEnabled
        this[Keys.INCLUDE] = settings.splitIncludeOnly
        this[Keys.PACKAGES] = SettingsCodec.packageSet(settings)
        val selected = settings.selectedProfileId
        if (selected == null) remove(Keys.SELECTED) else this[Keys.SELECTED] = selected
    }

    private object Keys {
        val BOOT = booleanPreferencesKey("auto_connect_boot")
        val LAUNCH = booleanPreferencesKey("auto_connect_launch")
        val KILL = booleanPreferencesKey("kill_switch")
        val SPLIT = booleanPreferencesKey("split_enabled")
        val INCLUDE = booleanPreferencesKey("split_include_only")
        val PACKAGES = stringSetPreferencesKey("split_packages")
        val SELECTED = stringPreferencesKey("selected_profile")
    }
}
