package id.orbitcontrol

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as OrbitControlApplication).container
        setContent {
            OrbitControlTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    OrbitControlApp(container)
                }
            }
        }
    }
}

@Composable
private fun OrbitControlApp(container: AppContainer) {
    val authenticated by container.sessionManager.authenticated.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableStateOf(OrbitTab.SUMMARY) }
    var selectedNetworkToolsTab by rememberSaveable { mutableIntStateOf(0) }
    if (!authenticated) {
        val login: LoginViewModel = viewModel(factory = OrbitFactory {
            LoginViewModel(container.sessionManager, container.preferences)
        })
        LoginScreen(login)
    } else {
        OrbitShell(
            container = container,
            selected = selectedTab,
            onSelected = { selectedTab = it },
            selectedNetworkToolsTab = selectedNetworkToolsTab,
            onNetworkToolsTabSelected = { selectedNetworkToolsTab = it },
        )
    }
}

private enum class OrbitTab(val label: String) {
    SUMMARY("Ringkasan"),
    TOOLS("Tools"),
    DEVICES("Perangkat"),
    SETTINGS("Setelan"),
}

private val OrbitTab.displaySymbol: String
    get() = when (this) {
        OrbitTab.SUMMARY -> "\u2302"
        OrbitTab.TOOLS -> "\u2261"
        OrbitTab.DEVICES -> "\u25CF"
        OrbitTab.SETTINGS -> "\u2699"
    }

@Composable
private fun OrbitShell(
    container: AppContainer,
    selected: OrbitTab,
    onSelected: (OrbitTab) -> Unit,
    selectedNetworkToolsTab: Int,
    onNetworkToolsTabSelected: (Int) -> Unit,
) {
    Scaffold(
        bottomBar = {
            NavigationBar {
                OrbitTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selected == tab,
                        onClick = { onSelected(tab) },
                        icon = { Text(tab.displaySymbol) },
                        label = {
                            Text(
                                tab.label,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }
        },
    ) { padding ->
        Surface(Modifier.fillMaxSize().padding(padding), color = MaterialTheme.colorScheme.background) {
            when (selected) {
                OrbitTab.SUMMARY -> {
                    val vm: DashboardViewModel = viewModel(factory = OrbitFactory {
                        DashboardViewModel(
                            container.statusRepository,
                            container.signalRepository,
                            container.trafficRepository,
                            container.deviceRepository,
                        )
                    })
                    DashboardScreen(vm)
                }
                OrbitTab.TOOLS -> {
                    val vm: NetworkToolsViewModel = viewModel(factory = OrbitFactory {
                        NetworkToolsViewModel(container.networkToolsRepository)
                    })
                    NetworkToolsScreen(vm, selectedNetworkToolsTab, onNetworkToolsTabSelected)
                }
                OrbitTab.DEVICES -> {
                    val vm: DeviceViewModel = viewModel(factory = OrbitFactory {
                        DeviceViewModel(container.deviceRepository)
                    })
                    DeviceScreen(vm)
                }
                OrbitTab.SETTINGS -> {
                    val vm: SettingsViewModel = viewModel(factory = OrbitFactory {
                        SettingsViewModel(
                            container.sessionManager,
                            container.debugReportRepository,
                            container.networkToolsRepository,
                        )
                    })
                    SettingsScreen(vm)
                }
            }
        }
    }
}

private class OrbitFactory<V : ViewModel>(private val creator: () -> V) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = creator() as T
}
