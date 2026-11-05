package app.upmile.core.vehicle

enum class Gear { P, R, N, D, UNKNOWN }

data class VehicleSnapshot(
    val gear: Gear, val speedMps: Float, val brakePressed: Boolean, val steeringDeg: Float,
    val doorsClosed: Boolean, val activeTelltales: List<String>, val energyPercent: Int?, val rangeKm: Int?,
    val beltBuckled: Boolean?, val driverSeated: Boolean, val wiperOn: Boolean, val headlightsOn: Boolean,
    val outsideTempC: Float?, val timestampMs: Long)
