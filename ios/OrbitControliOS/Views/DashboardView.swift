import SwiftUI

struct DashboardView: View {
    @EnvironmentObject private var model: AppModel

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(spacing: 16) {
                    if model.dashboardLoading && model.dashboardRefreshedAt == nil {
                        LoadingRow()
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }

                    connectionCard
                    signalCard
                    trafficCard
                    networkCard
                    endpointErrors

                    if let refreshedAt = model.dashboardRefreshedAt {
                        Text("Diperbarui (refreshedAt.formatted(date: .omitted, time: .standard))")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                }
                .padding()
                .frame(maxWidth: 760)
            }
            .navigationTitle("Ringkasan")
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(action: model.refreshDashboard) {
                        if model.dashboardLoading {
                            ProgressView()
                        } else {
                            Image(systemName: "arrow.clockwise")
                        }
                    }
                    .disabled(model.dashboardLoading)
                    .accessibilityLabel("Muat ulang")
                }
            }
            .refreshable {
                model.refreshDashboard()
            }
        }
        .navigationViewStyle(.stack)
        .onAppear(perform: model.refreshDashboard)
    }

    private var connectionCard: some View {
        SectionCard("Status modem") {
            let status = model.dashboard.status.value
            let operatorInfo = model.dashboard.operatorInfo.value
            ValueRow(
                "Koneksi",
                Formatters.connectionLabel(status?.connectionStatus),
                color: Formatters.connectionLabel(status?.connectionStatus) == "Terhubung" ? .green : .orange
            )
            ValueRow("Jaringan", Formatters.networkTypeLabel(status?.networkType))
            ValueRow("Operator", operatorInfo?.name ?? operatorInfo?.shortName ?? "N/A")
            ValueRow("PLMN", operatorInfo?.plmn ?? "N/A")
            ValueRow("RAT", operatorInfo?.radioAccessType ?? "N/A")
            ValueRow("Sinyal", status?.signalBars.map { "\($0)/5" } ?? "N/A")
            if status?.roaming == true {
                Label("Roaming aktif", systemImage: "r.circle.fill")
                    .font(.footnote)
                    .foregroundStyle(.orange)
            }
        }
    }

    private var signalCard: some View {
        SectionCard("Sinyal radio") {
            let signal = model.dashboard.signal.value
            ValueRow("Band aktif", signal?.band ?? "N/A", color: .blue)
            ValueRow("RSRP", Formatters.signal(signal?.rsrp, unit: "dBm"))
            ValueRow("RSRQ", Formatters.signal(signal?.rsrq, unit: "dB"))
            ValueRow("SINR", Formatters.signal(signal?.sinr, unit: "dB"))
            ValueRow("RSSI", Formatters.signal(signal?.rssi, unit: "dBm"))
            ValueRow("PCI", signal?.pci ?? "N/A")
            ValueRow("Cell ID", signal?.cellID ?? "N/A")
            ValueRow("eNodeB", signal?.eNodeB ?? "N/A")
        }
    }

    private var trafficCard: some View {
        SectionCard("Lalu lintas") {
            let traffic = model.dashboard.traffic.value
            ValueRow("Unduh saat ini", Formatters.bytes(traffic?.currentDownload))
            ValueRow("Unggah saat ini", Formatters.bytes(traffic?.currentUpload))
            ValueRow("Kecepatan unduh", Formatters.rate(traffic?.currentDownloadRate), color: .blue)
            ValueRow("Kecepatan unggah", Formatters.rate(traffic?.currentUploadRate), color: .blue)
            ValueRow("Total unduh", Formatters.bytes(traffic?.totalDownload))
            ValueRow("Total unggah", Formatters.bytes(traffic?.totalUpload))
            ValueRow("Durasi sesi", Formatters.duration(traffic?.currentConnectTime))
            ValueRow("Perangkat aktif", "\(model.dashboard.devices.value?.count ?? 0)")
        }
    }

    private var networkCard: some View {
        SectionCard("Jaringan lokal") {
            let status = model.dashboard.status.value
            let network = model.dashboard.network.value
            ValueRow("IP WAN", network?.wanIPAddress ?? status?.wanIPAddress ?? "N/A")
            ValueRow("IPv6 WAN", network?.wanIPv6Address ?? status?.wanIPv6Address ?? "N/A")
            ValueRow("DNS utama", status?.primaryDNS ?? "N/A")
            ValueRow("DNS sekunder", status?.secondaryDNS ?? "N/A")
            ValueRow("IP LAN", network?.lanIPAddress ?? "N/A")
            ValueRow("Netmask LAN", network?.lanNetmask ?? "N/A")
        }
    }

    @ViewBuilder
    private var endpointErrors: some View {
        let errors = [
            model.dashboard.status.error,
            model.dashboard.signal.error,
            model.dashboard.operatorInfo.error,
            model.dashboard.traffic.error,
            model.dashboard.network.error,
        ].compactMap { $0 }
        if !errors.isEmpty {
            SectionCard("Sebagian data belum tersedia") {
                ForEach(Array(Set(errors)).sorted(), id: \.self) { error in
                    ErrorText(text: error)
                }
            }
        }
    }
}
