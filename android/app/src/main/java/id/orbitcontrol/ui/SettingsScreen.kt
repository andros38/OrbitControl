package id.orbitcontrol.ui

import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.orbitcontrol.ui.components.OrbitGreen
import id.orbitcontrol.ui.components.OrbitRed
import id.orbitcontrol.ui.components.SectionCard
import id.orbitcontrol.ui.components.ValueRow
import id.orbitcontrol.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val session = viewModel.sessionSnapshot()
    var showFirmwareDetails by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(viewModel) {
        viewModel.shareReport.collect { file ->
            runCatching {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file,
                )
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Orbit Control Debug Report")
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newRawUri("Orbit Control Debug Report", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(sendIntent, "Bagikan Debug Report"))
            }.onFailure { viewModel.reportShareFailed() }
        }
    }

    LaunchedEffect(showFirmwareDetails) {
        if (showFirmwareDetails && !state.firmwareDetailsLoaded) viewModel.loadFirmwareDetails()
    }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Pengaturan", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        SectionCard("Koneksi modem") {
            ValueRow("Host", session.host)
            ValueRow("Username", session.username)
            Text("Password tidak disimpan. Ubah host atau akun dengan keluar lalu login kembali.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SectionCard("Sesi") {
            ValueRow("SessionID", if (session.hasSessionId) "Aktif" else "Tidak ada")
            ValueRow("Token", if (session.hasVerificationToken) "Aktif" else "Tidak ada")
            Button(onClick = viewModel::logout) { Text("Keluar dari modem") }
        }
        SectionCard("Detail Teknis Firmware") {
            OutlinedButton(
                onClick = { showFirmwareDetails = !showFirmwareDetails },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (showFirmwareDetails) "Tutup Detail Teknis" else "Lihat Detail Teknis")
            }
            if (showFirmwareDetails) {
                when {
                    state.firmwareDetailsLoading -> Text(
                        "Membaca detail firmware...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    state.firmwareDetails.isEmpty() -> Text(
                        state.firmwareDetailsMessage ?: "Detail firmware tidak tersedia.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> state.firmwareDetails.forEachIndexed { index, field ->
                        if (index > 0) HorizontalDivider()
                        Text(field.label, fontWeight = FontWeight.SemiBold)
                        field.source.takeIf(String::isNotBlank)?.let { source ->
                            Text(
                                source,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            field.value,
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
        SectionCard("Debug Report") {
            Text(
                "Kumpulkan status modem dan hasil endpoint untuk membantu diagnosis. Password, token, IMEI, dan serial tidak disertakan.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = viewModel::exportDebugReport,
                enabled = !state.exportingReport,
            ) {
                Text(if (state.exportingReport) "Membuat laporan..." else "Export Debug Report")
            }
            state.reportMessage?.let { message ->
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.reportError) OrbitRed else OrbitGreen,
                )
            }
        }
        SectionCard("Tentang") {
            Text("Orbit Control v0.5.0")
            Text("Dibuat oleh Ahmad Asyhari", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
