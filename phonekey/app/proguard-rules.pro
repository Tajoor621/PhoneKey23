# Release minify is off. These rules are here if you turn it on.
-keep class com.wireguard.** { *; }
-keep class com.phonekey.vpn.service.PhoneKeyVpnService { *; }
-keep class com.wireguard.crypto.** { *; }
-dontwarn com.wireguard.**
