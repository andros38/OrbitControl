package id.orbitcontrol.data.repository

import android.content.Context
import id.orbitcontrol.data.HuaweiApiException
import id.orbitcontrol.data.HuaweiSessionManager
import id.orbitcontrol.data.XmlNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DebugReportRepository(
    private val context: Context,
    private val session: HuaweiSessionManager,
) {
    suspend fun export(): File = withContext(Dispatchers.IO) {
        val createdAt = reportTime(Date())
        val snapshot = session.snapshot()
        val probes = REPORT_ENDPOINTS.map { path -> probe(path) }
        val byPath = probes.associateBy(EndpointProbe::path)

        val operatorNode = byPath[CURRENT_PLMN]?.node
        val signalNode = byPath[DEVICE_SIGNAL]?.node
        val deviceNode = byPath[DEVICE_INFORMATION]?.node
        val operator = operatorNode?.firstValue("FullName", "OperatorName", "ShortName") ?: "N/A"
        val plmn = operatorNode?.firstValue("Numeric", "PLMN") ?: "N/A"
        val activeBand = normalizeBand(signalNode?.firstValue("band", "Band", "lteband", "LTEBand")) ?: "N/A"

        val report = buildString {
            appendLine("Orbit Control Debug Report")
            appendLine("Dibuat: $createdAt")
            appendLine()
            appendLine("KONEKSI")
            appendLine("Host modem: ${snapshot.host}")
            appendLine("SessionID: ${availability(snapshot.hasSessionId)} (nilai disembunyikan)")
            appendLine("Verification token: ${availability(snapshot.hasVerificationToken)} (nilai disembunyikan)")
            appendLine("Password: tidak disertakan")
            appendLine()
            appendLine("JARINGAN")
            appendLine("Operator: $operator")
            appendLine("PLMN: $plmn")
            appendLine("Band aktif: $activeBand")
            appendLine()
            appendLine("PERANGKAT / FIRMWARE")
            appendLine("Nama perangkat: ${deviceNode.safeValue("DeviceName", "ProductName")}")
            appendLine("Model/keluarga: ${deviceNode.safeValue("ProductFamily", "Classify", "DeviceClass")}")
            appendLine("Versi hardware: ${deviceNode.safeValue("HardwareVersion")}")
            appendLine("Versi software: ${deviceNode.safeValue("SoftwareVersion")}")
            appendLine("Versi WebUI: ${deviceNode.safeValue("WebUIVersion")}")
            appendLine("Identitas pribadi modem (IMEI/serial): tidak disertakan")
            appendLine()
            appendLine("STATUS ENDPOINT")
            probes.forEach { probe ->
                if (probe.success) {
                    appendLine("[OK] ${probe.path}")
                } else {
                    append("[GAGAL] ${probe.path}")
                    probe.errorCode?.let { append(" | kode=$it") }
                    probe.errorMessage?.let { append(" | ${sanitizeError(it)}") }
                    appendLine()
                }
            }
            appendLine()
            appendLine("KODE ERROR YANG MUNCUL")
            val codes = probes.mapNotNull(EndpointProbe::errorCode).distinct()
            appendLine(if (codes.isEmpty()) "Tidak ada" else codes.joinToString(", "))
            appendLine()
            appendLine("Catatan privasi: laporan tidak memuat password, nilai SessionID, nilai token, IMEI, serial, atau respons XML mentah.")
        }

        val directory = File(context.cacheDir, REPORT_DIRECTORY).apply {
            if (!exists() && !mkdirs()) error("Folder laporan debug tidak dapat dibuat.")
        }
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        File(directory, "orbit-control-debug-$timestamp.txt").apply {
            writeText(report, Charsets.UTF_8)
        }
    }

    private suspend fun probe(path: String): EndpointProbe = try {
        EndpointProbe(path = path, success = true, node = session.authenticatedGet(path).response())
    } catch (error: Throwable) {
        EndpointProbe(
            path = path,
            success = false,
            errorCode = (error as? HuaweiApiException)?.code ?: errorCodeFromMessage(error.message),
            errorMessage = error.message,
        )
    }

    companion object {
        const val REPORT_DIRECTORY = "debug_reports"
        private const val DEVICE_SIGNAL = "/api/device/signal"
        private const val CURRENT_PLMN = "/api/net/current-plmn"
        private const val DEVICE_INFORMATION = "/api/device/information"
        private val REPORT_ENDPOINTS = listOf(
            "/api/webserver/SesTokInfo",
            DEVICE_INFORMATION,
            DEVICE_SIGNAL,
            "/api/monitoring/status",
            "/api/monitoring/traffic-statistics",
            "/api/monitoring/month_statistics",
            CURRENT_PLMN,
            "/api/net/net-mode",
            "/api/net/net-mode-list",
            "/api/wlan/host-list",
        )
    }
}

private data class EndpointProbe(
    val path: String,
    val success: Boolean,
    val node: XmlNode? = null,
    val errorCode: String? = null,
    val errorMessage: String? = null,
)

private fun availability(available: Boolean): String = if (available) "tersedia" else "tidak tersedia"

private fun reportTime(date: Date): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss XXX", Locale.US).format(date)

private fun XmlNode?.safeValue(vararg names: String): String =
    this?.firstValue(*names)?.trim()?.takeIf(String::isNotEmpty) ?: "N/A"

private fun normalizeBand(raw: String?): String? {
    val value = raw?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val number = Regex("(?i)(?:LTE\\s*)?(?:BC|B)?\\s*(\\d{1,3})").matchEntire(value)
        ?.groupValues?.getOrNull(1)
    return number?.let { "B$it" } ?: value.take(32)
}

private fun errorCodeFromMessage(message: String?): String? =
    message?.let { Regex("(?<!\\d)(-1|100002|100003|125001|125002|125003)(?!\\d)").find(it)?.value }

private fun sanitizeError(message: String): String = when {
    message.contains("100002") || message.contains("tidak didukung", true) -> "Endpoint tidak didukung firmware."
    message.contains("100003") || message.contains("login ulang", true) -> "Sesi memerlukan login ulang."
    message.contains("125001") || message.contains("125002") || message.contains("125003") ||
        message.contains("token", true) -> "Token atau sesi tidak valid."
    message.contains("tidak dapat menghubungi", true) -> "Modem tidak dapat dijangkau."
    else -> message.replace(Regex("(?i)(token|sessionid)=[^\\s;,]+"), "$1=<disembunyikan>").take(180)
}

