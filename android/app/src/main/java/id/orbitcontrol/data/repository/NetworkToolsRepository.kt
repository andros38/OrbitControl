package id.orbitcontrol.data.repository

import id.orbitcontrol.data.HuaweiApiException
import id.orbitcontrol.data.HuaweiSessionManager
import id.orbitcontrol.data.XmlNode
import id.orbitcontrol.domain.EndpointValue
import id.orbitcontrol.domain.Formatters
import id.orbitcontrol.domain.ModemStatus
import id.orbitcontrol.domain.SignalInfo
import id.orbitcontrol.domain.TrafficInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.math.BigInteger
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

/** Local HiLink operations used by the modem-management screens. */
class NetworkToolsRepository(
    private val session: HuaweiSessionManager,
) {
    private val signalRepository = SignalRepository(session)
    private val statusRepository = StatusRepository(session)
    private val trafficRepository = TrafficRepository(session)
    private val publicHttp = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(18, TimeUnit.SECONDS)
        .writeTimeout(18, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun getBandSnapshot(): NetworkBandSnapshot {
        val signal = endpoint { signalRepository.getSignal() }
        val status = endpoint { statusRepository.getStatus() }
        val netMode = readEndpoint("/api/net/net-mode", "Mode jaringan")
        val netModeList = readEndpoint("/api/net/net-mode-list", "Daftar mode jaringan")
        val lteBandInfo = readEndpoint("/api/net/lte-band-info", "Info band LTE")
        val bandInfo = buildBandInfo(signal.value, status.value, netMode, netModeList, lteBandInfo)
        return NetworkBandSnapshot(
            signal = signal,
            status = status,
            netMode = netMode,
            netModeList = netModeList,
            lteBandInfo = lteBandInfo,
            bandInfo = bandInfo,
        )
    }

    suspend fun checkBandLockSupport(): BandLockSupport {
        val snapshot = getBandSnapshot()
        val current = snapshot.bandInfo.currentConfig
        val error = snapshot.netMode.error ?: snapshot.netModeList.error

        return when {
            snapshot.netMode.error != null -> BandLockSupport(
                state = BandLockSupportState.UNSUPPORTED,
                canApply = false,
                endpoint = BAND_LOCK_ENDPOINT,
                message = "Band lock tidak didukung firmware ini. ${snapshot.netMode.error}",
            )
            current == null || current.networkMode.isNullOrBlank() || current.networkBand.isNullOrBlank() || current.lteBand.isNullOrBlank() -> BandLockSupport(
                state = BandLockSupportState.UNSUPPORTED,
                canApply = false,
                endpoint = BAND_LOCK_ENDPOINT,
                message = "Band lock tidak didukung firmware ini. Konfigurasi NetworkMode/NetworkBand/LTEBand tidak lengkap.",
            )
            snapshot.bandInfo.supportedBands.isEmpty() -> BandLockSupport(
                state = BandLockSupportState.UNSUPPORTED,
                canApply = false,
                endpoint = BAND_LOCK_ENDPOINT,
                message = "Band lock tidak didukung firmware ini. Daftar band LTE tidak dapat dibaca.",
            )
            else -> BandLockSupport(
                state = BandLockSupportState.POST_CANDIDATE,
                canApply = true,
                endpoint = BAND_LOCK_ENDPOINT,
                message = buildString {
                    append("Endpoint kandidat band lock tersedia: POST $BAND_LOCK_ENDPOINT. ")
                    append("Payload mengikuti pola legacy Huawei: NetworkMode + NetworkBand aktif, lalu LTEBand target. ")
                    if (error != null) append("Sebagian endpoint opsional tidak tersedia: $error")
                },
            )
        }
    }

    suspend fun applyBandLock(targetBands: Set<Int>): BandLockResult {
        val bands = targetBands.filter { it in 1..64 }.distinct().sorted()
        if (bands.isEmpty()) {
            return BandLockResult(
                success = false,
                requestedBands = emptyList(),
                requestedLteBand = null,
                message = "Pilih minimal satu band LTE.",
            )
        }

        val before = parseNetModeConfig(session.authenticatedGet(BAND_LOCK_ENDPOINT).response())
        if (before.networkMode.isNullOrBlank() || before.networkBand.isNullOrBlank() || before.lteBand.isNullOrBlank()) {
            return BandLockResult(
                success = false,
                requestedBands = bands,
                requestedLteBand = null,
                before = before,
                message = "Konfigurasi modem tidak lengkap. Band lock dibatalkan.",
            )
        }

        val targetHex = lteBandHex(bands)
        session.authenticatedPost(
            BAND_LOCK_ENDPOINT,
            linkedMapOf(
                "NetworkMode" to before.networkMode,
                "NetworkBand" to before.networkBand,
                "LTEBand" to targetHex,
            ),
        )
        delay(2_000)

        val after = parseNetModeConfig(session.authenticatedGet(BAND_LOCK_ENDPOINT).response())
        val afterSignal = runCatching { signalRepository.getSignal() }.getOrNull()
        val configMatches = normalizeHex(after.lteBand) == normalizeHex(targetHex)
        val activeBand = bandNumber(afterSignal?.band)
        val signalMatches = activeBand != null && activeBand in bands

        if (configMatches || signalMatches) {
            return BandLockResult(
                success = true,
                requestedBands = bands,
                requestedLteBand = targetHex,
                before = before,
                after = after,
                activeBand = afterSignal?.band,
                message = "Band lock terverifikasi dari pembacaan ulang modem.",
            )
        }

        val restored = runCatching {
            session.authenticatedPost(
                BAND_LOCK_ENDPOINT,
                linkedMapOf(
                    "NetworkMode" to before.networkMode,
                    "NetworkBand" to before.networkBand,
                    "LTEBand" to before.lteBand,
                ),
            )
            delay(1_000)
        }.isSuccess

        return BandLockResult(
            success = false,
            requestedBands = bands,
            requestedLteBand = targetHex,
            before = before,
            after = after,
            activeBand = afterSignal?.band,
            restored = restored,
            message = if (restored) {
                "POST diterima, tetapi hasil tidak sesuai setelah GET ulang. Status sebelumnya sudah dicoba dikembalikan."
            } else {
                "POST diterima, tetapi hasil tidak sesuai setelah GET ulang. Pengembalian status sebelumnya gagal; cek modem secara manual."
            },
        )
    }

    suspend fun getStatisticsSnapshot(): NetworkStatisticsSnapshot = NetworkStatisticsSnapshot(
        traffic = endpoint { trafficRepository.getTraffic() },
        month = endpoint { getMonthStatistics() },
        status = endpoint { statusRepository.getStatus() },
    )

    suspend fun runDiagnosis(target: String, attempts: Int = 3): DiagnosisResult = withContext(Dispatchers.IO) {
        val safeTarget = target.trim().ifBlank { "1.1.1.1" }
        val urls = latencyUrls(safeTarget)
        val latencies = mutableListOf<Long>()
        val failures = mutableListOf<String>()

        repeat(attempts.coerceAtLeast(1)) {
            val elapsed = measureTimeMillis {
                val result = runCatching { requestAny(urls) }
                if (result.getOrDefault(false)) {
                    latencies += 0L
                } else {
                    failures += result.exceptionOrNull()?.message.orEmpty().ifBlank { "Target tidak merespons." }
                }
            }
            if (latencies.isNotEmpty() && latencies.last() == 0L) latencies[latencies.lastIndex] = elapsed
        }

        DiagnosisResult(
            target = safeTarget,
            reachable = latencies.isNotEmpty(),
            latencyMs = latencies.takeIf { it.isNotEmpty() }?.average()?.toLong(),
            attempts = attempts.coerceAtLeast(1),
            successCount = latencies.size,
            failureCount = attempts.coerceAtLeast(1) - latencies.size,
            method = "HTTP latency fallback",
            message = if (latencies.isNotEmpty()) {
                "Target merespons lewat request HTTP/HTTPS ringan."
            } else {
                failures.firstOrNull() ?: "Target tidak dapat dijangkau."
            },
        )
    }

    private suspend fun getMonthStatistics(): MonthStatistics {
        val node = session.authenticatedGet("/api/monitoring/month_statistics").response()
        val monthUpload = node.longValue("CurrentMonthUpload", "MonthUpload", "MonthUploadTraffic")
        val monthDownload = node.longValue("CurrentMonthDownload", "MonthDownload", "MonthDownloadTraffic")
        val totalUpload = node.longValue("TotalUpload", "TotalUploadTraffic", "AllUpload")
        val totalDownload = node.longValue("TotalDownload", "TotalDownloadTraffic", "AllDownload")
        return MonthStatistics(
            monthUpload = monthUpload,
            monthDownload = monthDownload,
            monthTotal = node.longValue("CurrentMonthTotal", "MonthTotal", "CurrentMonthTraffic", "MonthTraffic")
                ?: sumOrNull(monthUpload, monthDownload),
            monthDuration = node.longValue("MonthDuration", "CurrentMonthDuration", "MonthConnectTime"),
            totalUpload = totalUpload,
            totalDownload = totalDownload,
            totalUsage = node.longValue("TotalTraffic", "TotalData", "AllTraffic", "TotalTrafficData")
                ?: sumOrNull(totalUpload, totalDownload),
            totalDuration = node.longValue("TotalConnectTime", "TotalDuration", "AllDuration", "TotalConnectDuration"),
        )
    }

    private suspend fun readEndpoint(path: String, title: String): EndpointValue<ReadOnlyEndpoint> =
        endpoint {
            val node = session.authenticatedGet(path).response()
            ReadOnlyEndpoint(
                title = title,
                path = path,
                fields = leafRows(node).ifEmpty { listOf(InfoField("Status", "Endpoint tersedia, tetapi respons kosong.", "status")) },
                rawXml = node.toXmlString(),
            )
        }

    private suspend fun <T> endpoint(block: suspend () -> T): EndpointValue<T> = runCatching {
        EndpointValue(value = block())
    }.getOrElse { error ->
        EndpointValue(error = endpointError(error))
    }

    private fun buildBandInfo(
        signal: SignalInfo?,
        status: ModemStatus?,
        netMode: EndpointValue<ReadOnlyEndpoint>,
        netModeList: EndpointValue<ReadOnlyEndpoint>,
        lteBandInfo: EndpointValue<ReadOnlyEndpoint>,
    ): BandUiInfo {
        val current = parseNetModeConfig(netMode.value)
        val activeBandNumber = bandNumber(signal?.band)
        val parsedSupportedBands = netModeList.value?.rawXml
            ?.let(::parseSupportedLteBandsFromNetModeList)
            .orEmpty()
        val usingFallback = parsedSupportedBands.isEmpty()
        val supportedBands = (if (usingFallback) fallbackB312Bands() else parsedSupportedBands)
            .map { it.copy(isActive = it.band == activeBandNumber) }
        return BandUiInfo(
            currentConfig = current,
            currentNetworkMode = current?.networkMode,
            currentNetworkModeLabel = current?.networkMode?.let(::networkModeLabel)
                ?: status?.networkType?.let(::networkTypeLabel)
                ?: "N/A",
            currentNetworkBand = current?.networkBand,
            currentLteBand = current?.lteBand,
            activeBand = signal?.band ?: activeBandNumber?.let { "B$it" },
            activeBandNumber = activeBandNumber,
            supportedBands = supportedBands,
            supportedBandsNotice = if (usingFallback) FALLBACK_BAND_NOTICE else null,
            technicalFields = technicalFields(
                "/api/net/net-mode" to netMode,
                "/api/net/net-mode-list" to netModeList,
                "/api/net/lte-band-info" to lteBandInfo,
            ),
        )
    }

    private fun parseNetModeConfig(endpoint: ReadOnlyEndpoint?): NetModeConfig? {
        if (endpoint == null) return null
        return NetModeConfig(
            networkMode = endpoint.firstRawValue("NetworkMode", "networkmode"),
            networkBand = endpoint.firstRawValue("NetworkBand", "networkband"),
            lteBand = endpoint.firstRawValue("LTEBand", "lteband", "LTE_BAND"),
        )
    }

    private fun parseNetModeConfig(node: XmlNode): NetModeConfig = NetModeConfig(
        networkMode = node.firstValue("NetworkMode", "networkmode").cleanText(),
        networkBand = node.firstValue("NetworkBand", "networkband").cleanText(),
        lteBand = node.firstValue("LTEBand", "lteband", "LTE_BAND").cleanText(),
    )

    private fun fallbackB312Bands(): List<LteBandOption> =
        B312_FALLBACK_BANDS.map { band -> LteBandOption(band = band, source = "fallback:B312") }

    private fun technicalFields(vararg endpoints: Pair<String, EndpointValue<ReadOnlyEndpoint>>): List<InfoField> =
        endpoints.flatMap { (path, endpoint) ->
            val data = endpoint.value
            when {
                data != null -> data.fields.map { field ->
                    InfoField(
                        label = "${data.title}: ${field.rawName}",
                        value = field.value,
                        rawName = field.rawName,
                        source = data.path,
                    )
                } + if (data.path == "/api/net/net-mode-list") {
                    listOf(InfoField("raw net-mode-list", data.rawXml, "raw-net-mode-list", data.path))
                } else {
                    emptyList()
                }
                endpoint.error != null -> listOf(InfoField("Endpoint gagal", endpoint.error, "error", path))
                else -> emptyList()
            }
        }

    private fun endpointError(error: Throwable): String = when {
        error is HuaweiApiException && error.code == "-1" -> "Endpoint tidak tersedia atau tidak dapat dibaca pada firmware ini."
        error is HuaweiApiException && error.code == "100002" -> "Endpoint tidak didukung firmware."
        error is HuaweiApiException && error.code == "100003" -> "Sesi modem perlu login ulang."
        error.message?.contains("tidak tersedia", ignoreCase = true) == true -> "Endpoint tidak tersedia atau tidak dapat dibaca pada firmware ini."
        error.message?.contains("tidak didukung", ignoreCase = true) == true -> "Endpoint tidak didukung firmware."
        error.message?.contains("sesi", ignoreCase = true) == true -> "Sesi modem perlu login ulang."
        error.message?.contains("menghubungi", ignoreCase = true) == true -> "Modem tidak dapat dijangkau."
        else -> error.message ?: "Data belum tersedia."
    }

    private fun leafRows(node: XmlNode, prefix: String = ""): List<InfoField> {
        val path = when {
            prefix.isBlank() && node.name.equals("response", true) -> ""
            prefix.isBlank() -> node.name
            else -> "$prefix.${node.name}"
        }
        if (node.children.isEmpty()) {
            return node.text.cleanText()?.let {
                val rawName = path.substringAfterLast('.').ifBlank { node.name }
                listOf(InfoField(friendlyLabel(rawName), it, rawName, path))
            }.orEmpty()
        }
        return node.children.flatMap { child -> leafRows(child, path) }
            .distinctBy { it.source to it.rawName to it.value }
            .take(80)
    }

    private fun friendlyLabel(rawName: String): String {
        val name = rawName.substringAfterLast('.')
        return when (name.lowercase(Locale.US)) {
            "networkmode", "network_mode" -> "Mode jaringan"
            "networkband", "network_band" -> "Band jaringan"
            "lteband", "lte_band" -> "Band LTE"
            "namelte" -> "Nama LTE"
            "service_domain", "servicedomain" -> "Service domain"
            "accessmode" -> "Mode akses"
            "band" -> "Band"
            "rat" -> "RAT"
            else -> name.replace('_', ' ').replace('-', ' ').split(' ').filter(String::isNotBlank)
                .joinToString(" ") { word -> word.replaceFirstChar { it.titlecase(Locale.US) } }
        }
    }

    private fun latencyUrls(target: String): List<String> {
        val raw = target.trim().removeSuffix("/")
        if (raw.startsWith("http://", true) || raw.startsWith("https://", true)) return listOf(raw)
        val host = raw.substringBefore('/').removePrefix("[").removeSuffix("]")
            .takeIf(String::isNotBlank) ?: "1.1.1.1"
        val hostForUrl = if (':' in host && host.count { it == ':' } > 1) "[$host]" else host
        return listOf("https://$hostForUrl/", "http://$hostForUrl/")
    }

    private fun requestAny(urls: List<String>): Boolean {
        var lastError: Throwable? = null
        urls.forEach { url ->
            val request = Request.Builder()
                .url(url)
                .head()
                .header("User-Agent", "OrbitControl/1.0")
                .build()
            val ok = runCatching {
                publicHttp.newCall(request).execute().use { response -> response.code in 100..599 }
            }.onFailure { lastError = it }.getOrDefault(false)
            if (ok) return true
        }
        throw IOException(lastError?.message ?: "Target tidak merespons lewat HTTP/HTTPS.")
    }

    companion object {
        const val BAND_LOCK_ENDPOINT = "/api/net/net-mode"
        private val B312_FALLBACK_BANDS = listOf(1, 3, 5, 8, 38, 40)
        private const val FALLBACK_BAND_NOTICE =
            "Daftar band fallback, endpoint firmware tidak mengembalikan daftar lengkap."
    }
}

data class NetworkBandSnapshot(
    val signal: EndpointValue<SignalInfo> = EndpointValue(),
    val status: EndpointValue<ModemStatus> = EndpointValue(),
    val netMode: EndpointValue<ReadOnlyEndpoint> = EndpointValue(),
    val netModeList: EndpointValue<ReadOnlyEndpoint> = EndpointValue(),
    val lteBandInfo: EndpointValue<ReadOnlyEndpoint> = EndpointValue(),
    val bandInfo: BandUiInfo = BandUiInfo(),
)

data class BandUiInfo(
    val currentConfig: NetModeConfig? = null,
    val currentNetworkMode: String? = null,
    val currentNetworkModeLabel: String = "N/A",
    val currentNetworkBand: String? = null,
    val currentLteBand: String? = null,
    val activeBand: String? = null,
    val activeBandNumber: Int? = null,
    val supportedBands: List<LteBandOption> = emptyList(),
    val supportedBandsNotice: String? = null,
    val technicalFields: List<InfoField> = emptyList(),
)

data class NetModeConfig(
    val networkMode: String? = null,
    val networkBand: String? = null,
    val lteBand: String? = null,
)

enum class BandLockSupportState { UNKNOWN, POST_CANDIDATE, UNSUPPORTED }

data class BandLockSupport(
    val state: BandLockSupportState = BandLockSupportState.UNKNOWN,
    val canApply: Boolean = false,
    val endpoint: String? = null,
    val message: String = "Belum dicek.",
)

data class BandLockResult(
    val success: Boolean,
    val requestedBands: List<Int>,
    val requestedLteBand: String?,
    val before: NetModeConfig? = null,
    val after: NetModeConfig? = null,
    val activeBand: String? = null,
    val restored: Boolean = false,
    val message: String,
)

data class NetworkStatisticsSnapshot(
    val traffic: EndpointValue<TrafficInfo> = EndpointValue(),
    val month: EndpointValue<MonthStatistics> = EndpointValue(),
    val status: EndpointValue<ModemStatus> = EndpointValue(),
)

data class ReadOnlyEndpoint(
    val title: String,
    val path: String,
    val fields: List<InfoField>,
    val rawXml: String = "",
)

data class InfoField(val label: String, val value: String, val rawName: String = label, val source: String = "")

data class MonthStatistics(
    val monthUpload: Long? = null,
    val monthDownload: Long? = null,
    val monthTotal: Long? = null,
    val monthDuration: Long? = null,
    val totalUpload: Long? = null,
    val totalDownload: Long? = null,
    val totalUsage: Long? = null,
    val totalDuration: Long? = null,
)

data class DiagnosisResult(
    val target: String,
    val reachable: Boolean,
    val latencyMs: Long?,
    val attempts: Int,
    val successCount: Int,
    val failureCount: Int,
    val method: String,
    val message: String,
)

private val BAND_PATTERN = Regex("""(?i)\b(?:LTE\s*)?(?:B|BC|Band)\s*([1-9]\d?)\b""")

private fun XmlNode.longValue(vararg names: String): Long? = Formatters.long(firstValue(*names))

private fun XmlNode.toXmlString(): String = buildString {
    fun appendNode(node: XmlNode) {
        append('<').append(node.name).append('>')
        if (node.children.isEmpty()) {
            append(node.text.escapeXmlText())
        } else {
            node.children.forEach(::appendNode)
        }
        append("</").append(node.name).append('>')
    }
    appendNode(this@toXmlString)
}

private fun String.escapeXmlText(): String = replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&apos;")

private fun ReadOnlyEndpoint.firstRawValue(vararg names: String): String? {
    val targets = names.map { it.lowercase(Locale.US) }.toSet()
    return fields.firstOrNull { it.rawName.lowercase(Locale.US) in targets }?.value.cleanText()
}

private fun String?.cleanText(): String? = this?.trim()?.takeUnless {
    it.isEmpty() || it == "-" || it == "--" || it.equals("N/A", true)
}

private fun sumOrNull(left: Long?, right: Long?): Long? =
    if (left == null && right == null) null else (left ?: 0L) + (right ?: 0L)

private fun extractBandsFromText(raw: String): List<Int> =
    BAND_PATTERN.findAll(raw).mapNotNull { it.groupValues.getOrNull(1)?.toIntOrNull() }.filter { it in 1..64 }.toList()

private fun bandNumber(raw: String?): Int? =
    raw?.let { extractBandsFromText(it).firstOrNull() ?: Regex("""(?i)^B?(\d{1,2})$""").find(it.trim())?.groupValues?.getOrNull(1)?.toIntOrNull() }

private fun lteBandHex(bands: Collection<Int>): String {
    var mask = BigInteger.ZERO
    bands.filter { it in 1..64 }.distinct().forEach { band -> mask = mask.setBit(band - 1) }
    return mask.toString(16).uppercase(Locale.US)
}

private fun normalizeHex(value: String?): String? =
    value?.trim()?.removePrefix("0x")?.removePrefix("0X")?.trimStart('0')?.uppercase(Locale.US)?.ifBlank { "0" }

private fun networkModeLabel(value: String): String = when (value.trim()) {
    "00" -> "Otomatis"
    "01" -> "2G saja"
    "02" -> "3G saja"
    "03" -> "4G/LTE saja"
    else -> "Tidak diketahui"
}

private fun networkTypeLabel(value: String): String = when (value.trim()) {
    "0" -> "Tidak ada layanan"
    "1" -> "GSM"
    "2" -> "GPRS"
    "3" -> "EDGE"
    "4" -> "WCDMA"
    "5" -> "HSDPA"
    "6" -> "HSUPA"
    "7" -> "HSPA"
    "9" -> "HSPA+"
    "19" -> "LTE"
    else -> "Tidak diketahui"
}
