package id.orbitcontrol.data.repository

import id.orbitcontrol.data.HuaweiSessionManager
import id.orbitcontrol.domain.Formatters
import id.orbitcontrol.domain.TrafficInfo

class TrafficRepository(private val session: HuaweiSessionManager) {
    suspend fun getTraffic(): TrafficInfo {
        val node = session.authenticatedGet("/api/monitoring/traffic-statistics").response()
        return TrafficInfo(
            currentUpload = Formatters.long(node.firstValue("CurrentUpload", "CurrentUploadTraffic", "SessionUpload")),
            currentDownload = Formatters.long(node.firstValue("CurrentDownload", "CurrentDownloadTraffic", "SessionDownload")),
            currentUploadRate = Formatters.long(node.firstValue("CurrentUploadRate", "UploadRate")),
            currentDownloadRate = Formatters.long(node.firstValue("CurrentDownloadRate", "DownloadRate")),
            totalUpload = Formatters.long(node.firstValue("TotalUpload", "TotalUploadTraffic", "AllUpload")),
            totalDownload = Formatters.long(node.firstValue("TotalDownload", "TotalDownloadTraffic", "AllDownload")),
            currentConnectTime = Formatters.long(node.firstValue("CurrentConnectTime", "CurrentDuration", "SessionDuration")),
        )
    }
}
