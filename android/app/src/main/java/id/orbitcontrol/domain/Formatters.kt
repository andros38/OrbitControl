package id.orbitcontrol.domain

import java.util.Locale

object Formatters {
    private val numberPattern = Regex("-?\\d+(?:\\.\\d+)?")
    fun number(raw: String?): Double? = raw?.let { numberPattern.find(it)?.value?.toDoubleOrNull() }
    fun long(raw: String?): Long? = number(raw)?.toLong()
    fun bytes(value: Long?): String {
        if (value == null) return "—"
        val units = listOf("B", "KB", "MB", "GB", "TB")
        var size = value.toDouble(); var index = 0
        while (size >= 1024 && index < units.lastIndex) { size /= 1024; index++ }
        return if (index == 0) "$value B" else String.format(Locale.US, "%.1f %s", size, units[index])
    }
    fun rate(value: Long?): String = if (value == null) "—" else "${bytes(value)}/s"
    fun duration(seconds: Long?): String {
        if (seconds == null) return "—"
        val days = seconds / 86400; val hours = (seconds % 86400) / 3600; val minutes = (seconds % 3600) / 60
        return buildString { if (days > 0) append("${days}h "); if (hours > 0 || days > 0) append("${hours}j "); append("${minutes}m") }
    }
}
