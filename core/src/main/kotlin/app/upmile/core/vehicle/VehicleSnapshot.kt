package app.upmile.core.vehicle

enum class Gear { P, R, N, D, UNKNOWN }

data class VehicleSnapshot(
    val gear: Gear, val speedMps: Float, val brakePressed: Boolean, val steeringDeg: Float,
    val doorsClosed: Boolean,
    /** Active telltale ids; null means the telltale list is unavailable (e.g. permission denied). */
    val activeTelltales: List<String>?, val energyPercent: Int?, val rangeKm: Int?,
    val beltBuckled: Boolean?, val driverSeated: Boolean, val wiperOn: Boolean, val headlightsOn: Boolean,
    val outsideTempC: Float?,
    /**
     * Monotonic ms: `SystemClock.elapsedRealtime()` on the device, replay time in a scenario. Only differences
     * are used, never wall-clock dates; a backwards jump just restarts the rate reference.
     */
    val timestampMs: Long)
