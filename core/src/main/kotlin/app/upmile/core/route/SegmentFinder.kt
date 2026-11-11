package app.upmile.core.route

import app.upmile.core.model.GuidePoint
import app.upmile.core.model.HardSegment
import app.upmile.core.model.Route
import app.upmile.core.model.SegmentType
import app.upmile.core.model.SegmentType.*

object SegmentFinder {
    private val MERGE = setOf(101, 102, 103, 111, 112, 113)

    // declaration order doubles as trim priority (garage handled separately)
    private val PRIORITY = listOf(HIGHWAY_MERGE, MULTILANE_LEFT, ROUNDABOUT, ACCIDENT_SPOT, SHARP_CURVE)

    fun find(route: Route, destinationHasGarage: Boolean, limit: Int = 3): List<HardSegment> {
        val found = route.guides.mapNotNull { gp -> typeOf(gp)?.let { build(route, gp, it) } }
        val garage = if (destinationHasGarage && route.polyline.isNotEmpty()) listOf(garage(route)) else emptyList()
        val keep = (limit - garage.size).coerceAtLeast(0)
        val kept = found.withIndex()
            .sortedWith(compareBy({ PRIORITY.indexOf(it.value.type) }, { it.index }))
            .take(keep).sortedBy { it.index }.map { it.value }
        return (kept + garage).take(limit)
    }

    private fun typeOf(g: GuidePoint): SegmentType? = when {
        g.turnType in MERGE -> HIGHWAY_MERGE
        g.turnType == 12 && g.roadType != null && g.roadType <= 6 -> MULTILANE_LEFT
        g.turnType in 131..142 -> ROUNDABOUT
        g.turnType == 192 -> ACCIDENT_SPOT
        g.turnType == 193 -> SHARP_CURVE
        else -> null
    }

    private fun title(t: SegmentType) = when (t) {
        HIGHWAY_MERGE -> "고속도로 합류"
        MULTILANE_LEFT -> "다차선 좌회전"
        ROUNDABOUT -> "회전교차로"
        GARAGE_RAMP -> "주차장 경사로"
        ACCIDENT_SPOT -> "사고 다발 구간"
        SHARP_CURVE -> "급커브"
    }

    /** Demo-place values (양재IC 경부 5 lanes per OSM; 판교역로 5 lanes forward per OSM; 분당내곡로 6 lanes). In production map data fills these. */
    private fun params(t: SegmentType): Map<String, Double> = when (t) {
        HIGHWAY_MERGE -> mapOf("lanes" to 5.0, "accelLaneM" to 180.0)
        MULTILANE_LEFT -> mapOf("turnLanes" to 2.0, "fromLane" to 2.0, "approachLanes" to 5.0, "crossLanes" to 3.0)
        GARAGE_RAMP -> mapOf("slope" to 0.12, "turnDeg" to 90.0)
        ROUNDABOUT -> mapOf("radiusM" to 14.0, "exitArm" to 2.0)
        SHARP_CURVE -> mapOf("radiusM" to 45.0, "turnDeg" to -90.0)
        ACCIDENT_SPOT -> emptyMap()
    }

    private fun build(route: Route, g: GuidePoint, t: SegmentType) = HardSegment(
        "${route.id}:${t.name.lowercase()}:${g.index}", t, title(t), g.description, g.point,
        alongRouteM(route, g.point).toInt(), params(t),
    )

    private fun garage(route: Route): HardSegment {
        val end = route.polyline.last()
        return HardSegment("${route.id}:garage_ramp:end", GARAGE_RAMP, title(GARAGE_RAMP), route.name, end,
            alongRouteM(route, end).toInt(), params(GARAGE_RAMP))
    }
}
