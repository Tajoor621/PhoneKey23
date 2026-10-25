package com.phonekey.vpn

import android.content.Context
import com.phonekey.vpn.data.store.ProfileRepository
import com.phonekey.vpn.data.store.SettingsRepository
import com.phonekey.vpn.data.vpn.VpnRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settingsRepository = SettingsRepository(appContext)
    val profileRepository = ProfileRepository(appContext)
    val vpnRepository = VpnRepository(
        context = appContext,
        appScope = appScope,
        profiles = profileRepository,
        settings = settingsRepository,
    )
}
