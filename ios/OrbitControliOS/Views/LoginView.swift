import SwiftUI

struct LoginView: View {
    @EnvironmentObject private var model: AppModel

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(spacing: 20) {
                    Image(systemName: "antenna.radiowaves.left.and.right")
                        .font(.system(size: 48, weight: .semibold))
                        .foregroundStyle(.blue)
                        .padding(.top, 36)

                    VStack(spacing: 6) {
                        Text("Orbit Control")
                            .font(.largeTitle.bold())
                        Text("Manajemen modem Huawei/Orbit B312")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }

                    SectionCard("Login modem") {
                        TextField("Host modem", text: $model.host)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                            .keyboardType(.URL)
                            .textContentType(.URL)
                            .textFieldStyle(.roundedBorder)

                        TextField("Username", text: $model.username)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                            .textFieldStyle(.roundedBorder)

                        SecureField("Password modem", text: $model.password)
                            .textFieldStyle(.roundedBorder)

                        Toggle("Simpan host dan username", isOn: $model.saveLogin)
                            .font(.subheadline)

                        Text("Password tidak disimpan pada perangkat.")
                            .font(.footnote)
                            .foregroundStyle(.secondary)

                        Button(action: model.testConnection) {
                            Label("Test Koneksi", systemImage: "network")
                                .frame(maxWidth: .infinity)
                        }
                        .buttonStyle(.bordered)
                        .disabled(model.loginBusy)

                        Button(action: model.login) {
                            if model.loginBusy {
                                ProgressView().tint(.white)
                            } else {
                                Label("Masuk", systemImage: "arrow.right.circle.fill")
                            }
                        }
                        .frame(maxWidth: .infinity)
                        .buttonStyle(.borderedProminent)
                        .disabled(model.loginBusy || model.password.isEmpty)

                        if let message = model.loginMessage {
                            StatusText(text: message, isError: model.loginIsError)
                        }
                    }

                    Text("Hubungkan iPhone/iPad ke Wi‑Fi atau LAN modem sebelum login. Host bawaan adalah http://192.168.8.1.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 12)
                }
                .padding()
                .frame(maxWidth: 560)
            }
            .navigationBarTitleDisplayMode(.inline)
        }
    }
}
