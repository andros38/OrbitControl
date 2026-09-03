package id.orbitcontrol.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

val OrbitBlue = Color(0xFF1769E0)
val OrbitGreen = Color(0xFF159447)
val OrbitOrange = Color(0xFFED8A19)
val OrbitRed = Color(0xFFD33A35)

@Composable
fun SectionCard(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
fun ValueRow(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(0.42f),
        )
        Text(
            value,
            color = valueColor,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(0.58f),
        )
    }
}

@Composable
fun ErrorMessage(message: String) {
    Text(
        friendlyError(message),
        modifier = Modifier.fillMaxWidth(),
        color = OrbitRed,
        style = MaterialTheme.typography.bodySmall,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
    )
}

private fun friendlyError(message: String): String = when {
    message.contains("password", true) && message.contains("salah", true) -> "Password modem salah."
    message.contains("username", true) && message.contains("salah", true) -> "Username modem salah."
    message.contains("sesi lain", true) -> "Modem sedang dipakai oleh sesi lain."
    message.contains("wajib diisi", true) -> "Password modem wajib diisi."
    message.contains("alamat modem", true) && message.contains("valid", true) -> "Alamat modem tidak valid."
    message.contains("100003", true) || message.contains("login ulang", true) || message.contains("sesi berakhir", true) ->
        "Sesi berakhir. Silakan login ulang."
    message.contains("125001", true) || message.contains("125002", true) || message.contains("125003", true) ||
        message.contains("token", true) -> "Sesi modem perlu diperbarui."
    message.contains("100002", true) || message.contains("tidak didukung", true) -> "Fitur tidak didukung firmware."
    message.contains("tidak dapat menghubungi", true) || message.contains("tidak dapat dijangkau", true) ||
        message.contains("offline", true) -> "Modem tidak dapat dijangkau."
    message.contains("timeout", true) || message.contains("terlalu lama", true) -> "Permintaan terlalu lama. Coba lagi."
    message.contains("target", true) -> "Target diagnosis tidak dapat dijangkau."
    message.contains("perangkat", true) -> "Daftar perangkat belum tersedia."
    else -> "Data belum tersedia. Coba lagi."
}
