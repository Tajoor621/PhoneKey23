package com.phonekey.vpn.domain

import com.phonekey.vpn.domain.model.AppSettings
import com.phonekey.vpn.domain.model.VpnProfile

interface ProfileSource {
    suspend fun find(id: String): VpnProfile?
}

interface SettingsSource {
    suspend fun snapshot(): AppSettings
}
