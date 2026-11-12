package com.phonekey.vpn

import android.content.Context
import com.phonekey.vpn.data.store.ProfileCipher
import com.phonekey.vpn.data.store.ProfileRepository
import com.phonekey.vpn.data.store.SettingsRepository
import com.phonekey.vpn.data.vpn.AndroidTunnelRuntime
import com.phonekey.vpn.data.vpn.EndpointProbe
import com.phonekey.vpn.data.vpn.EngineFactory
import com.phonekey.vpn.data.vpn.UnderlyingNetworkWatcher
import com.phonekey.vpn.data.vpn.VpnRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settingsRepository = SettingsRepository(appContext)
    val profileRepository = ProfileRepository(
        file = File(appContext.filesDir, "profiles.json"),
        scope = appScope,
        sealer = ProfileCipher(),
    )
    lateinit var vpnRepository: VpnRepository
        private set

    init {
        val holder = arrayOfNulls<VpnRepository>(1)
        val engines = EngineFactory(appContext) { profileId ->
            holder[0]?.onTunnelDown(profileId)
        }
        val repo = VpnRepository(
            scope = appScope,
            profiles = profileRepository,
            settings = settingsRepository,
            runtime = AndroidTunnelRuntime(appContext, engines),
            probe = EndpointProbe(),
        )
        holder[0] = repo
        vpnRepository = repo
        UnderlyingNetworkWatcher(appContext, repo).start()
    }
}
