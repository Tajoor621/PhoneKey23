package com.phonekey.vpn.data.store

/**
 * Seals profile config text. The Android implementation uses Keystore AES-GCM.
 * Tests supply a fake. Implementations must not log plaintext keys.
 */
interface ProfileSealer {
    fun seal(plain: String): String
    fun open(stored: String): String
}

object ProfileSealFormat {
    const val PREFIX = "pk1:"
}
