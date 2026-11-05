package app.upmile.core.model

data class GeoPoint(val lat: Double, val lon: Double)

data class GuidePoint(val index: Int, val point: GeoPoint, val turnType: Int, val description: String, val roadType: Int?)

data class Route(
    val id: String,
    val name: String,
    val polyline: List<GeoPoint>,
    val guides: List<GuidePoint>,
    val totalDistanceM: Int,
    val totalTimeS: Int,
)

enum class SegmentType { HIGHWAY_MERGE, MULTILANE_LEFT, ROUNDABOUT, GARAGE_RAMP, ACCIDENT_SPOT, SHARP_CURVE }

data class HardSegment(
    /** Unique per route: "<routeId>:<type>:<guideIndex>" ("<routeId>:garage_ramp:end" for the destination ramp). */
    val id: String,
    val type: SegmentType,
    /** Generic type name for speech ("고속도로 합류"). */
    val title: String,
    /** Where it is, from the TMAP guide description ("양재IC 경부고속도로 방면 진입"); the destination name for the ramp. */
    val place: String,
    val start: GeoPoint,
    val distanceFromStartM: Int,
    val params: Map<String, Double>,
)
