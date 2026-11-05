package app.upmile.core.route

import app.upmile.core.model.GeoPoint
import app.upmile.core.model.GuidePoint
import app.upmile.core.model.Route
import app.upmile.core.model.SegmentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SegmentFinderTest {
    private fun g(turnType: Int, roadType: Int? = null) =
        GuidePoint(0, GeoPoint(37.0, 127.0), turnType, "", roadType)

    private fun route(guides: List<GuidePoint>) =
        Route("r", "목적지", listOf(GeoPoint(37.0, 127.0), GeoPoint(37.01, 127.0)), guides, 1000, 100)

    @Test fun find_mapsTurnTypesToSegments() {
        val segs = SegmentFinder.find(route(guides = listOf(g(101), g(12, roadType = 6), g(131))), destinationHasGarage = false)
        assertEquals(listOf(SegmentType.HIGHWAY_MERGE, SegmentType.MULTILANE_LEFT, SegmentType.ROUNDABOUT), segs.map { it.type })
    }

    @Test fun find_idsAreRouteScoped_andPlaceComesFromGuide() {
        val guides = listOf(
            GuidePoint(1, GeoPoint(37.0, 127.0), 12, "판교역 사거리에서 좌회전", 6),
            GuidePoint(4, GeoPoint(37.005, 127.0), 12, "서현역 사거리에서 좌회전", 6),
        )
        val segs = SegmentFinder.find(route(guides), destinationHasGarage = true)
        assertEquals(listOf("r:multilane_left:1", "r:multilane_left:4", "r:garage_ramp:end"), segs.map { it.id })
        assertEquals(listOf("판교역 사거리에서 좌회전", "서현역 사거리에서 좌회전", "목적지"), segs.map { it.place })
    }

    @Test fun find_leftTurnOnNarrowRoad_isNotMultilane() {
        assertTrue(SegmentFinder.find(route(guides = listOf(g(12, roadType = 7))), false).isEmpty())
    }

    @Test fun find_addsGarageRampAtDestination_andRespectsLimit() {
        val segs = SegmentFinder.find(route(guides = listOf(g(101), g(102), g(192), g(193))), destinationHasGarage = true, limit = 3)
        assertEquals(3, segs.size)
        assertEquals(SegmentType.GARAGE_RAMP, segs.last().type)
    }

    @Test fun find_emptyRoute_returnsEmpty() {
        assertTrue(SegmentFinder.find(route(guides = emptyList()), false).isEmpty())
    }
}
