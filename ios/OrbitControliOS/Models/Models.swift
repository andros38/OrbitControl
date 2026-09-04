import Foundation

struct SavedLoginConfig {
    var host: String = "http://192.168.8.1"
    var username: String = "admin"
    var saveConfig: Bool = false
}

struct SessionSnapshot {
    let host: String
    let username: String
    let hasSessionID: Bool
    let hasVerificationToken: Bool
}

struct EndpointValue<Value> {
    var value: Value?
    var error: String?

    init(value: Value? = nil, error: String? = nil) {
        self.value = value
        self.error = error
    }
}

struct SignalInfo {
    var rsrp: Double?
    var rsrq: Double?
    var sinr: Double?
    var rssi: Double?
    var band: String?
    var pci: String?
    var cellID: String?
    var eNodeB: String?
}

struct ModemStatus {
    var connectionStatus: String?
    var networkType: String?
    var signalBars: Int?
    var roaming: Bool?
    var wanIPAddress: String?
    var wanIPv6Address: String?
    var primaryDNS: String?
    var secondaryDNS: String?
}

struct OperatorInfo {
    var name: String?
    var shortName: String?
    var plmn: String?
    var radioAccessType: String?
}

struct NetworkInfo {
    var wanIPAddress: String?
    var wanIPv6Address: String?
    var lanIPAddress: String?
    var lanNetmask: String?
}

struct TrafficInfo {
    var currentUpload: Int64?
    var currentDownload: Int64?
    var currentUploadRate: Int64?
    var currentDownloadRate: Int64?
    var totalUpload: Int64?
    var totalDownload: Int64?
    var currentConnectTime: Int64?
}

struct ConnectedDevice: Identifiable {
    let id: String
    var name: String
    var ipAddress: String
    var macAddress: String
    var isActive: Bool?
    var ipv4Address: String?
    var ipv6Address: String?
}

struct DashboardSnapshot {
    var session: EndpointValue<String> = .init()
    var signal: EndpointValue<SignalInfo> = .init()
    var status: EndpointValue<ModemStatus> = .init()
    var operatorInfo: EndpointValue<OperatorInfo> = .init()
    var network: EndpointValue<NetworkInfo> = .init()
    var traffic: EndpointValue<TrafficInfo> = .init()
    var devices: EndpointValue<[ConnectedDevice]> = .init()
}

struct LteBandOption: Identifiable, Hashable {
    let band: Int
    var isActive: Bool
    var source: String

    var id: Int { band }
    var label: String { "B\(band)" }
}

struct NetModeConfig {
    var networkMode: String?
    var networkBand: String?
    var lteBand: String?
}

enum BandLockSupportState {
    case unknown
    case candidate
    case unsupported
}

struct BandLockSupport {
    var state: BandLockSupportState = .unknown
    var canApply: Bool = false
    var endpoint: String?
    var message: String = "Belum dicek."
}

struct BandLockResult {
    var success: Bool
    var requestedBands: [Int]
    var requestedLteBand: String?
    var before: NetModeConfig?
    var after: NetModeConfig?
    var activeBand: String?
    var restored: Bool
    var message: String
}

struct BandInfo {
    var currentConfig: NetModeConfig? = nil
    var networkModeLabel: String = "N/A"
    var activeBand: String?
    var supportedBands: [LteBandOption] = []
    var supportedBandsNotice: String?
    var technicalFields: [InfoField] = []
}

struct BandSnapshot {
    var signal: EndpointValue<SignalInfo> = .init()
    var status: EndpointValue<ModemStatus> = .init()
    var bandInfo: BandInfo = .init()
}

struct InfoField: Identifiable {
    var label: String
    var value: String
    var source: String = ""

    var id: String { "\(source)|\(label)|\(value)" }
}

struct MonthStatistics {
    var monthUpload: Int64?
    var monthDownload: Int64?
    var monthTotal: Int64?
    var monthDuration: Int64?
    var totalUpload: Int64?
    var totalDownload: Int64?
    var totalUsage: Int64?
    var totalDuration: Int64?
}

struct NetworkStatisticsSnapshot {
    var traffic: EndpointValue<TrafficInfo> = .init()
    var month: EndpointValue<MonthStatistics> = .init()
    var status: EndpointValue<ModemStatus> = .init()
}

struct DiagnosisResult {
    var target: String
    var reachable: Bool
    var latencyMS: Int?
    var attempts: Int
    var successCount: Int
    var failureCount: Int
    var method: String
    var message: String
}

struct HuaweiAPIError: LocalizedError {
    let message: String
    let code: String?
    let path: String?

    init(_ message: String, code: String? = nil, path: String? = nil) {
        self.message = message
        self.code = code
        self.path = path
    }

    var errorDescription: String? { message }
}

enum Formatters {
    static func integer(_ text: String?) -> Int64? {
        guard let raw = clean(text) else { return nil }
        return regexCapture("-?\\d+", in: raw).flatMap(Int64.init)
    }

    static func decimal(_ text: String?) -> Double? {
        guard let raw = clean(text) else { return nil }
        let normalized = raw.replacingOccurrences(of: ",", with: ".")
        return regexCapture("-?\\d+(?:\\.\\d+)?", in: normalized).flatMap(Double.init)
    }

    static func bytes(_ value: Int64?) -> String {
        guard let value else { return "N/A" }
        let units = ["B", "KB", "MB", "GB", "TB"]
        var number = Double(max(value, 0))
        var index = 0
        while number >= 1024, index < units.count - 1 {
            number /= 1024
            index += 1
        }
        return index == 0 ? "\(Int(number)) \(units[index])" : String(format: "%.2f %@", number, units[index])
    }

    static func rate(_ value: Int64?) -> String {
        guard let value else { return "N/A" }
        return "\(bytes(value))/s"
    }

    static func duration(_ value: Int64?) -> String {
        guard let value else { return "N/A" }
        let hours = value / 3600
        let minutes = (value % 3600) / 60
        let seconds = value % 60
        if hours > 0 { return "\(hours)j \(minutes)m \(seconds)d" }
        if minutes > 0 { return "\(minutes)m \(seconds)d" }
        return "\(seconds)d"
    }

    static func signal(_ value: Double?, unit: String) -> String {
        guard let value else { return "N/A" }
        return String(format: "%.1f %@", value, unit)
    }

    static func clean(_ value: String?) -> String? {
        guard let value else { return nil }
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty,
              trimmed != "-",
              trimmed != "--",
              !trimmed.caseInsensitiveCompare("N/A").isOrderedSame,
              trimmed != "0.0.0.0",
              trimmed != "::"
        else { return nil }
        return trimmed
    }

    static func connectionLabel(_ raw: String?) -> String {
        switch raw {
        case "900": return "Menghubungkan"
        case "901": return "Terhubung"
        case "902": return "Terputus"
        case "903": return "Memutuskan"
        case "904": return "Gagal terhubung"
        case "905": return "Tidak ada layanan"
        case "906": return "Gangguan koneksi"
        default: return clean(raw) == nil ? "N/A" : "Tidak diketahui"
        }
    }

    static func networkTypeLabel(_ raw: String?) -> String {
        switch raw {
        case "0": return "Tidak ada layanan"
        case "1": return "GSM"
        case "2": return "GPRS"
        case "3": return "EDGE"
        case "4": return "WCDMA"
        case "5": return "HSDPA"
        case "6": return "HSUPA"
        case "7": return "HSPA"
        case "9": return "HSPA+"
        case "19": return "LTE"
        default: return clean(raw) == nil ? "N/A" : "Tidak diketahui"
        }
    }

    static func networkModeLabel(_ raw: String?) -> String {
        switch raw?.trimmingCharacters(in: .whitespacesAndNewlines) {
        case "00": return "Otomatis"
        case "01": return "2G saja"
        case "02": return "3G saja"
        case "03": return "4G/LTE saja"
        default: return "Tidak diketahui"
        }
    }

    static func normalizedBand(_ value: String?) -> String? {
        guard let value = clean(value) else { return nil }
        if let band = regexCapture("(?i)(?:LTE\\s*)?(?:BC|B)?\\s*(\\d{1,3})", in: value, group: 1) {
            return "B\(band)"
        }
        return value
    }

    static func bandNumber(_ value: String?) -> Int? {
        guard let value = clean(value) else { return nil }
        if let match = regexCapture("(?i)(?:LTE\\s*)?(?:BC|B|Band)\\s*(\\d{1,2})", in: value, group: 1) {
            return Int(match)
        }
        if let match = regexCapture("(?i)^B?(\\d{1,2})$", in: value, group: 1) {
            return Int(match)
        }
        return nil
    }

    static func lteBandMask(_ bands: [Int]) -> String {
        var mask: UInt64 = 0
        for band in Set(bands) where (1...64).contains(band) {
            mask |= UInt64(1) << UInt64(band - 1)
        }
        return String(mask, radix: 16).uppercased()
    }

    static func normalizedHex(_ value: String?) -> String? {
        guard var value = clean(value) else { return nil }
        if value.lowercased().hasPrefix("0x") { value.removeFirst(2) }
        let normalized = value.trimmingCharacters(in: CharacterSet(charactersIn: "0")).uppercased()
        return normalized.isEmpty ? "0" : normalized
    }

    private static func regexCapture(_ pattern: String, in value: String, group: Int = 0) -> String? {
        guard let expression = try? NSRegularExpression(pattern: pattern) else { return nil }
        let range = NSRange(value.startIndex..., in: value)
        guard let match = expression.firstMatch(in: value, range: range),
              let capturedRange = Range(match.range(at: group), in: value)
        else { return nil }
        return String(value[capturedRange])
    }
}
