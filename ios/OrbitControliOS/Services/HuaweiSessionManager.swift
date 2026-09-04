import CryptoKit
import Foundation

struct HuaweiRawResponse {
    let status: Int
    let body: String
    let headers: [String: String]
    let url: URL
}

final class HuaweiAPIClient {
    private let session: URLSession

    init() {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.timeoutIntervalForRequest = 12
        configuration.timeoutIntervalForResource = 20
        configuration.httpShouldSetCookies = false
        self.session = URLSession(configuration: configuration)
    }

    func normalizeHost(_ input: String) throws -> URL {
        let trimmed = input.trimmingCharacters(in: .whitespacesAndNewlines)
        let candidate = trimmed.isEmpty ? "http://192.168.8.1" : trimmed
        let qualified = candidate.contains("://") ? candidate : "http://\(candidate)"
        guard var components = URLComponents(string: qualified),
              let scheme = components.scheme?.lowercased(),
              scheme == "http" || scheme == "https",
              components.host != nil
        else {
            throw HuaweiAPIError("Alamat modem tidak valid. Gunakan HTTP atau HTTPS.")
        }
        components.path = ""
        components.query = nil
        components.fragment = nil
        guard let url = components.url else {
            throw HuaweiAPIError("Alamat modem tidak valid.")
        }
        return url
    }

    func get(
        host: URL,
        path: String,
        cookie: String? = nil,
        headers: [String: String] = [:]
    ) async throws -> HuaweiRawResponse {
        try await execute(host: host, path: path, method: "GET", body: nil, cookie: cookie, headers: headers)
    }

    func postXML(
        host: URL,
        path: String,
        xml: String,
        cookie: String?,
        token: String,
        headers: [String: String] = [:]
    ) async throws -> HuaweiRawResponse {
        var merged = [
            "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
            "__RequestVerificationToken": token,
            "_ResponseSource": "Broswer",
        ]
        headers.forEach { merged[$0.key] = $0.value }
        return try await execute(host: host, path: path, method: "POST", body: xml, cookie: cookie, headers: merged)
    }

    private func execute(
        host: URL,
        path: String,
        method: String,
        body: String?,
        cookie: String?,
        headers: [String: String]
    ) async throws -> HuaweiRawResponse {
        let normalized = try normalizeHost(host.absoluteString)
        let safePath = path.hasPrefix("/") ? path : "/\(path)"
        guard let url = URL(string: safePath, relativeTo: normalized)?.absoluteURL else {
            throw HuaweiAPIError("Path API modem tidak valid.", path: path)
        }

        var request = URLRequest(url: url)
        request.httpMethod = method
        request.setValue("application/xml, text/xml, */*", forHTTPHeaderField: "Accept")
        request.setValue("keep-alive", forHTTPHeaderField: "Connection")
        cookie.flatMap(Formatters.clean).map { request.setValue($0, forHTTPHeaderField: "Cookie") }
        headers.forEach { request.setValue($0.value, forHTTPHeaderField: $0.key) }
        if method == "POST" {
            request.httpBody = Data((body ?? "").utf8)
        }

        do {
            let (data, response) = try await session.data(for: request)
            guard let http = response as? HTTPURLResponse else {
                throw HuaweiAPIError("Respons modem tidak valid.", path: path)
            }
            var responseHeaders: [String: String] = [:]
            for (key, value) in http.allHeaderFields {
                responseHeaders[String(describing: key)] = String(describing: value)
            }
            return HuaweiRawResponse(
                status: http.statusCode,
                body: String(decoding: data, as: UTF8.self),
                headers: responseHeaders,
                url: url
            )
        } catch let error as HuaweiAPIError {
            throw error
        } catch {
            throw HuaweiAPIError("Tidak dapat menghubungi modem di \(normalized.absoluteString).", path: path)
        }
    }
}

actor HuaweiSessionManager {
    private let api = HuaweiAPIClient()
    private var tokens: [String] = []
    private var host = URL(string: "http://192.168.8.1")!
    private var username = "admin"
    private var sessionCookie: String?
    private var passwordInMemory: String?
    private var authenticated = false

    func testConnection(host candidateHost: String) async throws {
        let normalized = try api.normalizeHost(candidateHost)
        let primary = try await api.get(host: normalized, path: "/api/webserver/SesTokInfo")
        let primaryNode = HuaweiXML.responseNode(HuaweiXML.parse(primary.body))
        let primaryValid = (200...299).contains(primary.status)
            && HuaweiXML.errorCode(primaryNode) == nil
            && (primaryNode?.firstValue("SesInfo", "TokInfo") != nil)

        guard !primaryValid else { return }

        let fallback = try await api.get(host: normalized, path: "/api/webserver/token")
        let fallbackNode = HuaweiXML.responseNode(HuaweiXML.parse(fallback.body))
        let fallbackValid = (200...299).contains(fallback.status)
            && HuaweiXML.errorCode(fallbackNode) == nil
            && (fallbackNode?.firstValue("token", "TokInfo") != nil)
        guard fallbackValid else {
            throw HuaweiAPIError("Modem merespons, tetapi endpoint sesi/token tidak valid.")
        }
    }

    func login(host candidateHost: String, username candidateUsername: String, password: String) async throws {
        guard !password.isEmpty else { throw HuaweiAPIError("Password modem wajib diisi.") }
        clearLocked()
        host = try api.normalizeHost(candidateHost)
        username = candidateUsername.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "admin" : candidateUsername.trimmingCharacters(in: .whitespacesAndNewlines)
        passwordInMemory = password

        do {
            try await loginLocked()
        } catch {
            clearLocked()
            throw error
        }
    }

    func logout() {
        clearLocked()
    }

    func snapshot() -> SessionSnapshot {
        SessionSnapshot(
            host: host.absoluteString.trimmingCharacters(in: CharacterSet(charactersIn: "/")),
            username: username,
            hasSessionID: Formatters.clean(sessionCookie) != nil,
            hasVerificationToken: !tokens.isEmpty
        )
    }

    func isAuthenticated() -> Bool {
        authenticated
    }

    func authenticatedGet(_ path: String) async throws -> XMLNode {
        try checkAuthenticated(path)
        var lastCode: String?

        for attempt in 0..<2 {
            let response = try await api.get(
                host: host,
                path: path,
                cookie: sessionCookie,
                headers: ["_ResponseSource": "Broswer"]
            )
            harvest(response)
            guard let root = HuaweiXML.parse(response.body) else {
                throw HuaweiAPIError("Respons XML modem tidak valid.", path: path)
            }
            let code = HuaweiXML.errorCode(root)
            if let code {
                lastCode = code
                if attempt == 0 {
                    if code == "125001" || code == "125003" {
                        try await refreshTokenLocked()
                        continue
                    }
                    if code == "125002" || code == "100003" {
                        try await reloginLocked()
                        continue
                    }
                }
                if sessionErrorCodes.contains(code) { invalidateLocked() }
                throw apiError(code: code, path: path)
            }
            guard (200...299).contains(response.status) else {
                throw HuaweiAPIError("Modem mengembalikan HTTP \(response.status).", path: path)
            }
            return root
        }

        if let lastCode, sessionErrorCodes.contains(lastCode) { invalidateLocked() }
        throw apiError(code: lastCode, path: path)
    }

    func authenticatedPost(_ path: String, fields: [String: String]) async throws -> XMLNode {
        try checkAuthenticated(path)
        var lastCode: String?

        for attempt in 0..<3 {
            let token = try await nextTokenLocked()
            let response = try await api.postXML(
                host: host,
                path: path,
                xml: HuaweiXML.request(fields),
                cookie: sessionCookie,
                token: token
            )
            harvest(response)
            guard let root = HuaweiXML.parse(response.body) else {
                throw HuaweiAPIError("Respons XML modem tidak valid.", path: path)
            }
            guard let code = HuaweiXML.errorCode(root) else { return root }

            lastCode = code
            switch code {
            case "125001", "125003":
                try await refreshTokenLocked()
            case "125002":
                try await reloginLocked()
            case "100003":
                if attempt == 0 {
                    try await reloginLocked()
                } else {
                    invalidateLocked()
                }
            default:
                throw apiError(code: code, path: path)
            }
        }

        if let lastCode, sessionErrorCodes.contains(lastCode) { invalidateLocked() }
        throw apiError(code: lastCode, path: path)
    }

    private func loginLocked() async throws {
        try await primeSessionLocked()
        let token = try await nextTokenLocked()
        let passwordType = try await detectPasswordTypeLocked()
        guard let password = passwordInMemory else {
            throw HuaweiAPIError("Password modem tidak tersedia di memori.")
        }

        let passwordField: String
        if passwordType == "0" {
            passwordField = sha256HexThenBase64(password)
        } else {
            let first = sha256HexThenBase64(password)
            passwordField = sha256HexThenBase64(username + first + token)
        }

        let response = try await api.postXML(
            host: host,
            path: "/api/user/login",
            xml: HuaweiXML.request([
                "Username": username,
                "Password": passwordField,
                "password_type": passwordType,
            ]),
            cookie: sessionCookie,
            token: token
        )
        harvest(response)

        let root = HuaweiXML.parse(response.body)
        if let code = HuaweiXML.errorCode(root) {
            throw apiError(code: code, path: "/api/user/login")
        }
        guard root?.name.caseInsensitiveCompare("response") == .orderedSame,
              root?.text.trimmingCharacters(in: .whitespacesAndNewlines).caseInsensitiveCompare("OK") == .orderedSame
        else {
            throw HuaweiAPIError("Login ditolak: respons modem tidak menyatakan OK.", path: "/api/user/login")
        }

        _ = try? await api.get(host: host, path: "/api/user/state-login", cookie: sessionCookie, headers: ["_ResponseSource": "Broswer"])

        let verify = try await api.get(host: host, path: "/api/monitoring/status", cookie: sessionCookie, headers: ["_ResponseSource": "Broswer"])
        harvest(verify)
        let verifyNode = HuaweiXML.parse(verify.body)
        guard HuaweiXML.errorCode(verifyNode) == nil,
              verifyNode?.name.caseInsensitiveCompare("response") == .orderedSame
        else {
            throw apiError(code: HuaweiXML.errorCode(verifyNode) ?? "100003", path: "/api/monitoring/status")
        }

        if tokens.isEmpty { try await refreshTokenLocked() }
        guard !tokens.isEmpty else {
            throw HuaweiAPIError("Login berhasil, tetapi token sesi lanjutan tidak tersedia.")
        }
        authenticated = true
    }

    private func primeSessionLocked() async throws {
        tokens.removeAll()
        sessionCookie = nil

        let response = try await api.get(host: host, path: "/api/webserver/SesTokInfo")
        harvest(response)
        let node = HuaweiXML.responseNode(HuaweiXML.parse(response.body))
        if let session = node?.firstValue("SesInfo") {
            sessionCookie = session.components(separatedBy: ";").first
        }
        if let token = node?.firstValue("TokInfo") {
            addTokenCandidates(token, webserverToken: false)
        }
        if tokens.isEmpty { try await refreshTokenLocked() }
        guard !tokens.isEmpty else {
            throw HuaweiAPIError("Token awal modem tidak tersedia.", path: "/api/webserver/SesTokInfo")
        }
    }

    private func refreshTokenLocked() async throws {
        tokens.removeAll()
        let response = try await api.get(host: host, path: "/api/webserver/token", cookie: sessionCookie)
        harvest(response)
        let node = HuaweiXML.responseNode(HuaweiXML.parse(response.body))
        if let token = node?.firstValue("token", "TokInfo") {
            addTokenCandidates(token, webserverToken: true)
        }

        if tokens.isEmpty {
            let fallback = try await api.get(host: host, path: "/api/webserver/SesTokInfo", cookie: sessionCookie)
            harvest(fallback)
            let fallbackNode = HuaweiXML.responseNode(HuaweiXML.parse(fallback.body))
            if let session = fallbackNode?.firstValue("SesInfo") {
                sessionCookie = session.components(separatedBy: ";").first
            }
            if let token = fallbackNode?.firstValue("TokInfo") {
                addTokenCandidates(token, webserverToken: false)
            }
        }
    }

    private func detectPasswordTypeLocked() async throws -> String {
        guard let response = try? await api.get(host: host, path: "/api/user/state-login", cookie: sessionCookie) else {
            return "4"
        }
        harvest(response)
        let node = HuaweiXML.responseNode(HuaweiXML.parse(response.body))
        return node?.firstValue("password_type", "passwordtype") == "0" ? "0" : "4"
    }

    private func reloginLocked() async throws {
        guard passwordInMemory != nil else {
            invalidateLocked()
            throw HuaweiAPIError("Sesi berakhir. Silakan login ulang.", code: "100003")
        }
        do {
            try await loginLocked()
        } catch {
            clearLocked()
            throw error
        }
    }

    private func harvest(_ response: HuaweiRawResponse) {
        let cookies = HTTPCookie.cookies(withResponseHeaderFields: response.headers, for: response.url)
        if let session = cookies.first(where: { $0.name.caseInsensitiveCompare("SessionID") == .orderedSame }) ?? cookies.first {
            sessionCookie = "\(session.name)=\(session.value)"
        }

        for (name, value) in response.headers {
            let normalized = name.lowercased()
            if normalized == "__requestverificationtoken"
                || normalized == "__requestverificationtokenone"
                || normalized == "__requestverificationtokentwo" {
                addTokenCandidates(value, webserverToken: false)
            }
        }
    }

    private func addTokenCandidates(_ rawValue: String, webserverToken: Bool) {
        let raw = rawValue.trimmingCharacters(in: .whitespacesAndNewlines)
        let candidates: [String]
        if webserverToken && raw.count >= 64 {
            let start = raw.index(raw.startIndex, offsetBy: 32)
            let middle = String(raw[start...].prefix(32))
            candidates = [middle, String(raw.prefix(32))]
        } else if raw.count >= 64 {
            let start = raw.index(raw.startIndex, offsetBy: 32)
            candidates = [String(raw.prefix(32)), String(raw[start...].prefix(32)), raw]
        } else if raw.count > 32 {
            candidates = [String(raw.prefix(32)), raw]
        } else {
            candidates = [raw]
        }

        for candidate in candidates where Formatters.clean(candidate) != nil && !tokens.contains(candidate) {
            tokens.append(candidate)
        }
    }

    private func nextTokenLocked() async throws -> String {
        if tokens.isEmpty { try await refreshTokenLocked() }
        guard !tokens.isEmpty else {
            throw HuaweiAPIError("Token verifikasi modem tidak tersedia.")
        }
        return tokens.removeFirst()
    }

    private func checkAuthenticated(_ path: String) throws {
        guard authenticated else {
            throw HuaweiAPIError("Sesi berakhir. Silakan login ulang.", code: "100003", path: path)
        }
    }

    private func invalidateLocked() {
        authenticated = false
        tokens.removeAll()
        sessionCookie = nil
    }

    private func clearLocked() {
        invalidateLocked()
        passwordInMemory = nil
    }

    private func apiError(code: String?, path: String) -> HuaweiAPIError {
        let message: String
        switch code {
        case "100002": message = "Endpoint tidak didukung oleh firmware modem."
        case "100003": message = "Sesi tidak memiliki hak akses. Silakan login ulang."
        case "-1": message = "Endpoint tidak tersedia atau tidak dapat dibaca pada firmware ini."
        case "108001": message = "Username modem salah."
        case "108002": message = "Password modem salah."
        case "108003": message = "Modem melaporkan sesi lain masih login."
        case "108006": message = "Username atau password modem salah."
        case "125001", "125002", "125003": message = "Token atau sesi modem tidak valid."
        case nil: message = "Permintaan modem gagal."
        default: message = "Modem menolak permintaan (kode \(code!))."
        }
        return HuaweiAPIError(message, code: code, path: path)
    }

    private func sha256HexThenBase64(_ value: String) -> String {
        let digest = SHA256.hash(data: Data(value.utf8))
        let hex = digest.map { String(format: "%02x", $0) }.joined()
        return Data(hex.utf8).base64EncodedString()
    }

    private let sessionErrorCodes: Set<String> = ["100003", "125001", "125002", "125003"]
}
