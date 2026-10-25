package com.phonekey.vpn.ui

import android.Manifest
import android.app.Activity
import android.net.VpnService
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.phonekey.vpn.R
import com.phonekey.vpn.domain.model.TunnelStatus
import com.phonekey.vpn.domain.model.VpnProfile
import com.phonekey.vpn.ui.home.HomeScreen
import com.phonekey.vpn.ui.profiles.AddConfigScreen
import com.phonekey.vpn.ui.profiles.DeleteDialog
import com.phonekey.vpn.ui.profiles.EditConfigScreen
import com.phonekey.vpn.ui.profiles.ProfilesScreen
import com.phonekey.vpn.ui.profiles.QrDialog
import com.phonekey.vpn.ui.settings.SettingsScreen
import com.phonekey.vpn.ui.theme.PhoneKeyTheme

private data class Tab(val route: String, val label: String, val icon: Int)

private val tabs = listOf(
    Tab("home", "Home", R.drawable.ic_power),
    Tab("profiles", "Profiles", R.drawable.ic_key_small),
    Tab("settings", "Settings", R.drawable.ic_settings),
)

@Composable
fun PhoneKeyRoot(vm: PhoneKeyViewModel = viewModel()) {
    PhoneKeyTheme {
        val context = LocalContext.current
        val nav = rememberNavController()
        val snackbar = remember { SnackbarHostState() }
        val session by vm.session.collectAsStateWithLifecycle()
        val profiles by vm.profiles.collectAsStateWithLifecycle()
        val settings by vm.settings.collectAsStateWithLifecycle()
        val selected by vm.selected.collectAsStateWithLifecycle()
        val backStack by nav.currentBackStackEntryAsState()
        val route = backStack?.destination?.route ?: "home"
        var pendingConnect by remember { mutableStateOf(false) }
        var qrProfile by remember { mutableStateOf<VpnProfile?>(null) }
        var deleteProfile by remember { mutableStateOf<VpnProfile?>(null) }
        var askedLaunchConnect by remember { mutableStateOf(false) }

        val vpnPermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && pendingConnect) vm.connectSelected()
            else if (pendingConnect) vm.note("VPN permission is required to connect.")
            pendingConnect = false
        }
        val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) vm.importUri(uri)
        }
        val scan = rememberLauncherForActivityResult(ScanContract()) { result ->
            result.contents?.let { vm.importText(it, "Scanned") }
        }
        val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

        fun connect() {
            val prepare = VpnService.prepare(context)
            if (prepare != null) {
                pendingConnect = true
                vpnPermission.launch(prepare)
            } else {
                vm.connectSelected()
            }
        }

        LaunchedEffect(Unit) {
            if (Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        LaunchedEffect(settings.autoConnectOnLaunch, settings.selectedProfileId) {
            if (askedLaunchConnect || !settings.autoConnectOnLaunch || settings.selectedProfileId == null) return@LaunchedEffect
            askedLaunchConnect = true
            if (session.status == TunnelStatus.DISCONNECTED && VpnService.prepare(context) == null) {
                vm.connectSelected()
            }
        }
        LaunchedEffect(Unit) {
            vm.banner.collect { message ->
                if (message != null) {
                    snackbar.showSnackbar(message)
                    vm.dismissBanner()
                }
            }
        }

        val showBars = route == "home" || route == "profiles" || route == "settings"
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                if (showBars) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        tabs.forEach { tab ->
                            NavigationBarItem(
                                selected = route == tab.route,
                                onClick = {
                                    nav.navigate(tab.route) {
                                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(painterResource(tab.icon), contentDescription = null) },
                                label = { Text(tab.label) },
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                if (route == "profiles") {
                    FloatingActionButton(onClick = { nav.navigate("add") }) {
                        Icon(painterResource(R.drawable.ic_add), contentDescription = "Add profile")
                    }
                }
            },
        ) { padding ->
            NavHost(nav, startDestination = "home", modifier = Modifier.padding(padding)) {
                composable("home") {
                    HomeScreen(
                        session = session,
                        selected = selected,
                        onToggle = {
                            if (session.status == TunnelStatus.CONNECTED || session.status == TunnelStatus.CONNECTING) {
                                vm.disconnect()
                            } else if (selected == null) {
                                nav.navigate("profiles")
                            } else {
                                connect()
                            }
                        },
                        onChooseProfile = { nav.navigate("profiles") },
                    )
                }
                composable("profiles") {
                    ProfilesScreen(
                        profiles = profiles,
                        selectedId = settings.selectedProfileId,
                        onSelect = vm::select,
                        onEdit = { nav.navigate("edit/$it") },
                        onDelete = { id -> deleteProfile = profiles.firstOrNull { it.id == id } },
                        onQr = { qrProfile = it },
                    )
                }
                composable("settings") {
                    SettingsScreen(settings = settings, onChange = vm::updateSettings)
                }
                composable("add") {
                    AddConfigScreen(
                        vm = vm,
                        onBack = { nav.popBackStack() },
                        onFile = {
                            openFile.launch(arrayOf("*/*", "text/plain", "application/json"))
                        },
                        onQr = {
                            scan.launch(
                                ScanOptions().apply {
                                    setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                    setPrompt("Scan a WireGuard or Xray config")
                                    setBeepEnabled(false)
                                    setOrientationLocked(false)
                                    setCaptureActivity(com.journeyapps.barcodescanner.CaptureActivity::class.java)
                                },
                            )
                        },
                    )
                }
                composable("edit/{id}") { entry ->
                    val id = entry.arguments?.getString("id")
                    EditConfigScreen(
                        vm = vm,
                        profile = profiles.firstOrNull { it.id == id },
                        onBack = { nav.popBackStack() },
                    )
                }
            }
        }
        QrDialog(qrProfile) { qrProfile = null }
        DeleteDialog(
            profile = deleteProfile,
            onConfirm = {
                deleteProfile?.let { vm.delete(it.id) }
                deleteProfile = null
            },
            onDismiss = { deleteProfile = null },
        )
    }
}
