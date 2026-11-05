package app.upmile.core.drive

import app.upmile.core.model.GeoPoint
import app.upmile.core.model.HardSegment
import app.upmile.core.model.Route
import app.upmile.core.model.SegmentType
import app.upmile.core.route.alongRouteM
import app.upmile.core.route.distanceM
import app.upmile.core.vehicle.Gear
import app.upmile.core.vehicle.VehicleSnapshot
import kotlin.math.abs
import kotlin.math.roundToInt

sealed interface DriveEvent {
    data class Approaching(val segment: HardSegment, val metersAhead: Int) : DriveEvent
    data class Passed(val segment: HardSegment, val hadHardMoment: Boolean) : DriveEvent
    data class Arrived(val at: GeoPoint, val drivenM: Int) : DriveEvent
}

/**
 * Tracks approach/pass/arrival for [segments] on [route]. Arrival needs >= 300 m NET displacement from the
 * first D position (jitter-proof); [DriveEvent.Arrived.drivenM] is the sum of straight-line hops between
 * consecutive D samples (off-route included).
 */
class DriveTracker(
    private val route: Route,
    private val segments: List<HardSegment>,
    private val approachM: Int = 1200,
    private val passPastM: Int = 150,
) {
    private val approached = mutableSetOf<String>()
    private val passed = mutableSetOf<String>()
    private val hardMoment = mutableSetOf<String>()
    private var ref: VehicleSnapshot? = null // reference for rate maths; not advanced on dt < MIN_DT_MS
    private var refAlong: Double? = null // along-route at ref, null if it was off-route
    private var prevGear: Gear? = null
    private var prevPos: GeoPoint? = null
    private var firstDPos: GeoPoint? = null
    private var drivenM = 0.0
    private var arrived = false

    fun onSample(s: VehicleSnapshot, p: GeoPoint): List<DriveEvent> {
        val out = mutableListOf<DriveEvent>()
        if (s.gear == Gear.D && firstDPos == null) firstDPos = p
        if (prevGear == Gear.D) prevPos?.let { drivenM += distanceM(it, p) }

        val along = alongRouteM(route, p)
        val onRoute = distanceM(p, pointAt(along)) <= OFF_ROUTE_M
        val r = ref
        var advance = true
        if (r != null) {
            val dtMs = s.timestampMs - r.timestampMs
            if (dtMs < MIN_DT_MS) advance = false
            else if (onRoute && isHardMoment(r, s)) {
                // attribute to the whole travelled interval so big position jumps can't skip the window
                val ra = refAlong ?: along
                val lo = minOf(ra, along)
                val hi = maxOf(ra, along)
                segments.filter { hi >= it.distanceFromStartM - 50 && lo <= it.distanceFromStartM + passPastM }
                    .forEach { hardMoment += it.id }
            }
        }
        if (onRoute) {
            for (seg in segments) {
                val ahead = seg.distanceFromStartM - along
                if (ahead > 0 && ahead <= approachM && approached.add(seg.id)) {
                    out += DriveEvent.Approaching(seg, ahead.roundToInt())
                }
                if (seg.type != SegmentType.GARAGE_RAMP && along >= seg.distanceFromStartM + passPastM && passed.add(seg.id)) {
                    out += DriveEvent.Passed(seg, seg.id in hardMoment)
                }
            }
        }

        val start = firstDPos
        if (!arrived && prevGear == Gear.D && s.gear == Gear.P && s.driverSeated &&
            start != null && distanceM(start, p) >= ARRIVE_MIN_M
        ) {
            arrived = true
            segments.filter { it.type == SegmentType.GARAGE_RAMP && passed.add(it.id) }
                .forEach { out += DriveEvent.Passed(it, it.id in hardMoment) }
            out += DriveEvent.Arrived(p, drivenM.roundToInt())
        }
        if (advance) {
            ref = s
            refAlong = if (onRoute) along else null
        }
        prevGear = s.gear
        prevPos = p
        return out
    }

    /** Rate-based (per actual elapsed time) so the verdict is independent of sampling rate. */
    private fun isHardMoment(a: VehicleSnapshot, b: VehicleSnapshot): Boolean {
        val dt = (b.timestampMs - a.timestampMs) / 1000.0
        val decel = (a.speedMps - b.speedMps) / dt
        val steerRate = abs(b.steeringDeg - a.steeringDeg) / dt
        return (b.brakePressed && decel >= 2.9) || steerRate > 90
    }

    private fun pointAt(alongM: Double): GeoPoint {
        var rem = alongM
        val pl = route.polyline
        for (i in 0 until pl.size - 1) {
            val len = distanceM(pl[i], pl[i + 1])
            if (rem <= len || i == pl.size - 2) {
                val t = if (len == 0.0) 0.0 else (rem / len).coerceIn(0.0, 1.0)
                return GeoPoint(pl[i].lat + (pl[i + 1].lat - pl[i].lat) * t, pl[i].lon + (pl[i + 1].lon - pl[i].lon) * t)
            }
            rem -= len
        }
        return pl.first()
    }

    private companion object {
        const val OFF_ROUTE_M = 80.0
        const val ARRIVE_MIN_M = 300.0
        const val MIN_DT_MS = 100L
    }
}
