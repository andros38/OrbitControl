package id.orbitcontrol.data

import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest

data class SessionSnapshot(
    val host: String,
    val username: String,
    val hasSessionId: Boolean,
    val hasVerificationToken: Boolean,
)

class HuaweiSessionManager(private val api: HuaweiApiClient) {
    private val mutex = Mutex()
    private val tokens = ArrayDeque<String>()
    private val _authenticated = MutableStateFlow(false)
    val authenticated: StateFlow<Boolean> = _authenticated.asStateFlow()

    private var host = "http://192.168.8.1"
    private var username = "admin"
    private var sessionCookie: String? = null
    private var passwordInMemory: CharArray? = null

    suspend fun testConnection(candidateHost: String): Result<Unit> = runCatching {
        val normalized = api.normalizeHost(candidateHost)
        val primary = api.get(normalized, "/api/webserver/SesTokInfo")
        val node = responseNode(HuaweiXmlParser.parse(primary.body))
        val primaryValid = primary.status in 200..299 && HuaweiXmlParser.errorCode(node) == null &&
            (node?.value("SesInfo", "TokInfo") != null)
        if (!primaryValid) {
            val fallback = api.get(normalized, "/api/webserver/token")
            val fallbackNode = responseNode(HuaweiXmlParser.parse(fallback.body))
            val valid = fallback.status in 200..299 && HuaweiXmlParser.errorCode(fallbackNode) == null &&
                fallbackNode?.value("token", "TokInfo") != null
            if (!valid) throw HuaweiApiException("Modem merespons, tetapi endpoint sesi/token tidak valid.")
        }
    }

    suspend fun login(candidateHost: String, candidateUsername: String, password: String): Result<Unit> = runCatching {
        require(password.isNotEmpty()) { "Password modem wajib diisi." }
        mutex.withLock {
            clearLocked()
            host = api.normalizeHost(candidateHost)
            username = candidateUsername.trim().ifEmpty { "admin" }
            passwordInMemory = password.toCharArray()
            try {
                loginLocked(passwordInMemory!!)
            } catch (error: Throwable) {
                clearLocked()
                throw error
            }
        }
    }.onFailure { _authenticated.value = false }

    suspend fun logout() = mutex.withLock { clearLocked() }

    fun snapshot(): SessionSnapshot = SessionSnapshot(
        host = host,
        username = username,
        hasSessionId = !sessionCookie.isNullOrBlank(),
        hasVerificationToken = tokens.isNotEmpty(),
    )

    suspend fun authenticatedGet(path: String): XmlNode = mutex.withLock {
        checkAuthenticated(path)
        var lastCode: String? = null
        repeat(2) { attempt ->
            val response = api.get(host, path, sessionCookie, mapOf("_ResponseSource" to "Broswer"))
            harvest(response)
            val node = HuaweiXmlParser.parse(response.body)
                ?: throw HuaweiApiException("Respons XML modem tidak valid.", path = path)
            val code = HuaweiXmlParser.errorCode(node)
            if (code != null) {
                lastCode = code
                if (attempt == 0) when (code) {
                    "125001", "125003" -> {
                        refreshTokenLocked()
                        return@repeat
                    }
                    "125002", "100003" -> {
                        reloginLocked()
                        return@repeat
                    }
                }
                if (code in SESSION_ERROR_CODES) invalidateLocked()
                throw apiError(code, path)
            }
            if (response.status !in 200..299) throw HuaweiApiException("Modem mengembalikan HTTP ${response.status}.", path = path)
            return@withLock node
        }
        if (lastCode in SESSION_ERROR_CODES) invalidateLocked()
        throw apiError(lastCode, path)
    }

    suspend fun authenticatedPost(path: String, fields: Map<String, Any?>): XmlNode = mutex.withLock {
        checkAuthenticated(path)
        var lastCode: String? = null
        repeat(3) { attempt ->
            val response = api.postXml(
                host, path, HuaweiXmlParser.buildRequest(fields), sessionCookie, nextTokenLocked(),
            )
            harvest(response)
            val node = HuaweiXmlParser.parse(response.body)
                ?: throw HuaweiApiException("Respons XML modem tidak valid.", path = path)
            val code = HuaweiXmlParser.errorCode(node)
            if (code == null) return@withLock node
            lastCode = code
            when (code) {
                "125001", "125003" -> refreshTokenLocked()
                "125002" -> reloginLocked()
                "100003" -> if (attempt == 0) reloginLocked() else invalidateLocked()
                else -> throw apiError(code, path)
            }
        }
        if (lastCode in SESSION_ERROR_CODES) invalidateLocked()
        throw apiError(lastCode, path)
    }

    private suspend fun loginLocked(password: CharArray) {
        primeSessionLocked()
        val token = nextTokenLocked()
        val passwordType = detectPasswordTypeLocked()
        val passwordText = String(password)
        val passwordField = if (passwordType == "0") {
            sha256HexThenBase64(passwordText)
        } else {
            val first = sha256HexThenBase64(passwordText)
            sha256HexThenBase64(username + first + token)
        }
        val xml = HuaweiXmlParser.buildRequest(
            linkedMapOf("Username" to username, "Password" to passwordField, "password_type" to passwordType),
        )
        val response = api.postXml(host, "/api/user/login", xml, sessionCookie, token)
        harvest(response)
        val root = HuaweiXmlParser.parse(response.body)
        val code = HuaweiXmlParser.errorCode(root)
        if (code != null) throw apiError(code, "/api/user/login")
        if (root?.name?.equals("response", true) != true || !root.text.equals("OK", true)) {
            throw HuaweiApiException("Login ditolak: respons modem tidak menyatakan OK.", path = "/api/user/login")
        }

        // Firmware B312/operator tidak konsisten dalam arti field State pada
        // /api/user/state-login. Beberapa unit tetap mengembalikan State=0
        // setelah /api/user/login membalas OK. Repo legacy juga memperlakukan
        // endpoint ini sebagai best-effort (hanya untuk info RSA), bukan bukti
        // autentikasi. Validasi sesi dilakukan lewat endpoint monitoring di bawah.
        runCatching {
            val stateResponse = api.get(host, "/api/user/state-login", sessionCookie, mapOf("_ResponseSource" to "Broswer"))
            harvest(stateResponse)
        }

        val verify = api.get(host, "/api/monitoring/status", sessionCookie, mapOf("_ResponseSource" to "Broswer"))
        harvest(verify)
        val verifyNode = HuaweiXmlParser.parse(verify.body)
        val verifyCode = HuaweiXmlParser.errorCode(verifyNode)
        if (verifyCode != null || verifyNode?.name?.equals("response", true) != true) {
            throw apiError(verifyCode ?: "100003", "/api/monitoring/status")
        }
        if (tokens.isEmpty()) refreshTokenLocked()
        if (tokens.isEmpty()) throw HuaweiApiException("Login berhasil, tetapi token sesi lanjutan tidak tersedia.")
        _authenticated.value = true
    }

    private suspend fun primeSessionLocked() {
        tokens.clear()
        sessionCookie = null
        val sesTok = api.get(host, "/api/webserver/SesTokInfo")
        harvest(sesTok)
        val node = responseNode(HuaweiXmlParser.parse(sesTok.body))
        node?.value("SesInfo")?.let { sessionCookie = it.substringBefore(';') }
        node?.value("TokInfo")?.let { addTokenCandidates(it, false) }
        if (tokens.isEmpty()) refreshTokenLocked()
        if (tokens.isEmpty()) throw HuaweiApiException("Token awal modem tidak tersedia.", path = "/api/webserver/SesTokInfo")
    }

    private suspend fun refreshTokenLocked() {
        tokens.clear()
        val response = api.get(host, "/api/webserver/token", sessionCookie)
        harvest(response)
        val node = responseNode(HuaweiXmlParser.parse(response.body))
        node?.value("token", "TokInfo")?.let { addTokenCandidates(it, true) }
        if (tokens.isEmpty()) {
            val fallback = api.get(host, "/api/webserver/SesTokInfo", sessionCookie)
            harvest(fallback)
            val fallbackNode = responseNode(HuaweiXmlParser.parse(fallback.body))
            fallbackNode?.value("SesInfo")?.let { sessionCookie = it.substringBefore(';') }
            fallbackNode?.value("TokInfo")?.let { addTokenCandidates(it, false) }
        }
    }

    private suspend fun detectPasswordTypeLocked(): String {
        val response = runCatching { api.get(host, "/api/user/state-login", sessionCookie) }.getOrNull() ?: return "4"
        harvest(response)
        return if (responseNode(HuaweiXmlParser.parse(response.body))?.value("password_type", "passwordtype") == "0") "0" else "4"
    }

    private suspend fun reloginLocked() {
        val password = passwordInMemory ?: run {
            invalidateLocked()
            throw HuaweiApiException("Sesi berakhir. Silakan login ulang.", code = "100003")
        }
        try {
            // Pertahankan shell/navigasi selama re-auth internal. Status menjadi
            // false hanya jika autentikasi ulang benar-benar gagal.
            loginLocked(password)
        } catch (error: Throwable) {
            clearLocked()
            throw error
        }
    }

    private fun harvest(response: HuaweiRawResponse) {
        response.headers.values("Set-Cookie").firstOrNull()?.substringBefore(';')
            ?.takeIf { it.contains('=') }?.let { sessionCookie = it }
        response.headers.names().forEach { name ->
            if (name.equals("__RequestVerificationToken", true) ||
                name.equals("__RequestVerificationTokenone", true) ||
                name.equals("__RequestVerificationTokentwo", true)
            ) response.headers.values(name).forEach { addTokenCandidates(it, false) }
        }
    }

    private fun addTokenCandidates(rawValue: String, webserverToken: Boolean) {
        val raw = rawValue.trim()
        val candidates = when {
            webserverToken && raw.length >= 64 -> listOf(raw.substring(32, 64), raw.substring(0, 32))
            raw.length >= 64 -> listOf(raw.substring(0, 32), raw.substring(32, 64), raw)
            raw.length > 32 -> listOf(raw.take(32), raw)
            else -> listOf(raw)
        }
        candidates.filter(String::isNotBlank).forEach { if (it !in tokens) tokens.addLast(it) }
    }

    private suspend fun nextTokenLocked(): String {
        if (tokens.isEmpty()) refreshTokenLocked()
        return tokens.removeFirstOrNull() ?: throw HuaweiApiException("Token verifikasi modem tidak tersedia.")
    }

    private fun responseNode(root: XmlNode?): XmlNode? = when {
        root == null -> null
        root.name.equals("response", true) -> root
        else -> root.child("response")
    }

    private fun checkAuthenticated(path: String) {
        if (!_authenticated.value) throw HuaweiApiException("Sesi berakhir. Silakan login ulang.", code = "100003", path = path)
    }

    private fun invalidateLocked() {
        _authenticated.value = false
        tokens.clear()
        sessionCookie = null
    }

    private fun clearLocked() {
        invalidateLocked()
        passwordInMemory?.fill('\u0000')
        passwordInMemory = null
    }

    private fun apiError(code: String?, path: String): HuaweiApiException {
        val message = when (code) {
            "100002" -> "Endpoint tidak didukung oleh firmware modem."
            "100003" -> "Sesi tidak memiliki hak akses. Silakan login ulang."
            "-1" -> "Endpoint tidak tersedia atau tidak dapat dibaca pada firmware ini."
            "108001" -> "Username modem salah."
            "108002" -> "Password modem salah."
            "108003" -> "Modem melaporkan sesi lain masih login."
            "108006" -> "Username atau password modem salah."
            "125001", "125002", "125003" -> "Token atau sesi modem tidak valid."
            null -> "Permintaan modem gagal."
            else -> "Modem menolak permintaan (kode $code)."
        }
        return HuaweiApiException(message, code, path)
    }

    private fun sha256HexThenBase64(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { "%02x".format(it) }
        return Base64.encodeToString(hex.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    companion object {
        private val TOKEN_ERROR_CODES = setOf("125001", "125002", "125003")
        private val SESSION_ERROR_CODES = setOf("100003", "125001", "125002", "125003")
    }
}
