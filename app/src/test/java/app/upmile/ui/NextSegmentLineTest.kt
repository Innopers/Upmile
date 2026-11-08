package app.upmile.ui

import app.upmile.core.model.GeoPoint
import app.upmile.core.model.HardSegment
import app.upmile.core.model.Route
import app.upmile.core.model.SegmentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NextSegmentLineTest {
    private val mPerDegLat = Math.PI / 180 * 6_371_000.0
    private fun at(m: Double) = GeoPoint(37.0 + m / mPerDegLat, 127.0) // m metres north along the route

    // a straight 5 km route due north; segment B listed first to show order comes from the distance
    private val route = Route("r", "r", listOf(at(0.0), at(5000.0)), emptyList(), 5000, 600)
    private val segments = listOf(
        HardSegment("r:multilane_left:2", SegmentType.MULTILANE_LEFT, "다차선 좌회전", "B", at(2500.0), 2500, emptyMap()),
        HardSegment("r:highway_merge:1", SegmentType.HIGHWAY_MERGE, "고속도로 합류", "A", at(1000.0), 1000, emptyMap()),
    )

    @Test fun atTheStart_firstSegmentInKm() = assertEquals("1km 앞 · 고속도로 합류", nextSegmentLine(route, segments, at(0.0)))

    @Test fun belowOneKm_metresToTheNearestHundred() = assertEquals("300m 앞 · 고속도로 합류", nextSegmentLine(route, segments, at(680.0)))

    @Test fun pastTheFirst_nextOneWithOneDecimal() = assertEquals("1.2km 앞 · 다차선 좌회전", nextSegmentLine(route, segments, at(1300.0)))

    @Test fun almostThere_saysRightAhead() = assertEquals("바로 앞 · 다차선 좌회전", nextSegmentLine(route, segments, at(2460.0)))

    @Test fun afterTheLast_nothing() = assertNull(nextSegmentLine(route, segments, at(2600.0)))

    @Test fun noPosition_nothing() = assertNull(nextSegmentLine(route, segments, null))
}
