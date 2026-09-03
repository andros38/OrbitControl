package id.orbitcontrol.data.repository

import id.orbitcontrol.data.HuaweiSessionManager
import id.orbitcontrol.domain.Formatters
import id.orbitcontrol.domain.SignalInfo

class SignalRepository(private val session: HuaweiSessionManager) {
    suspend fun getSignal(): SignalInfo {
        val node = session.authenticatedGet("/api/device/signal").response()
        val cellId = node.firstValue(
            "cell_id", "cellid", "CellID", "cell_id_4g", "lte_cell_id", "ltecellid", "scell_id", "scellid",
        ).cleanSignalText()
        val rawEnodeB = node.firstValue(
            "enodeb_id", "enodeb", "eNodeB", "enb_id", "enbid", "lte_enodeb_id",
        ).cleanSignalText()
        return SignalInfo(
            rsrp = Formatters.number(node.firstValue("rsrp", "RSRP", "lte_rsrp", "LteRsrp")),
            rsrq = Formatters.number(node.firstValue("rsrq", "RSRQ", "lte_rsrq", "LteRsrq")),
            sinr = Formatters.number(node.firstValue("sinr", "SINR", "lte_sinr", "LteSinr")),
            rssi = Formatters.number(node.firstValue("rssi", "RSSI", "lte_rssi", "LteRssi")),
            band = normalizeBand(node.firstValue("band", "Band", "lteband", "lte_band", "LTEBand", "band_info")),
            pci = normalizePci(node.firstValue("pci", "PCI", "lte_pci", "physicalcellid", "PhysicalCellID")),
            cellId = cellId,
            enodeB = rawEnodeB ?: deriveEnodeB(cellId),
        )
    }
}

private fun String?.cleanSignalText(): String? = this?.trim()?.takeUnless {
    it.isEmpty() || it == "-" || it == "--" || it.equals("N/A", true)
}

private fun normalizeBand(raw: String?): String? {
    val value = raw.cleanSignalText() ?: return null
    val match = Regex("(?i)(?:LTE\\s*)?(?:BC|B)?\\s*(\\d{1,3})").matchEntire(value)
    return match?.groupValues?.getOrNull(1)?.let { "B$it" } ?: value
}

private fun normalizePci(raw: String?): String? {
    val value = raw.cleanSignalText() ?: return null
    return Formatters.long(value)?.toString() ?: value
}

private fun deriveEnodeB(cellId: String?): String? {
    val value = cellId.cleanSignalText() ?: return null
    if ('-' in value) return value.substringBefore('-').trim().takeIf(String::isNotEmpty)
    val numeric = when {
        value.startsWith("0x", true) -> value.substring(2).toLongOrNull(16)
        value.all(Char::isDigit) -> value.toLongOrNull()
        else -> null
    } ?: return null
    return if (numeric > 255) (numeric shr 8).toString() else null
}
