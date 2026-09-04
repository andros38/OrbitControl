import SwiftUI

struct DevicesView: View {
    @EnvironmentObject private var model: AppModel

    var body: some View {
        NavigationView {
            Group {
                if model.devicesLoading && model.devices.isEmpty {
                    LoadingRow()
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else if let error = model.devicesError, model.devices.isEmpty {
                    VStack(spacing: 12) {
                        ErrorText(text: error)
                        Button("Coba lagi", action: model.refreshDevices)
                            .buttonStyle(.bordered)
                    }
                    .padding()
                } else if model.devices.isEmpty {
                    VStack(spacing: 12) {
                        Image(systemName: "desktopcomputer")
                            .font(.system(size: 40))
                            .foregroundStyle(.secondary)
                        Text("Tidak ada perangkat")
                            .font(.headline)
                        Text("Modem tidak melaporkan perangkat Wi-Fi/LAN yang terhubung.")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                            .multilineTextAlignment(.center)
                    }
                    .padding(30)
                } else {
                    List {
                        if let error = model.devicesError {
                            ErrorText(text: error)
                                .listRowBackground(Color.clear)
                        }
                        ForEach(model.devices) { device in
                            VStack(alignment: .leading, spacing: 8) {
                                HStack {
                                    Image(systemName: device.isActive == false ? "desktopcomputer.trianglebadge.exclamationmark" : "desktopcomputer")
                                        .foregroundStyle(device.isActive == false ? .orange : .blue)
                                    Text(device.name)
                                        .fontWeight(.semibold)
                                    Spacer()
                                    Text(device.isActive == false ? "Nonaktif" : "Aktif")
                                        .font(.caption)
                                        .foregroundStyle(device.isActive == false ? .secondary : .green)
                                }
                                ValueRow("IP", device.ipAddress)
                                ValueRow("MAC", device.macAddress)
                                if let ipv6 = device.ipv6Address, ipv6 != device.ipAddress {
                                    ValueRow("IPv6", ipv6)
                                }
                            }
                            .padding(.vertical, 5)
                        }
                    }
                    .listStyle(.insetGrouped)
                }
            }
            .navigationTitle("Perangkat")
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(action: model.refreshDevices) {
                        if model.devicesLoading {
                            ProgressView()
                        } else {
                            Image(systemName: "arrow.clockwise")
                        }
                    }
                    .disabled(model.devicesLoading)
                    .accessibilityLabel("Muat ulang perangkat")
                }
            }
            .refreshable {
                model.refreshDevices()
            }
        }
        .navigationViewStyle(.stack)
        .onAppear(perform: model.refreshDevices)
    }
}
