package id.orbitcontrol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.orbitcontrol.data.repository.BandLockSupportState
import id.orbitcontrol.data.repository.LteBandOption
import id.orbitcontrol.data.repository.MonthStatistics
import id.orbitcontrol.domain.Formatters
import id.orbitcontrol.domain.TrafficInfo
import id.orbitcontrol.ui.components.ErrorMessage
import id.orbitcontrol.ui.components.OrbitBlue
import id.orbitcontrol.ui.components.OrbitGreen
import id.orbitcontrol.ui.components.OrbitOrange
import id.orbitcontrol.ui.components.OrbitRed
import id.orbitcontrol.ui.components.SectionCard
import id.orbitcontrol.ui.components.ValueRow
import id.orbitcontrol.viewmodel.NetworkToolsUiState
import id.orbitcontrol.viewmodel.NetworkToolsViewModel

private const val NOT_AVAILABLE = "N/A"

@Composable
fun NetworkToolsScreen(
    viewModel: NetworkToolsViewModel,
    selectedTab: Int,
    onSelectedTabChange: (Int) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val tabs = remember { listOf("Pemilihan Band", "Statistik", "Diagnosis") }
    val visibleTab = selectedTab.coerceIn(tabs.indices)

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 6.dp)) {
            Text("Network Tools", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        ScrollableTabRow(selectedTabIndex = visibleTab, edgePadding = 8.dp) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = visibleTab == index,
                    onClick = { onSelectedTabChange(index) },
                    text = {
                        Text(
                            title,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            }
        }
        when (visibleTab) {
            0 -> BandSelectionTab(
                state = state,
                onRefresh = viewModel::refreshBand,
                onToggleBand = viewModel::toggleBand,
                onApplyBand = viewModel::applyBandLock,
            )
            1 -> StatisticsTab(state, viewModel::refreshStatistics)
            2 -> DiagnosisTab(
                state = state,
                onTargetChange = viewModel::updateDiagnosisTarget,
                onQuickTarget = viewModel::useQuickDiagnosisTarget,
                onRun = viewModel::runDiagnosis,
            )
        }
    }
}

@Composable
private fun BandSelectionTab(
    state: NetworkToolsUiState,
    onRefresh: () -> Unit,
    onToggleBand: (Int) -> Unit,
    onApplyBand: () -> Unit,
) {
    val info = state.bandSnapshot.bandInfo
    var confirmApply by remember { mutableStateOf(false) }
    val canApply = state.bandLockSupport.canApply &&
        state.selectedBands.isNotEmpty() &&
        !state.bandLockApplying &&
        !state.bandLockChecking

    if (confirmApply) {
        AlertDialog(
            onDismissRequest = { confirmApply = false },
            title = { Text("Konfirmasi Band Lock") },
            text = { Text("Mengubah band dapat memutus koneksi sementara. Lanjutkan?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmApply = false
                        onApplyBand()
                    },
                ) { Text("Lanjutkan") }
            },
            dismissButton = { TextButton(onClick = { confirmApply = false }) { Text("Batal") } },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 48.dp),
    ) {
        item {
            HeaderActionRow(
                title = "Pemilihan Band",
                subtitle = if (state.bandLoading) "Membaca data modem..." else "Atur band modem secara manual.",
                action = "Refresh",
                enabled = !state.bandLoading,
                onAction = onRefresh,
            )
        }
        item {
            SectionCard("Status band") {
                if (state.bandLoading) SmallLoading()
                ValueRow("Band aktif saat ini", info.activeBand ?: NOT_AVAILABLE, OrbitBlue)
                ValueRow("Mode jaringan", info.currentNetworkModeLabel)
                info.supportedBandsNotice?.let { Text(it, color = OrbitOrange, style = MaterialTheme.typography.bodySmall) }
                state.bandSnapshot.signal.error?.let { ErrorMessage(it) }
            }
        }
        item {
            SectionCard("Band Lock") {
                Text("Pilih satu band LTE, lalu terapkan. Aplikasi akan membaca ulang modem untuk memverifikasi perubahan.")
                if (info.supportedBands.isEmpty()) {
                    Text("Daftar band belum tersedia dari firmware.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    BandChipGrid(info.supportedBands, state.selectedBands, onToggleBand)
                }
                ValueRow(
                    "Target band",
                    state.selectedBands.sorted().joinToString(" + ") { "B$it" }.ifBlank { NOT_AVAILABLE },
                    OrbitBlue,
                )
                Button(
                    onClick = { confirmApply = true },
                    enabled = canApply,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (state.bandLockApplying) "Menerapkan..." else "Terapkan Band")
                }
                BandLockStatusSummary(state)
                Text(state.bandLockSupport.message, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                state.bandLockResult?.let { result ->
                    Text(result.message, color = if (result.success) OrbitGreen else OrbitRed)
                }
            }
        }
    }
}

@Composable
private fun StatisticsTab(state: NetworkToolsUiState, onRefresh: () -> Unit) {
    val snapshot = state.statisticsSnapshot
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 40.dp),
    ) {
        item {
            HeaderActionRow(
                title = "Statistik",
                subtitle = if (state.statisticsLoading) "Membaca statistik..." else "Data berasal dari counter modem.",
                action = "Refresh Statistik",
                enabled = !state.statisticsLoading,
                onAction = onRefresh,
            )
        }
        item {
            SectionCard("Data seluler saat ini") {
                if (state.statisticsLoading) SmallLoading()
                TrafficRows(snapshot.traffic.value, snapshot.month.value)
                snapshot.traffic.error?.let { ErrorMessage(it) }
                snapshot.month.error?.let { Text("Statistik bulanan belum tersedia.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        item {
            SectionCard("Status koneksi") {
                ValueRow("Status", connectionLabel(snapshot.status.value?.connectionStatus))
                ValueRow("Jaringan", networkTypeLabel(snapshot.status.value?.networkType))
                snapshot.status.error?.let { ErrorMessage(it) }
            }
        }
    }
}

@Composable
private fun DiagnosisTab(
    state: NetworkToolsUiState,
    onTargetChange: (String) -> Unit,
    onQuickTarget: (String) -> Unit,
    onRun: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 40.dp),
    ) {
        item {
            SectionCard("Diagnosis koneksi") {
                OutlinedTextField(
                    value = state.diagnosisTarget,
                    onValueChange = onTargetChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Target") },
                    singleLine = true,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickTargetButton("Cloudflare", "1.1.1.1", Modifier.weight(1f)) { onQuickTarget("1.1.1.1") }
                    QuickTargetButton("Google", "8.8.8.8", Modifier.weight(1f)) { onQuickTarget("8.8.8.8") }
                    QuickTargetButton("Quad9", "9.9.9.9", Modifier.weight(1f)) { onQuickTarget("9.9.9.9") }
                }
                Button(onClick = onRun, enabled = !state.diagnosisRunning) {
                    Text(if (state.diagnosisRunning) "Mendiagnosis..." else "Jalankan Diagnosis")
                }
            }
        }
        item {
            SectionCard("Hasil diagnosis") {
                if (state.diagnosisRunning) SmallLoading()
                state.diagnosisResult?.let { result ->
                    ValueRow("Status reachable", if (result.reachable) "Reachable" else "Tidak reachable", if (result.reachable) OrbitGreen else OrbitRed)
                    ValueRow("Latency perkiraan", result.latencyMs?.let { "$it ms" } ?: NOT_AVAILABLE)
                    ValueRow("Jumlah percobaan", result.attempts.toString())
                    ValueRow("Sukses / gagal", "${result.successCount} / ${result.failureCount}")
                    ValueRow("Metode", result.method, OrbitBlue)
                    Text(result.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } ?: Text("Belum ada diagnosis. Jalankan tes untuk melihat hasil nyata.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                state.diagnosisError?.let { ErrorMessage(it) }
            }
        }
    }
}

@Composable
private fun HeaderActionRow(title: String, subtitle: String, action: String, enabled: Boolean, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (subtitle.isNotBlank()) Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(10.dp))
        Button(onClick = onAction, enabled = enabled) { Text(action) }
    }
}

@Composable
private fun BandChipGrid(candidates: List<LteBandOption>, selectedBands: Set<Int>, onToggleBand: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        candidates.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { candidate ->
                    BandButton(
                        candidate = candidate,
                        selected = candidate.band in selectedBands,
                        onClick = { onToggleBand(candidate.band) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun BandButton(candidate: LteBandOption, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier) { BandButtonContent(candidate) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) { BandButtonContent(candidate) }
    }
}

@Composable
private fun BandButtonContent(candidate: LteBandOption) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(candidate.label)
        if (candidate.isActive) Text("Aktif", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun BandLockStatusSummary(state: NetworkToolsUiState) {
    val result = state.bandLockResult
    val status = when {
        state.bandLockSupport.state == BandLockSupportState.UNSUPPORTED -> "Tidak didukung"
        state.bandLockSupport.state == BandLockSupportState.UNKNOWN -> "Belum dicek"
        else -> "Terverifikasi"
    }
    val supportColor = when {
        state.bandLockSupport.state == BandLockSupportState.UNSUPPORTED -> OrbitOrange
        state.bandLockSupport.state == BandLockSupportState.POST_CANDIDATE -> OrbitGreen
        else -> OrbitBlue
    }
    ValueRow("Status band lock", status, supportColor)
    ValueRow(
        "Status apply terakhir",
        result?.let { if (it.success) "Berhasil" else "Gagal" } ?: "Belum ada",
        when {
            result == null -> OrbitBlue
            result.success -> OrbitGreen
            else -> OrbitRed
        },
    )
}

@Composable
private fun TrafficRows(traffic: TrafficInfo?, month: MonthStatistics?) {
    val currentVolume = sumOrNull(traffic?.currentUpload, traffic?.currentDownload)
    ValueRow("Volume saat ini", Formatters.bytes(currentVolume))
    ValueRow("Durasi saat ini", Formatters.duration(traffic?.currentConnectTime))
    ValueRow("Upload sesi", Formatters.bytes(traffic?.currentUpload))
    ValueRow("Download sesi", Formatters.bytes(traffic?.currentDownload))
    ValueRow("Download rate", Formatters.rate(traffic?.currentDownloadRate), OrbitBlue)
    ValueRow("Upload rate", Formatters.rate(traffic?.currentUploadRate), OrbitBlue)
    HorizontalDivider()
    ValueRow("Upload bulanan", Formatters.bytes(month?.monthUpload))
    ValueRow("Download bulanan", Formatters.bytes(month?.monthDownload))
    ValueRow("Penggunaan bulanan", Formatters.bytes(month?.monthTotal))
    ValueRow("Durasi bulanan", Formatters.duration(month?.monthDuration))
    ValueRow("Total penggunaan", Formatters.bytes(month?.totalUsage ?: sumOrNull(traffic?.totalUpload, traffic?.totalDownload)))
    ValueRow("Total durasi", Formatters.duration(month?.totalDuration))
}

@Composable
private fun QuickTargetButton(label: String, value: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label)
            Text(value, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun SmallLoading() {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.padding(2.dp), strokeWidth = 2.dp)
        Text("Memuat data nyata...", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
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

private fun sumOrNull(left: Long?, right: Long?): Long? =
    if (left == null && right == null) null else (left ?: 0L) + (right ?: 0L)
