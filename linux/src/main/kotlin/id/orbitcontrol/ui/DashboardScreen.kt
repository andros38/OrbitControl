package id.orbitcontrol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.orbitcontrol.domain.Formatters
import id.orbitcontrol.domain.SignalGrade
import id.orbitcontrol.domain.SignalInfo
import id.orbitcontrol.domain.SignalQualityEvaluator
import id.orbitcontrol.ui.components.ErrorMessage
import id.orbitcontrol.ui.components.OrbitBlue
import id.orbitcontrol.ui.components.OrbitGreen
import id.orbitcontrol.ui.components.OrbitOrange
import id.orbitcontrol.ui.components.OrbitRed
import id.orbitcontrol.ui.components.SectionCard
import id.orbitcontrol.ui.components.ValueRow
import id.orbitcontrol.viewmodel.DashboardViewModel
import java.util.Locale

private const val NOT_AVAILABLE = "N/A"

@Composable
fun DashboardScreen(viewModel: DashboardViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val data = state.snapshot

    LazyColumn(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 40.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Ringkasan", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        if (state.loading) "Memperbarui data..." else "Diperbarui otomatis setiap 5 detik",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(onClick = viewModel::refresh, enabled = !state.loading) { Text("Refresh") }
            }
        }

        item {
            SectionCard("Sinyal Live") {
                data.signal.value?.let { signal -> SignalLiveContent(signal) }
                data.signal.error?.let { ErrorMessage(shortError(it)) }
            }
        }

        item {
            val status = data.status.value
            val signal = data.signal.value
            val operator = data.operator.value
            val online = status?.connectionStatus == "901" && data.status.error == null
            val deviceCount = data.devices.value?.count { it.active != false }
            SectionCard("Status modem") {
                ValueRow("Kondisi", if (online) "Online" else "Offline", if (online) OrbitGreen else OrbitRed)
                ValueRow("Status koneksi", connectionLabel(status?.connectionStatus))
                ValueRow("Jaringan", networkTypeLabel(status?.networkType))
                ValueRow("Operator", operator?.name ?: operator?.shortName ?: NOT_AVAILABLE)
                ValueRow("PLMN", operator?.plmn ?: NOT_AVAILABLE)
                ValueRow("Band aktif", signal?.band ?: NOT_AVAILABLE)
                ValueRow("Perangkat terhubung", deviceCount?.toString() ?: NOT_AVAILABLE)
                data.status.error?.let { ErrorMessage(shortError(it)) }
                if (data.operator.error != null) Text("Informasi operator tidak tersedia.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        item {
            SectionCard("Traffic sesi") {
                data.traffic.value?.let { traffic ->
                    ValueRow("Durasi koneksi", Formatters.duration(traffic.currentConnectTime))
                    ValueRow("Download sesi", Formatters.bytes(traffic.currentDownload))
                    ValueRow("Upload sesi", Formatters.bytes(traffic.currentUpload))
                    ValueRow("Download rate", Formatters.rate(traffic.currentDownloadRate), OrbitBlue)
                    ValueRow("Upload rate", Formatters.rate(traffic.currentUploadRate), OrbitBlue)
                }
                data.traffic.error?.let { ErrorMessage(shortError(it)) }
            }
        }

        item {
            val status = data.status.value
            val network = data.network.value
            val wanIp = network?.wanIpAddress ?: status?.wanIpAddress
            val wanIpv6 = network?.wanIpv6Address ?: status?.wanIpv6Address
            val dns = listOfNotNull(status?.primaryDns, status?.secondaryDns).distinct().joinToString(" / ").ifBlank { NOT_AVAILABLE }
            SectionCard("WAN / LAN") {
                ValueRow("WAN IPv4", wanIp ?: NOT_AVAILABLE)
                ValueRow("WAN IPv6", wanIpv6 ?: NOT_AVAILABLE)
                ValueRow("DNS", dns)
                ValueRow("LAN gateway", network?.lanIpAddress ?: NOT_AVAILABLE)
                ValueRow("LAN netmask", network?.lanNetmask ?: NOT_AVAILABLE)
                if (data.network.error != null) Text("Sebagian informasi jaringan tidak tersedia.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

    }
}

@Composable
private fun SignalLiveContent(signal: SignalInfo) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SignalMetricMeter(
            label = "RSRP", value = signal.rsrp, unit = "dBm",
            grade = SignalQualityEvaluator.rsrp(signal.rsrp), progress = SignalQualityEvaluator.normalizedRsrp(signal.rsrp),
            modifier = Modifier.weight(1f),
        )
        SignalMetricMeter(
            label = "RSRQ", value = signal.rsrq, unit = "dB",
            grade = SignalQualityEvaluator.rsrq(signal.rsrq), progress = SignalQualityEvaluator.normalizedRsrq(signal.rsrq),
            modifier = Modifier.weight(1f),
        )
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SignalMetricMeter(
            label = "SINR", value = signal.sinr, unit = "dB",
            grade = SignalQualityEvaluator.sinr(signal.sinr), progress = SignalQualityEvaluator.normalizedSinr(signal.sinr),
            modifier = Modifier.weight(1f),
        )
        SignalMetricMeter(
            label = "RSSI", value = signal.rssi, unit = "dBm",
            grade = SignalQualityEvaluator.rssi(signal.rssi), progress = SignalQualityEvaluator.normalizedRssi(signal.rssi),
            modifier = Modifier.weight(1f),
        )
    }
    ValueRow("Band", signal.band ?: NOT_AVAILABLE)
    ValueRow("PCI", signal.pci ?: NOT_AVAILABLE)
    ValueRow("Cell ID", signal.cellId ?: NOT_AVAILABLE)
    ValueRow("eNodeB", signal.enodeB ?: NOT_AVAILABLE)
}

@Composable
private fun SignalMetricMeter(
    label: String,
    value: Double?,
    unit: String,
    grade: SignalGrade,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val color = gradeColor(grade)
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.09f)),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value?.let { "${formatMetric(it)} $unit" } ?: NOT_AVAILABLE,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = color,
            )
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = color)
            Text(grade.label, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun formatMetric(value: Double): String = if (value % 1.0 == 0.0) {
    value.toInt().toString()
} else {
    String.format(Locale.US, "%.1f", value)
}

private fun gradeColor(grade: SignalGrade): Color = when (grade) {
    SignalGrade.EXCELLENT, SignalGrade.GOOD -> OrbitGreen
    SignalGrade.FAIR -> OrbitOrange
    SignalGrade.POOR -> OrbitRed
    SignalGrade.UNKNOWN -> Color.Gray
}

private fun connectionLabel(value: String?): String = when (value) {
    "900" -> "Menghubungkan"
    "901" -> "Terhubung"
    "902" -> "Terputus"
    "903" -> "Memutuskan"
    "904" -> "Gagal terhubung"
    "905" -> "Tidak ada layanan"
    "906" -> "Gangguan koneksi"
    null, "" -> NOT_AVAILABLE
    else -> "Tidak diketahui"
}

private fun networkTypeLabel(value: String?): String = when (value) {
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
    null, "" -> NOT_AVAILABLE
    else -> "Tidak diketahui"
}

private fun shortError(message: String): String = when {
    message.contains("tidak dapat menghubungi", true) -> "Modem tidak dapat dijangkau."
    message.contains("tidak didukung", true) -> "Data tidak didukung firmware."
    message.contains("sesi", true) -> "Sesi modem perlu diperbarui."
    else -> "Data modem belum tersedia."
}
