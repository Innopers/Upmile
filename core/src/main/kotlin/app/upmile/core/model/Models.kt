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
    val id: String,
    val type: SegmentType,
    val title: String,
    val start: GeoPoint,
    val distanceFromStartM: Int,
    val params: Map<String, Double>,
)
