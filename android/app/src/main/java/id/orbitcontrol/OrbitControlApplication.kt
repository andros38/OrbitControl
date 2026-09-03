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

class OrbitControlApplication : Application() {
    val container by lazy { AppContainer(this) }
}

class AppContainer(application: Application) {
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
