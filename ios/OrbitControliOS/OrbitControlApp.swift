import SwiftUI

@main
struct OrbitControlApp: App {
    @StateObject private var model = AppModel()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(model)
                .tint(.blue)
        }
    }
}

struct RootView: View {
    @EnvironmentObject private var model: AppModel

    var body: some View {
        Group {
            if model.isAuthenticated {
                MainTabView()
            } else {
                LoginView()
            }
        }
        .animation(.easeInOut, value: model.isAuthenticated)
    }
}

struct MainTabView: View {
    var body: some View {
        TabView {
            DashboardView()
                .tabItem { Label("Ringkasan", systemImage: "house") }
            ToolsView()
                .tabItem { Label("Tools", systemImage: "slider.horizontal.3") }
            DevicesView()
                .tabItem { Label("Perangkat", systemImage: "desktopcomputer") }
            SettingsView()
                .tabItem { Label("Setelan", systemImage: "gearshape") }
        }
    }
}
