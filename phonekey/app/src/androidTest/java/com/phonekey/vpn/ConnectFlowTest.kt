package com.phonekey.vpn

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.phonekey.vpn.domain.model.Session
import com.phonekey.vpn.domain.model.TunnelStatus
import com.phonekey.vpn.ui.home.HomeScreen
import com.phonekey.vpn.ui.theme.PhoneKeyTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs on a device or emulator (`connectedDebugAndroidTest`). Not part of the
 * headless unit-test job.
 */
@RunWith(AndroidJUnit4::class)
class ConnectFlowTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun homeShowsOffUntilAProfileIsChosen() {
        compose.setContent {
            PhoneKeyTheme {
                HomeScreen(
                    session = Session(status = TunnelStatus.DISCONNECTED),
                    selected = null,
                    onToggle = {},
                    onChooseProfile = {},
                )
            }
        }
        compose.onNodeWithText("Off").assertIsDisplayed()
        compose.onNodeWithText("None selected").assertIsDisplayed()
        compose.onNodeWithText("Tap to connect").assertIsDisplayed()
    }
}
