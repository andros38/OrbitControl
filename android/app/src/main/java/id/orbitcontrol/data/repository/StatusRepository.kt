package id.orbitcontrol.data.repository

import id.orbitcontrol.data.HuaweiSessionManager
import id.orbitcontrol.domain.Formatters
import id.orbitcontrol.domain.ModemStatus
import id.orbitcontrol.domain.NetworkInfo
import id.orbitcontrol.domain.OperatorInfo

class StatusRepository(private val session: HuaweiSessionManager) {
    fun sessionSummary(): String {
        val state = session.snapshot()
        return when {
            state.hasSessionId && state.hasVerificationToken -> "SessionID dan token aktif"
            state.hasSessionId -> "SessionID aktif"
            else -> "Sesi belum tersedia"
        }
    }
    suspend fun getStatus(): ModemStatus {
        val node = session.authenticatedGet("/api/monitoring/status").response()
        return ModemStatus(
            connectionStatus = node.firstValue("ConnectionStatus", "connectionstatus").clean(),
            networkType = node.firstValue("CurrentNetworkType", "currentnetworktype", "CurrentServiceDomain").clean(),
            signalBars = Formatters.long(node.firstValue("SignalIcon", "signalicon"))?.toInt(),
            roaming = node.firstValue("RoamingStatus", "roamingstatus")?.let { it == "1" },
            wanIpAddress = node.firstValue("WanIPAddress", "wan_ip_address", "wanipaddress").clean(),
            wanIpv6Address = node.firstValue("WanIPv6Address", "wan_ipv6_address", "wanipv6address").clean(),
            primaryDns = node.firstValue("PrimaryDns", "primarydns").clean(),
            secondaryDns = node.firstValue("SecondaryDns", "secondarydns").clean(),
        )
    }

    suspend fun getOperator(): OperatorInfo {
        val node = session.authenticatedGet("/api/net/current-plmn").response()
        return OperatorInfo(
            name = node.firstValue("FullName", "fullname", "OperatorName", "operatorname").clean(),
            shortName = node.firstValue("ShortName", "shortname").clean(),
            plmn = node.firstValue("Numeric", "numeric", "PLMN", "plmn").clean(),
            radioAccessType = node.firstValue("Rat", "rat", "RadioAccessTechnology").clean(),
        )
    }

    suspend fun getNetworkInfo(): NetworkInfo {
        val device = runCatching { session.authenticatedGet("/api/device/information").response() }.getOrNull()
        val dhcp = runCatching { session.authenticatedGet("/api/dhcp/settings").response() }.getOrNull()
        return NetworkInfo(
            wanIpAddress = device?.firstValue("WanIPAddress", "wan_ip_address", "wanipaddress").clean(),
            wanIpv6Address = device?.firstValue("WanIPv6Address", "wan_ipv6_address", "wanipv6address").clean(),
            lanIpAddress = dhcp?.firstValue("DhcpIPAddress", "dhcpipaddress", "LanIPAddress", "lanipaddress").clean(),
            lanNetmask = dhcp?.firstValue("DhcpLanNetmask", "dhcplannetmask", "LanNetmask", "lannetmask").clean(),
        )
    }
}

private fun String?.clean(): String? = this?.trim()?.takeUnless {
    it.isEmpty() || it == "-" || it == "--" || it.equals("N/A", true) || it == "0.0.0.0" || it == "::"
}
