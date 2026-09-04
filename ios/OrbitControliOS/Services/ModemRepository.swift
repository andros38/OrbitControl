import Foundation

struct ModemRepository {
    let session: HuaweiSessionManager

    func dashboard() async -> DashboardSnapshot {
        async let signal = endpoint { try await signalInfo() }
        async let status = endpoint { try await modemStatus() }
        async let operatorInfo = endpoint { try await operator() }
        async let network = endpoint { try await networkInfo() }
        async let traffic = endpoint { try await trafficInfo() }
        async let devices = endpoint { try await connectedDevices() }
        let sessionValue = await sessionSummary()

        return await DashboardSnapshot(
            session: EndpointValue(value: sessionValue),
            signal: signal,
            status: status,
            operatorInfo: operatorInfo,
            network: network,
            traffic: traffic,
            devices: devices
        )
    }

    func signalInfo() async throws -> SignalInfo {
        let node = try await session.authenticatedGet("/api/device/signal")
        let cellID = Formatters.clean(node.firstValue(
            "cell_id", "cellid", "CellID", "cell_id_4g", "lte_cell_id", "ltecellid", "scell_id", "scellid"
        ))
        let explicitENodeB = Formatters.clean(node.firstValue(
            "enodeb_id", "enodeb", "eNodeB", "enb_id", "enbid", "lte_enodeb_id"
        ))
        let rawPCI = Formatters.clean(node.firstValue("pci", "PCI", "lte_pci", "physicalcellid", "PhysicalCellID"))

        return SignalInfo(
            rsrp: Formatters.decimal(node.firstValue("rsrp", "RSRP", "lte_rsrp", "LteRsrp")),
            rsrq: Formatters.decimal(node.firstValue("rsrq", "RSRQ", "lte_rsrq", "LteRsrq")),
            sinr: Formatters.decimal(node.firstValue("sinr", "SINR", "lte_sinr", "LteSinr")),
            rssi: Formatters.decimal(node.firstValue("rssi", "RSSI", "lte_rssi", "LteRssi")),
            band: Formatters.normalizedBand(node.firstValue("band", "Band", "lteband", "lte_band", "LTEBand", "band_info")),
            pci: Formatters.integer(rawPCI).map(String.init) ?? rawPCI,
            cellID: cellID,
            eNodeB: explicitENodeB ?? deriveENodeB(cellID)
        )
    }

    func modemStatus() async throws -> ModemStatus {
        let node = try await session.authenticatedGet("/api/monitoring/status")
        return ModemStatus(
            connectionStatus: Formatters.clean(node.firstValue("ConnectionStatus", "connectionstatus")),
            networkType: Formatters.clean(node.firstValue("CurrentNetworkType", "currentnetworktype", "CurrentServiceDomain")),
            signalBars: Formatters.integer(node.firstValue("SignalIcon", "signalicon")).map(Int.init),
            roaming: node.firstValue("RoamingStatus", "roamingstatus").map { $0 == "1" },
            wanIPAddress: Formatters.clean(node.firstValue("WanIPAddress", "wan_ip_address", "wanipaddress")),
            wanIPv6Address: Formatters.clean(node.firstValue("WanIPv6Address", "wan_ipv6_address", "wanipv6address")),
            primaryDNS: Formatters.clean(node.firstValue("PrimaryDns", "primarydns")),
            secondaryDNS: Formatters.clean(node.firstValue("SecondaryDns", "secondarydns"))
        )
    }

    func operator() async throws -> OperatorInfo {
        let node = try await session.authenticatedGet("/api/net/current-plmn")
        return OperatorInfo(
            name: Formatters.clean(node.firstValue("FullName", "fullname", "OperatorName", "operatorname")),
            shortName: Formatters.clean(node.firstValue("ShortName", "shortname")),
            plmn: Formatters.clean(node.firstValue("Numeric", "numeric", "PLMN", "plmn")),
            radioAccessType: Formatters.clean(node.firstValue("Rat", "rat", "RadioAccessTechnology"))
        )
    }

    func networkInfo() async throws -> NetworkInfo {
        async let deviceResult = try? session.authenticatedGet("/api/device/information")
        async let dhcpResult = try? session.authenticatedGet("/api/dhcp/settings")
        let device = await deviceResult
        let dhcp = await dhcpResult

        return NetworkInfo(
            wanIPAddress: Formatters.clean(device?.firstValue("WanIPAddress", "wan_ip_address", "wanipaddress")),
            wanIPv6Address: Formatters.clean(device?.firstValue("WanIPv6Address", "wan_ipv6_address", "wanipv6address")),
            lanIPAddress: Formatters.clean(dhcp?.firstValue("DhcpIPAddress", "dhcpipaddress", "LanIPAddress", "lanipaddress")),
            lanNetmask: Formatters.clean(dhcp?.firstValue("DhcpLanNetmask", "dhcplannetmask", "LanNetmask", "lannetmask"))
        )
    }

    func trafficInfo() async throws -> TrafficInfo {
        let node = try await session.authenticatedGet("/api/monitoring/traffic-statistics")
        return TrafficInfo(
            currentUpload: Formatters.integer(node.firstValue("CurrentUpload", "CurrentUploadTraffic", "SessionUpload")),
            currentDownload: Formatters.integer(node.firstValue("CurrentDownload", "CurrentDownloadTraffic", "SessionDownload")),
            currentUploadRate: Formatters.integer(node.firstValue("CurrentUploadRate", "UploadRate")),
            currentDownloadRate: Formatters.integer(node.firstValue("CurrentDownloadRate", "DownloadRate")),
            totalUpload: Formatters.integer(node.firstValue("TotalUpload", "TotalUploadTraffic", "AllUpload")),
            totalDownload: Formatters.integer(node.firstValue("TotalDownload", "TotalDownloadTraffic", "AllDownload")),
            currentConnectTime: Formatters.integer(node.firstValue("CurrentConnectTime", "CurrentDuration", "SessionDuration"))
        )
    }

    func connectedDevices() async throws -> [ConnectedDevice] {
        let root = try await session.authenticatedGet("/api/wlan/host-list")
        return root.descendants(named: "Host").enumerated().map { index, host in
            let addresses = splitAddresses(host.firstValue("IpAddress", "IPAddress", "ipaddress") ?? "")
            let mac = Formatters.clean(host.firstValue("MacAddress", "MACAddress", "macaddress")) ?? "—"
            let name = Formatters.clean(host.firstValue("HostName", "hostname")) ?? "Perangkat tanpa nama"
            let active = host.firstValue("Active", "active").map { $0 == "1" } ?? true
            return ConnectedDevice(
                id: "\(mac)|\(addresses.ipv4 ?? addresses.ipv6 ?? String(index))",
                name: name,
                ipAddress: addresses.ipv4 ?? addresses.ipv6 ?? "—",
                macAddress: mac,
                isActive: active,
                ipv4Address: addresses.ipv4,
                ipv6Address: addresses.ipv6
            )
        }
    }

    func bandSnapshot() async -> BandSnapshot {
        async let signal = endpoint { try await signalInfo() }
        async let status = endpoint { try await modemStatus() }
        async let netMode = readonlyEndpoint(path: "/api/net/net-mode", title: "Mode jaringan")
        async let netModeList = readonlyEndpoint(path: "/api/net/net-mode-list", title: "Daftar mode jaringan")
        async let lteBand = readonlyEndpoint(path: "/api/net/lte-band-info", title: "Info band LTE")

        let values = await (signal, status, netMode, netModeList, lteBand)
        let info = buildBandInfo(
            signal: values.0.value,
            status: values.1.value,
            netMode: values.2,
            netModeList: values.3,
            lteBand: values.4
        )
        return BandSnapshot(signal: values.0, status: values.1, bandInfo: info)
    }

    func bandLockSupport() async -> BandLockSupport {
        let snapshot = await bandSnapshot()
        let configuration = snapshot.bandInfo.currentConfig
        let missingConfiguration = configuration?.networkMode == nil
            || configuration?.networkBand == nil
            || configuration?.lteBand == nil

        if missingConfiguration {
            return BandLockSupport(
                state: .unsupported,
                canApply: false,
                endpoint: "/api/net/net-mode",
                message: "Band lock tidak didukung firmware ini. Konfigurasi NetworkMode/NetworkBand/LTEBand tidak lengkap."
            )
        }
        if snapshot.bandInfo.supportedBands.isEmpty {
            return BandLockSupport(
                state: .unsupported,
                canApply: false,
                endpoint: "/api/net/net-mode",
                message: "Band lock tidak didukung firmware ini. Daftar band LTE tidak dapat dibaca."
            )
        }
        return BandLockSupport(
            state: .candidate,
            canApply: true,
            endpoint: "/api/net/net-mode",
            message: "Endpoint kandidat band lock tersedia. Aplikasi menerapkan satu band LTE dan membaca ulang modem untuk verifikasi."
        )
    }

    func applyBandLock(_ requestedBands: [Int]) async -> BandLockResult {
        let bands = Array(Set(requestedBands.filter { (1...64).contains($0) })).sorted()
        guard !bands.isEmpty else {
            return BandLockResult(success: false, requestedBands: [], requestedLteBand: nil, before: nil, after: nil, activeBand: nil, restored: false, message: "Pilih satu band LTE.")
        }

        do {
            let beforeNode = try await session.authenticatedGet("/api/net/net-mode")
            let before = parseModeConfig(beforeNode)
            guard let networkMode = before.networkMode,
                  let networkBand = before.networkBand,
                  before.lteBand != nil
            else {
                return BandLockResult(success: false, requestedBands: bands, requestedLteBand: nil, before: before, after: nil, activeBand: nil, restored: false, message: "Konfigurasi modem tidak lengkap. Band lock dibatalkan.")
            }

            let target = Formatters.lteBandMask(bands)
            _ = try await session.authenticatedPost("/api/net/net-mode", fields: [
                "NetworkMode": networkMode,
                "NetworkBand": networkBand,
                "LTEBand": target,
            ])
            try? await Task.sleep(nanoseconds: 2_000_000_000)

            let afterNode = try await session.authenticatedGet("/api/net/net-mode")
            let after = parseModeConfig(afterNode)
            let afterSignal = try? await signalInfo()
            let signalBand = Formatters.bandNumber(afterSignal?.band)
            let verified = Formatters.normalizedHex(after.lteBand) == Formatters.normalizedHex(target)
                || (signalBand.map { bands.contains($0) } ?? false)

            if verified {
                return BandLockResult(
                    success: true,
                    requestedBands: bands,
                    requestedLteBand: target,
                    before: before,
                    after: after,
                    activeBand: afterSignal?.band,
                    restored: false,
                    message: "Band lock terverifikasi dari pembacaan ulang modem."
                )
            }

            let restored = await restoreMode(before)
            return BandLockResult(
                success: false,
                requestedBands: bands,
                requestedLteBand: target,
                before: before,
                after: after,
                activeBand: afterSignal?.band,
                restored: restored,
                message: restored
                    ? "POST diterima, tetapi hasil tidak sesuai setelah pembacaan ulang. Konfigurasi sebelumnya sudah dicoba dikembalikan."
                    : "POST diterima, tetapi hasil tidak sesuai setelah pembacaan ulang. Pengembalian konfigurasi sebelumnya gagal; cek modem secara manual."
            )
        } catch {
            return BandLockResult(
                success: false,
                requestedBands: bands,
                requestedLteBand: nil,
                before: nil,
                after: nil,
                activeBand: nil,
                restored: false,
                message: userMessage(error, fallback: "Band lock gagal diterapkan.")
            )
        }
    }

    func statistics() async -> NetworkStatisticsSnapshot {
        async let traffic = endpoint { try await trafficInfo() }
        async let month = endpoint { try await monthStatistics() }
        async let status = endpoint { try await modemStatus() }
        return await NetworkStatisticsSnapshot(traffic: traffic, month: month, status: status)
    }

    func diagnosis(target: String, attempts: Int = 3) async -> DiagnosisResult {
        let safeTarget = target.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "1.1.1.1" : target.trimmingCharacters(in: .whitespacesAndNewlines)
        let candidates = diagnosisURLs(for: safeTarget)
        var latencies: [Int] = []
        var failures: [String] = []

        for _ in 0..<max(1, attempts) {
            let started = Date()
            do {
                _ = try await requestAny(candidates)
                latencies.append(Int(Date().timeIntervalSince(started) * 1_000))
            } catch {
                failures.append(userMessage(error, fallback: "Target tidak merespons."))
            }
        }

        return DiagnosisResult(
            target: safeTarget,
            reachable: !latencies.isEmpty,
            latencyMS: latencies.isEmpty ? nil : Int(Double(latencies.reduce(0, +)) / Double(latencies.count)),
            attempts: max(1, attempts),
            successCount: latencies.count,
            failureCount: max(1, attempts) - latencies.count,
            method: "HTTP latency fallback",
            message: latencies.isEmpty ? (failures.first ?? "Target tidak dapat dijangkau.") : "Target merespons lewat request HTTP/HTTPS ringan."
        )
    }

    func firmwareDetails() async -> [InfoField] {
        let snapshot = await bandSnapshot()
        let sessionSnapshot = await session.snapshot()
        var fields = snapshot.bandInfo.technicalFields
        fields.append(contentsOf: [
            InfoField(label: "Status autentikasi", value: await session.isAuthenticated() ? "Aktif" : "Tidak aktif", source: "session"),
            InfoField(label: "SessionID", value: sessionSnapshot.hasSessionID ? "Tersedia (nilai disembunyikan)" : "Tidak tersedia", source: "session"),
            InfoField(label: "Token", value: sessionSnapshot.hasVerificationToken ? "Tersedia (nilai disembunyikan)" : "Tidak tersedia", source: "session"),
        ])
        return fields
    }

    func createDebugReport() async throws -> URL {
        let snapshot = await session.snapshot()
        let paths = [
            "/api/webserver/SesTokInfo",
            "/api/device/information",
            "/api/device/signal",
            "/api/monitoring/status",
            "/api/monitoring/traffic-statistics",
            "/api/monitoring/month_statistics",
            "/api/net/current-plmn",
            "/api/net/net-mode",
            "/api/net/net-mode-list",
            "/api/wlan/host-list",
        ]
        var probes: [(path: String, node: XMLNode?, error: Error?)] = []
        for path in paths {
            do {
                probes.append((path, try await session.authenticatedGet(path), nil))
            } catch {
                probes.append((path, nil, error))
            }
        }

        let operatorNode = probes.first { $0.path == "/api/net/current-plmn" }?.node
        let signalNode = probes.first { $0.path == "/api/device/signal" }?.node
        let deviceNode = probes.first { $0.path == "/api/device/information" }?.node
        let formatter = ISO8601DateFormatter()

        var lines = [
            "Orbit Control Debug Report",
            "Dibuat: \(formatter.string(from: Date()))",
            "",
            "KONEKSI",
            "Host modem: \(snapshot.host)",
            "SessionID: \(snapshot.hasSessionID ? "tersedia (nilai disembunyikan)" : "tidak tersedia")",
            "Verification token: \(snapshot.hasVerificationToken ? "tersedia (nilai disembunyikan)" : "tidak tersedia")",
            "Password: tidak disertakan",
            "",
            "JARINGAN",
            "Operator: \(operatorNode?.firstValue("FullName", "OperatorName", "ShortName") ?? "N/A")",
            "PLMN: \(operatorNode?.firstValue("Numeric", "PLMN") ?? "N/A")",
            "Band aktif: \(Formatters.normalizedBand(signalNode?.firstValue("band", "Band", "lteband", "LTEBand")) ?? "N/A")",
            "",
            "PERANGKAT / FIRMWARE",
            "Nama perangkat: \(deviceNode?.firstValue("DeviceName", "ProductName") ?? "N/A")",
            "Model/keluarga: \(deviceNode?.firstValue("ProductFamily", "Classify", "DeviceClass") ?? "N/A")",
            "Versi hardware: \(deviceNode?.firstValue("HardwareVersion") ?? "N/A")",
            "Versi software: \(deviceNode?.firstValue("SoftwareVersion") ?? "N/A")",
            "Versi WebUI: \(deviceNode?.firstValue("WebUIVersion") ?? "N/A")",
            "IMEI/serial: tidak disertakan",
            "",
            "STATUS ENDPOINT",
        ]

        for probe in probes {
            if probe.error == nil {
                lines.append("[OK] \(probe.path)")
            } else {
                let error = userMessage(probe.error!, fallback: "Endpoint gagal.")
                lines.append("[GAGAL] \(probe.path) | \(sanitize(error))")
            }
        }

        lines.append(contentsOf: [
            "",
            "Catatan privasi: laporan tidak memuat password, nilai SessionID, nilai token, IMEI, serial, atau respons XML mentah.",
        ])

        let filename = "orbit-control-debug-\(Int(Date().timeIntervalSince1970)).txt"
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(filename)
        try lines.joined(separator: "\n").write(to: url, atomically: true, encoding: .utf8)
        return url
    }

    private func monthStatistics() async throws -> MonthStatistics {
        let node = try await session.authenticatedGet("/api/monitoring/month_statistics")
        let monthUpload = Formatters.integer(node.firstValue("CurrentMonthUpload", "MonthUpload", "MonthUploadTraffic"))
        let monthDownload = Formatters.integer(node.firstValue("CurrentMonthDownload", "MonthDownload", "MonthDownloadTraffic"))
        let totalUpload = Formatters.integer(node.firstValue("TotalUpload", "TotalUploadTraffic", "AllUpload"))
        let totalDownload = Formatters.integer(node.firstValue("TotalDownload", "TotalDownloadTraffic", "AllDownload"))
        return MonthStatistics(
            monthUpload: monthUpload,
            monthDownload: monthDownload,
            monthTotal: Formatters.integer(node.firstValue("CurrentMonthTotal", "MonthTotal", "CurrentMonthTraffic", "MonthTraffic")) ?? sum(monthUpload, monthDownload),
            monthDuration: Formatters.integer(node.firstValue("MonthDuration", "CurrentMonthDuration", "MonthConnectTime")),
            totalUpload: totalUpload,
            totalDownload: totalDownload,
            totalUsage: Formatters.integer(node.firstValue("TotalTraffic", "TotalData", "AllTraffic", "TotalTrafficData")) ?? sum(totalUpload, totalDownload),
            totalDuration: Formatters.integer(node.firstValue("TotalConnectTime", "TotalDuration", "AllDuration", "TotalConnectDuration"))
        )
    }

    private func readonlyEndpoint(path: String, title: String) async -> EndpointValue<(fields: [InfoField], rawXML: String)> {
        await endpoint {
            let node = try await session.authenticatedGet(path)
            return (
                fields: leafFields(node).isEmpty ? [InfoField(label: "Status", value: "Endpoint tersedia, tetapi respons kosong.", source: path)] : leafFields(node),
                rawXML: HuaweiXML.serialize(node)
            )
        }
    }

    private func buildBandInfo(
        signal: SignalInfo?,
        status: ModemStatus?,
        netMode: EndpointValue<(fields: [InfoField], rawXML: String)>,
        netModeList: EndpointValue<(fields: [InfoField], rawXML: String)>,
        lteBand: EndpointValue<(fields: [InfoField], rawXML: String)>
    ) -> BandInfo {
        let configuration = netMode.value.map(parseModeConfig)
        let activeNumber = Formatters.bandNumber(signal?.band)
        let parsedBands = netModeList.value.map { supportedBands(rawXML: $0.rawXML) } ?? []
        let usingFallback = parsedBands.isEmpty
        let bands = (usingFallback ? [1, 3, 5, 8, 38, 40] : parsedBands)
            .map { LteBandOption(band: $0, isActive: $0 == activeNumber, source: usingFallback ? "fallback:B312" : "net-mode-list") }

        var technical: [InfoField] = []
        let sources = [
            ("Mode jaringan", "/api/net/net-mode", netMode),
            ("Daftar mode jaringan", "/api/net/net-mode-list", netModeList),
            ("Info band LTE", "/api/net/lte-band-info", lteBand),
        ]
        for (title, path, endpoint) in sources {
            if let value = endpoint.value {
                technical.append(contentsOf: value.fields.map { InfoField(label: "\(title): \($0.label)", value: $0.value, source: path) })
            } else if let error = endpoint.error {
                technical.append(InfoField(label: "Endpoint gagal", value: error, source: path))
            }
        }

        return BandInfo(
            currentConfig: configuration,
            networkModeLabel: configuration.map { Formatters.networkModeLabel($0.networkMode) } ?? Formatters.networkTypeLabel(status?.networkType),
            activeBand: signal?.band ?? activeNumber.map { "B\($0)" },
            supportedBands: bands,
            supportedBandsNotice: usingFallback ? "Daftar band fallback B312 karena firmware tidak mengembalikan daftar lengkap." : nil,
            technicalFields: technical
        )
    }

    private func parseModeConfig(_ node: XMLNode) -> NetModeConfig {
        NetModeConfig(
            networkMode: Formatters.clean(node.firstValue("NetworkMode", "networkmode")),
            networkBand: Formatters.clean(node.firstValue("NetworkBand", "networkband")),
            lteBand: Formatters.clean(node.firstValue("LTEBand", "lteband", "LTE_BAND"))
        )
    }

    private func restoreMode(_ config: NetModeConfig) async -> Bool {
        guard let networkMode = config.networkMode,
              let networkBand = config.networkBand,
              let lteBand = config.lteBand
        else { return false }
        do {
            _ = try await session.authenticatedPost("/api/net/net-mode", fields: [
                "NetworkMode": networkMode,
                "NetworkBand": networkBand,
                "LTEBand": lteBand,
            ])
            try? await Task.sleep(nanoseconds: 1_000_000_000)
            return true
        } catch {
            return false
        }
    }

    private func supportedBands(rawXML: String) -> [Int] {
        guard let root = HuaweiXML.parse(rawXML) else { return [] }
        var found = Set<Int>()
        let relevant = Set(["namelte", "name", "value", "lteband"])
        for node in root.allNodes() where relevant.contains(node.name.lowercased()) {
            let value = node.text.trimmingCharacters(in: .whitespacesAndNewlines)
            let named = extractNamedBands(value)
            if !named.isEmpty {
                found.formUnion(named)
            } else if node.name.caseInsensitiveCompare("LTEBand") == .orderedSame,
                      let mask = UInt64(value.replacingOccurrences(of: "0x", with: "", options: .caseInsensitive), radix: 16) {
                for band in 1...64 where mask & (UInt64(1) << UInt64(band - 1)) != 0 {
                    found.insert(band)
                }
            }
        }
        return found.filter { (1...64).contains($0) }.sorted()
    }

    private func extractNamedBands(_ value: String) -> [Int] {
        let expression = try? NSRegularExpression(pattern: "(?i)\\b(?:LTE\\s*)?(?:BC|B|Band)\\s*([1-9]\\d?)\\b")
        let range = NSRange(value.startIndex..., in: value)
        return expression?.matches(in: value, range: range).compactMap {
            Range($0.range(at: 1), in: value).flatMap { Int(value[$0]) }
        } ?? []
    }

    private func leafFields(_ node: XMLNode, prefix: String = "") -> [InfoField] {
        let path: String
        if prefix.isEmpty && node.name.caseInsensitiveCompare("response") == .orderedSame {
            path = ""
        } else if prefix.isEmpty {
            path = node.name
        } else {
            path = "\(prefix).\(node.name)"
        }

        if node.children.isEmpty {
            guard let value = Formatters.clean(node.text) else { return [] }
            let rawName = path.split(separator: ".").last.map(String.init) ?? node.name
            return [InfoField(label: friendlyLabel(rawName), value: value, source: path)]
        }

        return node.children.flatMap { leafFields($0, prefix: path) }
    }

    private func friendlyLabel(_ rawName: String) -> String {
        switch rawName.lowercased() {
        case "networkmode", "network_mode": return "Mode jaringan"
        case "networkband", "network_band": return "Band jaringan"
        case "lteband", "lte_band": return "Band LTE"
        case "namelte": return "Nama LTE"
        case "service_domain", "servicedomain": return "Service domain"
        case "accessmode": return "Mode akses"
        case "band": return "Band"
        case "rat": return "RAT"
        default:
            return rawName
                .replacingOccurrences(of: "_", with: " ")
                .replacingOccurrences(of: "-", with: " ")
                .split(separator: " ")
                .map { $0.prefix(1).uppercased() + $0.dropFirst() }
                .joined(separator: " ")
        }
    }

    private func splitAddresses(_ raw: String) -> (ipv4: String?, ipv6: String?) {
        let parts = raw
            .split(whereSeparator: { $0 == ";" || $0 == "," || $0 == " " || $0 == "\n" || $0 == "\t" })
            .map(String.init)
        let ipv4 = parts.first { $0.range(of: #"^\d{1,3}(?:\.\d{1,3}){3}$"#, options: .regularExpression) != nil }
        let ipv6 = parts.first { $0.contains(":") }
        return (ipv4, ipv6)
    }

    private func deriveENodeB(_ cellID: String?) -> String? {
        guard let cellID = Formatters.clean(cellID) else { return nil }
        if let dash = cellID.firstIndex(of: "-") {
            return String(cellID[..<dash]).trimmingCharacters(in: .whitespacesAndNewlines)
        }
        let number: UInt64?
        if cellID.lowercased().hasPrefix("0x") {
            number = UInt64(cellID.dropFirst(2), radix: 16)
        } else {
            number = UInt64(cellID)
        }
        guard let number, number > 255 else { return nil }
        return String(number >> 8)
    }

    private func diagnosisURLs(for target: String) -> [URL] {
        let raw = target.trimmingCharacters(in: .whitespacesAndNewlines).trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        if let qualified = URL(string: raw), qualified.scheme != nil { return [qualified] }
        let host = raw.isEmpty ? "1.1.1.1" : raw
        return ["https://\(host)/", "http://\(host)/"].compactMap(URL.init(string:))
    }

    private func requestAny(_ urls: [URL]) async throws -> Bool {
        var lastError: Error?
        for url in urls {
            var request = URLRequest(url: url)
            request.httpMethod = "HEAD"
            request.timeoutInterval = 8
            request.setValue("OrbitControl/1.0", forHTTPHeaderField: "User-Agent")
            do {
                let (_, response) = try await URLSession.shared.data(for: request)
                if let response = response as? HTTPURLResponse, (100...599).contains(response.statusCode) {
                    return true
                }
            } catch {
                lastError = error
            }
        }
        throw lastError ?? HuaweiAPIError("Target tidak merespons lewat HTTP/HTTPS.")
    }

    private func sessionSummary() async -> String {
        let snapshot = await session.snapshot()
        if snapshot.hasSessionID && snapshot.hasVerificationToken { return "SessionID dan token aktif" }
        if snapshot.hasSessionID { return "SessionID aktif" }
        return "Sesi belum tersedia"
    }

    private func endpoint<T>(_ work: () async throws -> T) async -> EndpointValue<T> {
        do {
            return EndpointValue(value: try await work())
        } catch {
            return EndpointValue(error: userMessage(error, fallback: "Data belum tersedia."))
        }
    }

    private func sum(_ left: Int64?, _ right: Int64?) -> Int64? {
        guard left != nil || right != nil else { return nil }
        return (left ?? 0) + (right ?? 0)
    }

    private func userMessage(_ error: Error, fallback: String) -> String {
        if let error = error as? HuaweiAPIError { return error.message }
        return error.localizedDescription.isEmpty ? fallback : error.localizedDescription
    }

    private func sanitize(_ value: String) -> String {
        let redacted = value.replacingOccurrences(
            of: "(?i)(token|sessionid)=[^\\s;,]+",
            with: "$1=<disembunyikan>",
            options: .regularExpression
        )
        return String(redacted.prefix(180))
    }
}
