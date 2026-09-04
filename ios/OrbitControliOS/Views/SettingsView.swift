import SwiftUI

struct SettingsView: View {
    @EnvironmentObject private var model: AppModel
    @State private var showLogoutConfirmation = false
    @State private var showFirmwareDetails = false

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(spacing: 16) {
                    sessionCard
                    firmwareCard
                    debugCard
                    aboutCard
                }
                .padding()
                .frame(maxWidth: 760)
            }
            .navigationTitle("Setelan")
        }
        .navigationViewStyle(.stack)
        .confirmationDialog(
            "Keluar dari modem?",
            isPresented: $showLogoutConfirmation,
            titleVisibility: .visible
        ) {
            Button("Keluar", role: .destructive, action: model.logout)
        } message: {
            Text("Sesi aplikasi akan dihapus dari memori. Host dan username hanya tetap tersimpan bila opsi simpan login aktif.")
        }
        .sheet(
            isPresented: Binding(
                get: { model.shareURL != nil },
                set: { if !$0 { model.shareURL = nil } }
            )
        ) {
            if let url = model.shareURL {
                ActivityViewController(activityItems: [url])
            }
        }
    }

    private var sessionCard: some View {
        SectionCard("Sesi modem") {
            ValueRow("Host", model.host)
            ValueRow("Username", model.username)
            ValueRow("Status", model.isAuthenticated ? "Aktif" : "Tidak aktif", color: model.isAuthenticated ? .green : .red)
            Text("Password tidak disimpan dan tidak dimasukkan ke laporan debug.")
                .font(.footnote)
                .foregroundStyle(.secondary)
            Button(role: .destructive) {
                showLogoutConfirmation = true
            } label: {
                Label("Keluar dari modem", systemImage: "rectangle.portrait.and.arrow.right")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.bordered)
        }
    }

    private var firmwareCard: some View {
        SectionCard("Informasi modem") {
            Button {
                showFirmwareDetails.toggle()
                if showFirmwareDetails {
                    model.loadFirmwareDetails()
                }
            } label: {
                Label(
                    showFirmwareDetails ? "Sembunyikan detail" : "Muat detail firmware",
                    systemImage: "info.circle"
                )
                .frame(maxWidth: .infinity)
            }
            .buttonStyle(.bordered)

            if model.firmwareLoading {
                LoadingRow()
            }
            if showFirmwareDetails {
                if let message = model.firmwareMessage {
                    ErrorText(text: message)
                }
                ForEach(model.firmwareDetails) { field in
                    ValueRow(field.label, field.value)
                }
            }
        }
    }

    private var debugCard: some View {
        SectionCard("Debug report") {
            Text("Membuat file teks ringkas untuk troubleshooting. Password, nilai token, SessionID, IMEI, serial, dan XML mentah tidak disertakan.")
                .font(.footnote)
                .foregroundStyle(.secondary)
            Button(action: model.exportDebugReport) {
                if model.exportingReport {
                    ProgressView()
                } else {
                    Label("Buat dan bagikan laporan", systemImage: "square.and.arrow.up")
                        .frame(maxWidth: .infinity)
                }
            }
            .buttonStyle(.bordered)

            if let message = model.reportMessage {
                StatusText(text: message, isError: model.reportIsError)
            }
        }
    }

    private var aboutCard: some View {
        SectionCard("Tentang") {
            ValueRow("Aplikasi", "Orbit Control iOS")
            ValueRow("Versi", "0.5.0")
            ValueRow("Target", "iOS 15+")
            Text("Versi ini berfokus pada manajemen modem Huawei/Orbit B312: pemantauan, perangkat, Band Lock manual, statistik, diagnosis, dan laporan debug.")
                .font(.footnote)
                .foregroundStyle(.secondary)
        }
    }
}
