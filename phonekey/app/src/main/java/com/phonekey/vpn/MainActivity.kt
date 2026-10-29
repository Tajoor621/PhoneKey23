package com.phonekey.vpn

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.phonekey.vpn.service.PhoneKeyVpnService
import com.phonekey.vpn.ui.PhoneKeyRoot

class MainActivity : ComponentActivity() {
    private var connectTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (intent?.action == PhoneKeyVpnService.ACTION_CONNECT) connectTick++
        setContent {
            PhoneKeyRoot(connectTick = connectTick)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == PhoneKeyVpnService.ACTION_CONNECT) connectTick++
    }
}
