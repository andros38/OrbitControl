package id.orbitcontrol

import android.app.Application
import id.orbitcontrol.data.AppPreferences
import id.orbitcontrol.data.HuaweiApiClient
import id.orbitcontrol.data.HuaweiSessionManager
import id.orbitcontrol.data.repository.DeviceRepository
import id.orbitcontrol.data.repository.DebugReportRepository
import id.orbitcontrol.data.repository.NetworkToolsRepository
import id.orbitcontrol.data.repository.SignalRepository
import id.orbitcontrol.data.repository.StatusRepository
import id.orbitcontrol.data.repository.TrafficRepository

/**
 * Dependency container used by the Windows launcher.  The small desktop
 * Application compatibility object only provides private data/cache folders;
 * it has no Android runtime dependency.
 */
class AppContainer(application: Application = Application()) {
    val preferences = AppPreferences(application)
    val apiClient = HuaweiApiClient()
    val sessionManager = HuaweiSessionManager(apiClient)
    val statusRepository = StatusRepository(sessionManager)
    val signalRepository = SignalRepository(sessionManager)
    val trafficRepository = TrafficRepository(sessionManager)
    val deviceRepository = DeviceRepository(sessionManager)
    val networkToolsRepository = NetworkToolsRepository(sessionManager)
    val debugReportRepository = DebugReportRepository(application, sessionManager)
}
