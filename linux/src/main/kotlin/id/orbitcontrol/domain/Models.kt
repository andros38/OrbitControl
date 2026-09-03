package id.orbitcontrol.domain

data class SignalInfo(
    val rsrp: Double? = null,
    val rsrq: Double? = null,
    val sinr: Double? = null,
    val rssi: Double? = null,
    val band: String? = null,
    val pci: String? = null,
    val cellId: String? = null,
    val enodeB: String? = null,
)
data class ModemStatus(
    val connectionStatus: String? = null,
    val networkType: String? = null,
    val signalBars: Int? = null,
    val roaming: Boolean? = null,
    val wanIpAddress: String? = null,
    val wanIpv6Address: String? = null,
    val primaryDns: String? = null,
    val secondaryDns: String? = null,
)
data class OperatorInfo(val name: String? = null, val shortName: String? = null, val plmn: String? = null, val radioAccessType: String? = null)
data class NetworkInfo(
    val wanIpAddress: String? = null,
    val wanIpv6Address: String? = null,
    val lanIpAddress: String? = null,
    val lanNetmask: String? = null,
)
data class TrafficInfo(val currentUpload: Long? = null, val currentDownload: Long? = null, val currentUploadRate: Long? = null, val currentDownloadRate: Long? = null, val totalUpload: Long? = null, val totalDownload: Long? = null, val currentConnectTime: Long? = null)
data class ConnectedDevice(
    val name: String,
    val ipAddress: String,
    val macAddress: String,
    val active: Boolean?,
    val ipv4Address: String? = null,
    val ipv6Address: String? = null,
)
data class EndpointValue<T>(val value: T? = null, val error: String? = null)
data class DashboardSnapshot(
    val session: EndpointValue<String> = EndpointValue(),
    val signal: EndpointValue<SignalInfo> = EndpointValue(),
    val status: EndpointValue<ModemStatus> = EndpointValue(),
    val operator: EndpointValue<OperatorInfo> = EndpointValue(),
    val network: EndpointValue<NetworkInfo> = EndpointValue(),
    val traffic: EndpointValue<TrafficInfo> = EndpointValue(),
    val devices: EndpointValue<List<ConnectedDevice>> = EndpointValue(),
)
enum class SignalGrade(val label: String) { EXCELLENT("Sangat Bagus"), GOOD("Bagus"), FAIR("Lumayan"), POOR("Buruk"), UNKNOWN("N/A") }
