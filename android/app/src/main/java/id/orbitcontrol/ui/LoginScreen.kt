package id.orbitcontrol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.orbitcontrol.ui.components.ErrorMessage
import id.orbitcontrol.ui.components.OrbitBlue
import id.orbitcontrol.ui.components.SectionCard
import id.orbitcontrol.viewmodel.LoginViewModel

@Composable
fun LoginScreen(viewModel: LoginViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showPassword by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 36.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Orbit Control", style = MaterialTheme.typography.headlineLarge, color = OrbitBlue, fontWeight = FontWeight.Bold)
        Text("Dashboard native untuk modem Huawei / Orbit B312", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        SectionCard("Masuk ke modem") {
            OutlinedTextField(state.host, viewModel::setHost, Modifier.fillMaxWidth(), label = { Text("Host modem") }, singleLine = true, enabled = !state.loading)
            OutlinedTextField(state.username, viewModel::setUsername, Modifier.fillMaxWidth(), label = { Text("Username") }, singleLine = true, enabled = !state.loading)
            OutlinedTextField(
                value = state.password, onValueChange = viewModel::setPassword, modifier = Modifier.fillMaxWidth(),
                label = { Text("Password modem") }, singleLine = true, enabled = !state.loading,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = { TextButton(onClick = { showPassword = !showPassword }) { Text(if (showPassword) "Tutup" else "Lihat") } },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(state.saveConfig, viewModel::setSaveConfig, enabled = !state.loading)
                Text("Simpan host dan username")
            }
            Text("Password tidak disimpan dan akan diminta lagi saat aplikasi dibuka.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.message?.let { if (state.isError) ErrorMessage(it) else Text(it, color = id.orbitcontrol.ui.components.OrbitGreen) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = viewModel::testConnection, modifier = Modifier.weight(1f), enabled = !state.loading) { Text("Test Koneksi") }
                Button(onClick = viewModel::login, modifier = Modifier.weight(1f), enabled = !state.loading && state.password.isNotEmpty()) { Text("Masuk") }
            }
            if (state.loading) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
        }
    }
}
