package com.phonekey.vpn.data.store

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

data class LaunchableApp(
    val packageName: String,
    val label: String,
)

object InstalledApps {
    /** Launchable apps only. The manifest `<queries>` entry covers this without QUERY_ALL_PACKAGES. */
    fun list(context: Context): List<LaunchableApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }
        return resolved.mapNotNull { info ->
            val packageName = info.activityInfo?.packageName ?: return@mapNotNull null
            if (packageName == context.packageName) return@mapNotNull null
            LaunchableApp(
                packageName = packageName,
                label = info.loadLabel(pm)?.toString().orEmpty().ifBlank { packageName },
            )
        }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
    }
}
