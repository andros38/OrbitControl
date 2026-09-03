package id.orbitcontrol.data.repository

import id.orbitcontrol.data.HuaweiSessionManager
import id.orbitcontrol.domain.ConnectedDevice

class DeviceRepository(private val session: HuaweiSessionManager) {
    suspend fun getDevices(): List<ConnectedDevice> {
        val root = session.authenticatedGet("/api/wlan/host-list").response()
        return root.descendants("Host").map { host ->
            val rawIp = host.firstValue("IpAddress", "IPAddress", "ipaddress").orEmpty()
            val addresses = splitIpAddresses(rawIp)
            ConnectedDevice(
                name = host.firstValue("HostName", "hostname").orEmpty().ifBlank { "Perangkat tanpa nama" },
                ipAddress = addresses.ipv4 ?: addresses.ipv6 ?: "—",
                macAddress = host.firstValue("MacAddress", "MACAddress", "macaddress").orEmpty().ifBlank { "—" },
                active = host.firstValue("Active", "active")?.let { it == "1" } ?: true,
                ipv4Address = addresses.ipv4,
                ipv6Address = addresses.ipv6,
            )
        }
    }
}

private data class DeviceAddresses(val ipv4: String?, val ipv6: String?)

private fun splitIpAddresses(raw: String): DeviceAddresses {
    val parts = raw.split(';', ',', ' ', '\n', '\t')
        .map(String::trim)
        .filter(String::isNotBlank)
    val ipv4 = parts.firstOrNull { it.matches(Regex("""\d{1,3}(?:\.\d{1,3}){3}""")) }
    val ipv6 = parts.firstOrNull { ':' in it }
    return DeviceAddresses(ipv4, ipv6)
}
