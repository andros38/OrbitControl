package id.orbitcontrol.data

import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

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
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = false
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                isXIncludeAware = false
                isExpandEntityReferences = false
            }
            val document = factory.newDocumentBuilder().parse(
                ByteArrayInputStream(xml.filterValidXmlChars().toByteArray(Charsets.UTF_8)),
            )
            document.documentElement?.toXmlNode()
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

}

private fun Element.toXmlNode(): XmlNode {
    val childElements = buildList {
        val nodes = childNodes
        for (index in 0 until nodes.length) (nodes.item(index) as? Element)?.let { add(it.toXmlNode()) }
    }
    val directText = buildString {
        val nodes = childNodes
        for (index in 0 until nodes.length) {
            val node = nodes.item(index)
            if (node.nodeType == org.w3c.dom.Node.TEXT_NODE || node.nodeType == org.w3c.dom.Node.CDATA_SECTION_NODE) {
                append(node.nodeValue)
            }
        }
    }.trim()
    return XmlNode(tagName, directText, childElements)
}

private fun String.escapeXml(): String = replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&apos;")

private fun String.filterValidXmlChars(): String = filter { c ->
    c == '\t' || c == '\n' || c == '\r' || c.code in 0x20..0xD7FF || c.code in 0xE000..0xFFFD
}
