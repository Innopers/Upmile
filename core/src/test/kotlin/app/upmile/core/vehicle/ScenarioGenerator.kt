package app.upmile.core.vehicle

import app.upmile.core.model.GeoPoint
import app.upmile.core.model.Route
import app.upmile.core.model.SegmentType
import app.upmile.core.route.AssetRouteRepository
import app.upmile.core.route.SegmentFinder
import app.upmile.core.route.alongRouteM
import app.upmile.core.route.distanceM
import kotlin.math.PI
import kotlin.math.sin

/** Builds assets/scenarios/commute_drive.json from assets/routes/commute.json. Run: GEN_SCENARIO=1 ./gradlew :core:test --tests '*regenerate*' */
object ScenarioGenerator {
    const val STEP = 200L
    const val P_START = 50      // 10 s parked at the origin
    const val DRIVE = 400       // 80 s of D
    const val HOLD = 70         // position pinned at the end while slowing to 0
    const val P_END = 25        // 5 s parked at the destination

    fun load(readAsset: (String) -> String): Route = AssetRouteRepository(readAsset).route("commute")

    fun pointAt(route: Route, alongM: Double): GeoPoint {
        var rem = alongM
        val pl = route.polyline
        for (i in 0 until pl.size - 1) {
            val len = distanceM(pl[i], pl[i + 1])
            if (rem <= len || i == pl.size - 2) {
                val t = (rem / len).coerceIn(0.0, 1.0)
                return GeoPoint(pl[i].lat + (pl[i + 1].lat - pl[i].lat) * t, pl[i].lon + (pl[i + 1].lon - pl[i].lon) * t)
            }
            rem -= len
        }
        return pl.last()
    }

    fun generate(route: Route): List<ScenarioFrame> {
        val total = route.polyline.zipWithNext { a, b -> distanceM(a, b) }.sum()
        val left = SegmentFinder.find(route, true).first { it.type == SegmentType.MULTILANE_LEFT }
        val moving = DRIVE - HOLD
        val along = DoubleArray(DRIVE) { minOf(it, moving) * total / moving }
        // first drive frame whose step crosses the left-turn point
        val h = (1 until DRIVE).first { along[it] >= left.distanceFromStartM }
        val speed = DoubleArray(DRIVE) { i ->
            val cruise = 15.0 + 4.0 * sin(2 * PI * i / 160.0)
            val stop = (DRIVE - 1 - i) * 0.3                 // linear slow-down, 1.5 m/s^2
            // hard brake at the left turn: -1.0 m/s per step (5 m/s^2) for 3 steps, so 400 ms sampling of either
            // phase still sees it; then recovering at +0.4 m/s per step
            val dip = when {
                i < h -> 0.0
                i <= h + 2 -> 1.0 * (i - h + 1)
                else -> (3.0 - 0.4 * (i - h - 2)).coerceAtLeast(0.0)
            }
            minOf(cruise, stop) - dip
        }

        fun snap(t: Long, gear: Gear, v: Double, brake: Boolean, steer: Double) = VehicleSnapshot(
            gear, v.toFloat(), brake, steer.toFloat(), true, emptyList(), 62, 412, true, true, true, false, 7.0f, t)
        val out = ArrayList<ScenarioFrame>()
        val origin = route.polyline.first()
        repeat(P_START) { out += ScenarioFrame(snap(it * STEP, Gear.P, 0.0, false, 0.0), origin.lat, origin.lon) }
        for (i in 0 until DRIVE) {
            val p = pointAt(route, along[i])
            val brake = i in h..h + 2 || i >= DRIVE - 40
            val steer = 4.0 * sin(2 * PI * i / 120.0)
            out += ScenarioFrame(snap((P_START + i) * STEP, Gear.D, speed[i], brake, steer), p.lat, p.lon)
        }
        val end = route.polyline.last()
        repeat(P_END) { out += ScenarioFrame(snap((P_START + DRIVE + it) * STEP, Gear.P, 0.0, false, 0.0), end.lat, end.lon) }
        return out
    }
}
