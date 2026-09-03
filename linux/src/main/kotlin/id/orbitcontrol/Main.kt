package id.orbitcontrol

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.orbitcontrol.ui.DashboardScreen
import id.orbitcontrol.ui.DeviceScreen
import id.orbitcontrol.ui.LoginScreen
import id.orbitcontrol.ui.NetworkToolsScreen
import id.orbitcontrol.ui.SettingsScreen
import id.orbitcontrol.ui.components.OrbitControlTheme
import id.orbitcontrol.viewmodel.DashboardViewModel
import id.orbitcontrol.viewmodel.DeviceViewModel
import id.orbitcontrol.viewmodel.LoginViewModel
import id.orbitcontrol.viewmodel.NetworkToolsViewModel
import id.orbitcontrol.viewmodel.SettingsViewModel

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Orbit Control",
        state = rememberWindowState(width = 1280.dp, height = 820.dp),
    ) {
        OrbitControlTheme { Surface(color = MaterialTheme.colorScheme.background) { OrbitControlLinuxApp() } }
    }
}

private enum class DesktopTab(val label: String, val symbol: String) {
    SUMMARY("Ringkasan", "⌂"),
    TOOLS("Tools", "≡"),
    DEVICES("Perangkat", "●"),
    SETTINGS("Setelan", "⚙"),
}

@Composable
private fun OrbitControlLinuxApp() {
    val container = remember { AppContainer() }
    val authenticated by container.sessionManager.authenticated.collectAsStateWithLifecycle()
    if (!authenticated) {
        val loginModel = remember(container) { LoginViewModel(container.sessionManager, container.preferences) }
        LoginScreen(loginModel)
    } else {
        DesktopShell(container)
    }
}

@Composable
private fun DesktopShell(container: AppContainer) {
    var selectedTab by remember { mutableStateOf(DesktopTab.SUMMARY) }
    var selectedNetworkToolTab by remember { mutableIntStateOf(0) }
    val dashboard = remember(container) {
        DashboardViewModel(container.statusRepository, container.signalRepository, container.trafficRepository, container.deviceRepository)
    }
    val networkTools = remember(container) { NetworkToolsViewModel(container.networkToolsRepository) }
    val devices = remember(container) { DeviceViewModel(container.deviceRepository) }
    val settings = remember(container) {
        SettingsViewModel(
            container.sessionManager,
            container.debugReportRepository,
            container.networkToolsRepository,
        )
    }

    Row(Modifier.fillMaxSize()) {
        NavigationRail {
            DesktopTab.entries.forEach { tab ->
                NavigationRailItem(
                    selected = tab == selectedTab,
                    onClick = { selectedTab = tab },
                    icon = { Text(tab.symbol) },
                    label = { Text(tab.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
            }
        }
        Surface(Modifier.weight(1f).fillMaxSize().padding(end = 2.dp), color = MaterialTheme.colorScheme.background) {
            when (selectedTab) {
                DesktopTab.SUMMARY -> DashboardScreen(dashboard)
                DesktopTab.TOOLS -> NetworkToolsScreen(networkTools, selectedNetworkToolTab) { selectedNetworkToolTab = it }
                DesktopTab.DEVICES -> DeviceScreen(devices)
                DesktopTab.SETTINGS -> SettingsScreen(settings)
            }
        }
    }
}
