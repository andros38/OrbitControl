package id.orbitcontrol.domain

object SignalQualityEvaluator {
    fun rsrp(value: Double?): SignalGrade = when {
        value == null -> SignalGrade.UNKNOWN
        value >= -80 -> SignalGrade.EXCELLENT
        value >= -90 -> SignalGrade.GOOD
        value >= -100 -> SignalGrade.FAIR
        else -> SignalGrade.POOR
    }
    fun rsrq(value: Double?): SignalGrade = when {
        value == null -> SignalGrade.UNKNOWN
        value >= -10 -> SignalGrade.EXCELLENT
        value >= -15 -> SignalGrade.GOOD
        value >= -20 -> SignalGrade.FAIR
        else -> SignalGrade.POOR
    }
    fun sinr(value: Double?): SignalGrade = when {
        value == null -> SignalGrade.UNKNOWN
        value >= 20 -> SignalGrade.EXCELLENT
        value >= 13 -> SignalGrade.GOOD
        value >= 0 -> SignalGrade.FAIR
        else -> SignalGrade.POOR
    }
    fun rssi(value: Double?): SignalGrade = when {
        value == null -> SignalGrade.UNKNOWN
        value >= -65 -> SignalGrade.EXCELLENT
        value >= -75 -> SignalGrade.GOOD
        value >= -85 -> SignalGrade.FAIR
        else -> SignalGrade.POOR
    }
    fun normalizedRsrp(value: Double?): Float = normalize(value, -120.0, -70.0)
    fun normalizedRsrq(value: Double?): Float = normalize(value, -25.0, -3.0)
    fun normalizedSinr(value: Double?): Float = normalize(value, -10.0, 30.0)
    fun normalizedRssi(value: Double?): Float = normalize(value, -110.0, -50.0)

    private fun normalize(value: Double?, minimum: Double, maximum: Double): Float =
        value?.let { ((it - minimum) / (maximum - minimum)).coerceIn(0.0, 1.0).toFloat() } ?: 0f
}
