package com.phonekey.vpn.domain.model

/**
 * Plain mapping for DataStore so persistence can be tested without Android.
 */
object SettingsCodec {
    const val BOOT = "auto_connect_boot"
    const val LAUNCH = "auto_connect_launch"
    const val KILL = "kill_switch"
    const val SPLIT = "split_enabled"
    const val INCLUDE = "split_include_only"
    const val PACKAGES = "split_packages"
    const val SELECTED = "selected_profile"

    fun decode(
        boot: Boolean?,
        launch: Boolean?,
        kill: Boolean?,
        split: Boolean?,
        includeOnly: Boolean?,
        packages: Set<String>?,
        selected: String?,
    ): AppSettings = AppSettings(
        autoConnectOnBoot = boot ?: false,
        autoConnectOnLaunch = launch ?: false,
        killSwitch = kill ?: false,
        splitTunnelEnabled = split ?: false,
        splitIncludeOnly = includeOnly ?: false,
        splitTunnelPackages = (packages ?: emptySet()).map { it.trim() }.filter { it.isNotEmpty() }.distinct().sorted(),
        selectedProfileId = selected?.takeIf { it.isNotBlank() },
    )

    fun packageSet(settings: AppSettings): Set<String> =
        settings.splitTunnelPackages.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
}
