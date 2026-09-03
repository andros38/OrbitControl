package id.orbitcontrol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.orbitcontrol.ui.components.ErrorMessage
import id.orbitcontrol.ui.components.SectionCard
import id.orbitcontrol.ui.components.ValueRow
import id.orbitcontrol.viewmodel.DeviceViewModel

@Composable
fun DeviceScreen(viewModel: DeviceViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LazyColumn(
        Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 40.dp),
    ) {
        item {
            Column {
                Text("Perangkat", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Klien yang dilaporkan modem", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item { Button(onClick = viewModel::refresh, enabled = !state.loading) { Text("Muat ulang") } }
        if (state.loading) item { CircularProgressIndicator(Modifier.fillMaxWidth().padding(20.dp).wrapContentWidth(Alignment.CenterHorizontally)) }
        state.error?.let { error -> item { SectionCard("Tidak dapat membaca perangkat") { ErrorMessage(error) } } }
        if (!state.loading && state.error == null && state.devices.isEmpty()) item { SectionCard("Belum ada perangkat") { Text("Modem tidak mengembalikan entri host aktif.") } }
        items(state.devices.size) { index ->
            val device = state.devices[index]
            SectionCard(device.name) {
                ValueRow("Nama perangkat", device.name)
                ValueRow("IPv4", device.ipv4Address ?: "N/A")
                ValueRow("IPv6", device.ipv6Address ?: "N/A")
                ValueRow("MAC", device.macAddress)
                ValueRow("Status", device.active?.let { if (it) "Aktif" else "Tidak aktif" } ?: "Aktif")
            }
        }
    }
}
