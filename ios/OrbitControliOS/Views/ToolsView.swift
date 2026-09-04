import SwiftUI

struct ToolsView: View {
    @State private var selectedTool = 0

    var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                Picker("Tool", selection: $selectedTool) {
                    Text("Band Lock").tag(0)
                    Text("Statistik").tag(1)
                    Text("Diagnosis").tag(2)
                }
                .pickerStyle(.segmented)
                .padding()

                Group {
                    switch selectedTool {
                    case 1:
                        StatisticsToolView()
                    case 2:
                        DiagnosisToolView()
                    default:
                        BandLockToolView()
                    }
                }
            }
            .navigationTitle("Tools")
        }
        .navigationViewStyle(.stack)
    }
}

private struct BandLockToolView: View {
    @EnvironmentObject private var model: AppModel
    @State private var showConfirmation = false

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                SectionCard("Band Lock manual") {
                    Text("Pilih tepat satu band LTE. Aplikasi akan mengirim konfigurasi ke modem, lalu membaca ulang hasilnya. Bila verifikasi tidak sesuai, aplikasi mencoba mengembalikan konfigurasi sebelumnya.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)

                    ValueRow("Mode jaringan", model.bandSnapshot.bandInfo.networkModeLabel)
                    ValueRow("Band aktif", model.bandSnapshot.bandInfo.activeBand ?? "N/A", color: .blue)

                    if let notice = model.bandSnapshot.bandInfo.supportedBandsNotice {
                        Text(notice)
                            .font(.footnote)
                            .foregroundStyle(.orange)
                    }

                    if model.bandLockChecking || model.bandLoading {
                        LoadingRow()
                    } else if !model.bandLockSupport.canApply {
                        ErrorText(text: model.bandLockSupport.message)
                    } else {
                        Text("Pilih band")
                            .font(.subheadline.weight(.semibold))
                        LazyVGrid(
                            columns: [GridItem(.adaptive(minimum: 62), spacing: 10)],
                            spacing: 10
                        ) {
                            ForEach(model.bandSnapshot.bandInfo.supportedBands) { option in
                                Button(action: { model.toggleBand(option.band) }) {
                                    Text(option.label)
                                        .frame(maxWidth: .infinity)
                                }
                                .buttonStyle(model.selectedBand == option.band ? .borderedProminent : .bordered)
                                .tint(option.isActive && model.selectedBand != option.band ? .green : .blue)
                                .accessibilityLabel("Pilih band \(option.band)")
                            }
                        }

                        Button {
                            showConfirmation = true
                        } label: {
                            if model.bandLockApplying {
                                ProgressView().tint(.white)
                            } else {
                                Label(
                                    model.selectedBand.map { "Terapkan B\($0)" } ?? "Pilih satu band",
                                    systemImage: "lock.fill"
                                )
                            }
                        }
                        .frame(maxWidth: .infinity)
                        .buttonStyle(.borderedProminent)
                        .disabled(model.selectedBand == nil || model.bandLockApplying)
                    }

                    if let result = model.bandLockResult {
                        StatusText(text: result.message, isError: !result.success)
                        if let requested = result.requestedLteBand {
                            ValueRow("Mask LTE yang dikirim", requested)
                        }
                        ValueRow("Band setelah verifikasi", result.activeBand ?? "N/A")
                    }
                }

                if !model.bandSnapshot.bandInfo.technicalFields.isEmpty {
                    SectionCard("Detail modem") {
                        ForEach(model.bandSnapshot.bandInfo.technicalFields) { field in
                            ValueRow(field.label, field.value)
                        }
                    }
                }
            }
            .padding()
            .frame(maxWidth: 760)
        }
        .confirmationDialog(
            "Terapkan Band Lock?",
            isPresented: $showConfirmation,
            titleVisibility: .visible
        ) {
            Button("Terapkan", role: .destructive, action: model.applyBandLock)
        } message: {
            Text("Modem akan dikunci ke \(model.selectedBand.map { "B\($0)" } ?? "band yang dipilih"). Koneksi seluler dapat terputus sementara.")
        }
        .onAppear {
            model.checkBandLockSupport()
        }
    }
}

private struct StatisticsToolView: View {
    @EnvironmentObject private var model: AppModel

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                SectionCard("Statistik jaringan") {
                    if model.statisticsLoading {
                        LoadingRow()
                    }
                    let traffic = model.statistics.traffic.value
                    let month = model.statistics.month.value
                    ValueRow("Koneksi", Formatters.connectionLabel(model.statistics.status.value?.connectionStatus))
                    ValueRow("Unduh sesi", Formatters.bytes(traffic?.currentDownload))
                    ValueRow("Unggah sesi", Formatters.bytes(traffic?.currentUpload))
                    ValueRow("Durasi sesi", Formatters.duration(traffic?.currentConnectTime))
                    Divider()
                    ValueRow("Unduh bulan ini", Formatters.bytes(month?.monthDownload), color: .blue)
                    ValueRow("Unggah bulan ini", Formatters.bytes(month?.monthUpload), color: .blue)
                    ValueRow("Total bulan ini", Formatters.bytes(month?.monthTotal), color: .blue)
                    ValueRow("Durasi bulan ini", Formatters.duration(month?.monthDuration))
                    Divider()
                    ValueRow("Total unduh", Formatters.bytes(month?.totalDownload))
                    ValueRow("Total unggah", Formatters.bytes(month?.totalUpload))
                    ValueRow("Total pemakaian", Formatters.bytes(month?.totalUsage))
                    ValueRow("Total durasi", Formatters.duration(month?.totalDuration))
                }

                endpointErrors
            }
            .padding()
            .frame(maxWidth: 760)
        }
        .refreshable {
            model.refreshStatistics()
        }
        .onAppear(perform: model.refreshStatistics)
    }

    @ViewBuilder
    private var endpointErrors: some View {
        let errors = [
            model.statistics.traffic.error,
            model.statistics.month.error,
            model.statistics.status.error,
        ].compactMap { $0 }
        if !errors.isEmpty {
            SectionCard("Data belum lengkap") {
                ForEach(Array(Set(errors)).sorted(), id: \.self) { error in
                    ErrorText(text: error)
                }
            }
        }
    }
}

private struct DiagnosisToolView: View {
    @EnvironmentObject private var model: AppModel

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                SectionCard("Diagnosis koneksi") {
                    Text("Pengujian memakai request HTTP/HTTPS ringan dari iPhone/iPad. Ini bukan speed test dan tidak mengubah konfigurasi modem.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)

                    TextField("Target, mis. 1.1.1.1", text: $model.diagnosisTarget)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .keyboardType(.URL)
                        .textFieldStyle(.roundedBorder)

                    HStack {
                        ForEach(["1.1.1.1", "8.8.8.8", "https://www.apple.com"], id: \.self) { target in
                            Button(target) { model.diagnosisTarget = target }
                                .buttonStyle(.bordered)
                                .font(.caption)
                        }
                    }

                    Button(action: model.runDiagnosis) {
                        if model.diagnosisRunning {
                            ProgressView().tint(.white)
                        } else {
                            Label("Jalankan diagnosis", systemImage: "stethoscope")
                        }
                    }
                    .frame(maxWidth: .infinity)
                    .buttonStyle(.borderedProminent)
                    .disabled(model.diagnosisRunning)
                }

                if let result = model.diagnosisResult {
                    SectionCard("Hasil diagnosis") {
                        ValueRow("Target", result.target)
                        ValueRow("Status", result.reachable ? "Terjangkau" : "Tidak terjangkau", color: result.reachable ? .green : .red)
                        ValueRow("Rata-rata latensi", result.latencyMS.map { "\($0) ms" } ?? "N/A")
                        ValueRow("Berhasil", "\(result.successCount)/\(result.attempts)")
                        ValueRow("Metode", result.method)
                        StatusText(text: result.message, isError: !result.reachable)
                    }
                }

                if let error = model.diagnosisError {
                    ErrorText(text: error)
                }
            }
            .padding()
            .frame(maxWidth: 760)
        }
    }
}
