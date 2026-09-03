package id.orbitcontrol.data

import android.util.Xml
import org.xmlpull.v1.XmlPullParser

data class XmlNode(
    val name: String,
    val text: String = "",
    val children: List<XmlNode> = emptyList(),
) {
    fun child(name: String): XmlNode? = children.firstOrNull { it.name.equals(name, ignoreCase = true) }
    fun children(name: String): List<XmlNode> = children.filter { it.name.equals(name, ignoreCase = true) }
    fun value(vararg names: String): String? = names.firstNotNullOfOrNull { child(it)?.text?.trim()?.takeIf(String::isNotEmpty) }
    fun descendants(name: String): List<XmlNode> = buildList {
        children.forEach { item ->
            if (item.name.equals(name, ignoreCase = true)) add(item)
            addAll(item.descendants(name))
        }
    }
}

object HuaweiXmlParser {
    fun parse(xml: String): XmlNode? {
        if (xml.isBlank()) return null
        return runCatching {
            val parser = Xml.newPullParser().apply {
                setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                setInput(xml.filterValidXmlChars().reader())
            }
            val stack = ArrayDeque<MutableNode>()
            var root: XmlNode? = null
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                when (parser.eventType) {
                    XmlPullParser.START_TAG -> stack.addLast(MutableNode(parser.name))
                    XmlPullParser.TEXT, XmlPullParser.CDSECT -> stack.lastOrNull()?.text?.append(parser.text)
                    XmlPullParser.END_TAG -> {
                        val completed = stack.removeLast().freeze()
                        if (stack.isEmpty()) root = completed else stack.last().children += completed
                    }
                }
                parser.next()
            }
            root
        }.getOrNull()
    }

    fun buildRequest(fields: Map<String, Any?>): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?><request>")
        fields.forEach { (key, value) -> appendNode(key, value) }
        append("</request>")
    }

    fun errorCode(node: XmlNode?): String? {
        val error = when {
            node == null -> null
            node.name.equals("error", true) -> node
            else -> node.descendants("error").firstOrNull()
        }
        return error?.value("code") ?: if (error?.children?.isEmpty() == true) error.text.trim().takeIf(String::isNotEmpty) else null
    }

    private fun StringBuilder.appendNode(name: String, value: Any?) {
        when (value) {
            is Map<*, *> -> {
                append('<').append(name).append('>')
                value.forEach { (key, child) -> if (key != null) appendNode(key.toString(), child) }
                append("</").append(name).append('>')
            }
            is Iterable<*> -> value.forEach { appendNode(name, it) }
            else -> append('<').append(name).append('>')
                .append(value?.toString()?.escapeXml().orEmpty())
                .append("</").append(name).append('>')
        }
    }

    private data class MutableNode(
        val name: String,
        val text: StringBuilder = StringBuilder(),
        val children: MutableList<XmlNode> = mutableListOf(),
    ) {
        fun freeze() = XmlNode(name, text.toString().trim(), children.toList())
    }
}

private fun String.escapeXml(): String = replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&apos;")

private fun String.filterValidXmlChars(): String = filter { c ->
    c == '\t' || c == '\n' || c == '\r' || c.code in 0x20..0xD7FF || c.code in 0xE000..0xFFFD
}
