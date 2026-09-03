package id.orbitcontrol.ui

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.orbitcontrol.ui.components.OrbitGreen
import id.orbitcontrol.ui.components.OrbitRed
import id.orbitcontrol.ui.components.SectionCard
import id.orbitcontrol.ui.components.ValueRow
import id.orbitcontrol.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val session = viewModel.sessionSnapshot()
    var showFirmware by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.shareReport.collect { file ->
            saveCopyWithDialog(file, "Simpan Debug Report").onSuccess(::openFileLocation)
                .onFailure { viewModel.reportShareFailed() }
        }
    }
    LaunchedEffect(showFirmware) {
        if (showFirmware && !state.firmwareDetailsLoaded) viewModel.loadFirmwareDetails()
    }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Pengaturan", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        SectionCard("Koneksi modem") {
            ValueRow("Host", session.host)
            ValueRow("Username", session.username)
            Text("Password hanya disimpan di memori saat sesi aktif.", style = MaterialTheme.typography.bodySmall)
        }
        SectionCard("Sesi") {
            ValueRow("SessionID", if (session.hasSessionId) "Aktif" else "Tidak ada")
            ValueRow("Verification token", if (session.hasVerificationToken) "Aktif" else "Tidak ada")
            Button(onClick = viewModel::logout) { Text("Keluar dari modem") }
        }
        SectionCard("Diagnostik") {
            OutlinedButton(onClick = { showFirmware = !showFirmware }, modifier = Modifier.fillMaxWidth()) {
                Text(if (showFirmware) "Sembunyikan Detail Firmware" else "Tampilkan Detail Firmware")
            }
            if (showFirmware) {
                if (state.firmwareDetailsLoading) Text("Membaca detail firmware…")
                state.firmwareDetailsMessage?.let { Text(it, color = OrbitRed) }
                state.firmwareDetails.forEach { ValueRow(it.label, it.value) }
            }
            HorizontalDivider()
            Button(onClick = viewModel::exportDebugReport, enabled = !state.exportingReport, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.exportingReport) "Membuat laporan…" else "Simpan Debug Report")
            }
            state.reportMessage?.let {
                Text(it, color = if (state.reportError) OrbitRed else OrbitGreen, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
