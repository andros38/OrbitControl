package id.orbitcontrol.data.repository

import id.orbitcontrol.data.XmlNode

internal fun XmlNode.response(): XmlNode = if (name.equals("response", true)) this else child("response") ?: this
internal fun XmlNode.firstValue(vararg names: String): String? = value(*names)
