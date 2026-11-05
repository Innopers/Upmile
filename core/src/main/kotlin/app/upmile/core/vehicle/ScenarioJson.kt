package app.upmile.core.vehicle

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class ScenarioFrame(val snapshot: VehicleSnapshot, val lat: Double, val lon: Double)

/** One file feeds the app (runDemo) and tools/demo.sh. Flat DTO keeps the JSON greppable. */
object ScenarioJson {
    private val json = Json { prettyPrint = false }

    @Serializable
    private class Dto(
        val t: Long, val lat: Double, val lon: Double, val gear: Gear, val speedMps: Float,
        val brakePressed: Boolean, val steeringDeg: Float, val doorsClosed: Boolean,
        val activeTelltales: List<String>?, val energyPercent: Int?, val rangeKm: Int?,
        val beltBuckled: Boolean?, val driverSeated: Boolean, val wiperOn: Boolean,
        val headlightsOn: Boolean, val outsideTempC: Float?,
    )

    fun decode(json: String): List<ScenarioFrame> = this.json.decodeFromString<List<Dto>>(json).map {
        ScenarioFrame(
            VehicleSnapshot(it.gear, it.speedMps, it.brakePressed, it.steeringDeg, it.doorsClosed, it.activeTelltales,
                it.energyPercent, it.rangeKm, it.beltBuckled, it.driverSeated, it.wiperOn, it.headlightsOn,
                it.outsideTempC, it.t),
            it.lat, it.lon,
        )
    }

    /** One frame per line so diffs and demo.sh greps stay readable. */
    fun encode(frames: List<ScenarioFrame>): String = frames.joinToString(",\n", "[\n", "\n]\n") { f ->
        val s = f.snapshot
        json.encodeToString(Dto(s.timestampMs, f.lat, f.lon, s.gear, s.speedMps, s.brakePressed, s.steeringDeg,
            s.doorsClosed, s.activeTelltales, s.energyPercent, s.rangeKm, s.beltBuckled, s.driverSeated,
            s.wiperOn, s.headlightsOn, s.outsideTempC))
    }
}
