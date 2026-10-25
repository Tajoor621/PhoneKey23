package com.phonekey.vpn.ui

import java.util.Locale

fun formatBytes(value: Long): String {
    val units = arrayOf("B", "KB", "MB", "GB")
    var amount = value.coerceAtLeast(0).toDouble()
    var index = 0
    while (amount >= 1024 && index < units.lastIndex) {
        amount /= 1024
        index++
    }
    return if (index == 0) {
        "${amount.toLong()} ${units[index]}"
    } else {
        String.format(Locale.US, "%.1f %s", amount, units[index])
    }
}

fun formatRate(bps: Double): String {
    val value = bps.coerceAtLeast(0.0)
    return when {
        value < 1024 -> String.format(Locale.US, "%.0f B/s", value)
        value < 1024 * 1024 -> String.format(Locale.US, "%.1f KB/s", value / 1024.0)
        else -> String.format(Locale.US, "%.1f MB/s", value / (1024.0 * 1024.0))
    }
}

fun formatDuration(sinceEpochMs: Long?, now: Long): String {
    if (sinceEpochMs == null) return "00:00"
    val seconds = ((now - sinceEpochMs) / 1000).coerceAtLeast(0)
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) {
        String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.US, "%02d:%02d", m, s)
    }
}
