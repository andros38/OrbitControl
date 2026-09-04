import Foundation
import SwiftUI

@MainActor
final class AppModel: ObservableObject {
    @Published var isAuthenticated = false
    @Published var host: String
    @Published var username: String
    @Published var password = ""
    @Published var saveLogin = false
    @Published var loginBusy = false
    @Published var loginMessage: String?
    @Published var loginIsError = false

    @Published var dashboard = DashboardSnapshot()
    @Published var dashboardLoading = false
    @Published var dashboardRefreshedAt: Date?

    @Published var devices: [ConnectedDevice] = []
    @Published var devicesLoading = false
    @Published var devicesError: String?

    @Published var bandSnapshot = BandSnapshot()
    @Published var bandLoading = false
    @Published var bandLockChecking = false
    @Published var bandLockApplying = false
    @Published var bandLockSupport = BandLockSupport()
    @Published var bandLockResult: BandLockResult?
    @Published var selectedBand: Int?

    @Published var statistics = NetworkStatisticsSnapshot()
    @Published var statisticsLoading = false
    @Published var diagnosisTarget = "1.1.1.1"
    @Published var diagnosisRunning = false
    @Published var diagnosisResult: DiagnosisResult?
    @Published var diagnosisError: String?

    @Published var firmwareDetails: [InfoField] = []
    @Published var firmwareLoading = false
    @Published var firmwareMessage: String?
    @Published var exportingReport = false
    @Published var reportMessage: String?
    @Published var reportIsError = false
    @Published var shareURL: URL?

    private let session = HuaweiSessionManager()
    private lazy var repository = ModemRepository(session: session)
    private var refreshTask: Task<Void, Never>?

    init() {
        let defaults = UserDefaults.standard
        host = defaults.string(forKey: "orbit.host") ?? "http://192.168.8.1"
        username = defaults.string(forKey: "orbit.username") ?? "admin"
        saveLogin = defaults.bool(forKey: "orbit.saveLogin")
    }

    deinit {
        refreshTask?.cancel()
    }

    func testConnection() {
        guard !loginBusy else { return }
        loginBusy = true
        loginMessage = "Memeriksa koneksi modem..."
        loginIsError = false
        Task {
            do {
                try await session.testConnection(host: host)
                loginMessage = "Koneksi modem berhasil."
                loginIsError = false
            } catch {
                loginMessage = message(for: error, fallback: "Koneksi modem gagal.")
                loginIsError = true
            }
            loginBusy = false
        }
    }

    func login() {
        guard !loginBusy else { return }
        loginBusy = true
        loginMessage = nil
        loginIsError = false

        Task {
            do {
                try await session.login(host: host, username: username, password: password)
                persistLoginConfig()
                password = ""
                isAuthenticated = true
                loginMessage = nil
                await refreshAfterLogin()
                startAutoRefresh()
            } catch {
                loginMessage = message(for: error, fallback: "Login modem gagal.")
                loginIsError = true
            }
            loginBusy = false
        }
    }

    func logout() {
        refreshTask?.cancel()
        refreshTask = nil
        Task { await session.logout() }
        isAuthenticated = false
        password = ""
        dashboard = DashboardSnapshot()
        devices = []
        bandSnapshot = BandSnapshot()
        bandLockSupport = BandLockSupport()
        bandLockResult = nil
        selectedBand = nil
        statistics = NetworkStatisticsSnapshot()
        diagnosisResult = nil
        firmwareDetails = []
    }

    func refreshDashboard() {
        guard !dashboardLoading else { return }
        dashboardLoading = true
        Task {
            dashboard = await repository.dashboard()
            dashboardRefreshedAt = Date()
            dashboardLoading = false
        }
    }

    func refreshDevices() {
        guard !devicesLoading else { return }
        devicesLoading = true
        devicesError = nil
        Task {
            do {
                devices = try await repository.connectedDevices()
            } catch {
                devicesError = message(for: error, fallback: "Daftar perangkat belum tersedia.")
            }
            devicesLoading = false
        }
    }

    func refreshBand() {
        guard !bandLoading else { return }
        bandLoading = true
        Task {
            bandSnapshot = await repository.bandSnapshot()
            let validBands = Set(bandSnapshot.bandInfo.supportedBands.map(\.band))
            if let selectedBand, !validBands.contains(selectedBand) {
                self.selectedBand = nil
            }
            bandLoading = false
        }
    }

    func checkBandLockSupport() {
        guard !bandLockChecking else { return }
        bandLockChecking = true
        bandLockResult = nil
        Task {
            bandLockSupport = await repository.bandLockSupport()
            bandLockChecking = false
            refreshBand()
        }
    }

    func toggleBand(_ band: Int) {
        selectedBand = selectedBand == band ? nil : band
    }

    func applyBandLock() {
        guard !bandLockApplying,
              bandLockSupport.canApply,
              let selectedBand
        else { return }

        bandLockApplying = true
        bandLockResult = nil
        Task {
            bandLockResult = await repository.applyBandLock([selectedBand])
            bandLockApplying = false
            refreshBand()
            for delay in [5, 10, 20, 30] {
                try? await Task.sleep(nanoseconds: UInt64(delay) * 1_000_000_000)
                guard !Task.isCancelled, isAuthenticated else { return }
                refreshBand()
            }
        }
    }

    func refreshStatistics() {
        guard !statisticsLoading else { return }
        statisticsLoading = true
        Task {
            statistics = await repository.statistics()
            statisticsLoading = false
        }
    }

    func runDiagnosis() {
        guard !diagnosisRunning else { return }
        diagnosisRunning = true
        diagnosisError = nil
        diagnosisResult = nil
        Task {
            diagnosisResult = await repository.diagnosis(target: diagnosisTarget)
            diagnosisRunning = false
        }
    }

    func loadFirmwareDetails() {
        guard !firmwareLoading else { return }
        firmwareLoading = true
        firmwareMessage = nil
        Task {
            firmwareDetails = await repository.firmwareDetails()
            if firmwareDetails.isEmpty {
                firmwareMessage = "Detail firmware tidak tersedia."
            }
            firmwareLoading = false
        }
    }

    func exportDebugReport() {
        guard !exportingReport else { return }
        exportingReport = true
        reportMessage = "Membuat laporan..."
        reportIsError = false
        Task {
            do {
                shareURL = try await repository.createDebugReport()
                reportMessage = "Laporan siap dibagikan."
                reportIsError = false
            } catch {
                reportMessage = message(for: error, fallback: "Laporan debug gagal dibuat.")
                reportIsError = true
            }
            exportingReport = false
        }
    }

    private func refreshAfterLogin() async {
        refreshDashboard()
        refreshDevices()
        refreshBand()
        refreshStatistics()
        checkBandLockSupport()
    }

    private func startAutoRefresh() {
        refreshTask?.cancel()
        refreshTask = Task { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 5_000_000_000)
                guard !Task.isCancelled else { return }
                self?.refreshDashboard()
            }
        }
    }

    private func persistLoginConfig() {
        let defaults = UserDefaults.standard
        defaults.set(saveLogin, forKey: "orbit.saveLogin")
        if saveLogin {
            defaults.set(host.trimmingCharacters(in: .whitespacesAndNewlines), forKey: "orbit.host")
            defaults.set(username.trimmingCharacters(in: .whitespacesAndNewlines), forKey: "orbit.username")
        } else {
            defaults.removeObject(forKey: "orbit.host")
            defaults.removeObject(forKey: "orbit.username")
        }
    }

    private func message(for error: Error, fallback: String) -> String {
        if let error = error as? HuaweiAPIError { return error.message }
        return error.localizedDescription.isEmpty ? fallback : error.localizedDescription
    }
}
