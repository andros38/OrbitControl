package id.orbitcontrol.data.repository

import id.orbitcontrol.data.HuaweiXmlParser
import id.orbitcontrol.data.XmlNode
import java.math.BigInteger
import java.util.Locale

data class LteBandOption(
    val band: Int,
    val label: String = "B$band",
    val isActive: Boolean = false,
    val source: String,
)

/**
 * Reads selectable LTE bands from /api/net/net-mode-list.
 *
 * NameLTE/Name/Value are descriptive fields on different Huawei firmware
 * variants. LTEBand is accepted as a bit mask only when no human-readable
 * band name can be extracted from the same value.
 */
fun parseSupportedLteBandsFromNetModeList(xml: String): List<LteBandOption> {
    val root = HuaweiXmlParser.parse(xml) ?: return emptyList()
    val found = linkedMapOf<Int, MutableSet<String>>()

    root.walk().forEach { node ->
        val field = node.name.substringAfterLast('.').lowercase(Locale.US)
        if (field !in NET_MODE_LIST_FIELDS) return@forEach
        val value = node.text.trim()
        if (value.isBlank()) return@forEach

        val textBands = extractNamedLteBands(value)
        val bands = when {
            textBands.isNotEmpty() -> textBands
            field == "lteband" -> lteMaskToBands(value)
            else -> emptyList()
        }
        bands.forEach { band ->
            found.getOrPut(band) { linkedSetOf() }.add("net-mode-list:${node.name}")
        }
    }

    return found.entries
        .filter { it.key in 1..64 }
        .sortedBy { it.key }
        .map { (band, sources) ->
            LteBandOption(band = band, source = sources.joinToString(", "))
        }
}

internal fun extractNamedLteBands(raw: String): List<Int> =
    LTE_BAND_NAME_PATTERN.findAll(raw)
        .mapNotNull { it.groupValues.getOrNull(1)?.toIntOrNull() }
        .filter { it in 1..64 }
        .distinct()
        .toList()

private fun XmlNode.walk(): Sequence<XmlNode> = sequence {
    yield(this@walk)
    children.forEach { yieldAll(it.walk()) }
}

private fun lteMaskToBands(raw: String): List<Int> {
    val value = raw.trim().removePrefix("0x").removePrefix("0X")
    if (!LTE_MASK_PATTERN.matches(value) || value.isBlank()) return emptyList()
    val mask = runCatching { BigInteger(value, 16) }.getOrNull() ?: return emptyList()
    if (mask == BigInteger.ZERO) return emptyList()
    return (1..minOf(mask.bitLength(), 64)).filter { mask.testBit(it - 1) }
}

private val NET_MODE_LIST_FIELDS = setOf("namelte", "name", "value", "lteband")
private val LTE_BAND_NAME_PATTERN = Regex("""(?i)\b(?:LTE\s*)?(?:BC|B|Band)\s*([1-9]\d?)\b""")
private val LTE_MASK_PATTERN = Regex("""^[0-9a-fA-F]+$""")

